# Assumptions and decisions
The sections below describe different assumptions and decisions made during the https://github.com/paidy/interview/blob/master/Forex.md problem solving.
 
## General
 - Some sbt dependencies are quite old. Bumping them is out of scope.
 - A production ready microservice would have logging, metrics, tracing. Those will help in troubleshooting and give more visibility about the service. Introducing them is out of scope of current task.
 - As the purpose of this proxy is to hide downstream(s) complexities and possibly unify the api, the `Forex` `/rate` endpoint should not be changed to support querying multiple currencies at once (aka made it the same as for `One-Frame`).

## Caching
As the downstream service `One-Frame` supports a max of 1k rpd, and the `Forex` service should at least support 10x more rpd the downstream calls should be cached, so the one-frame responses can be reused.
Cache `ttl=5mins` should be used in order to meet another requirement: `One-Frame should return responses that are not older than 5 minutes`.

### Caching approaches
The `Forex` service currently supports 9 currencies (btw one-frame supports more) so 72 different `Forex` requests could be made.

The requirements don't specify the specifics of `Forex` requests like: are there any hot currency pair requests, requests distribution.

**Lets assume the worst :), `Forex` will be queried for every pair once in 5 mins.**. 

Lets consider Several Solutions (given the `Forex` requests nature assumption above):
 1. Map `Forex` request (1 pair) to the `One-Frame` request (1 pair) and cache the one-frame result for 5 mins.
    - The Forex will support only 1k rps. As basically the cache won't help here at all.
 2. Map `Forex` request (1 pair) to the `One-Frame` request (2 pairs, ie: `USDEUR`, `EURUSD`) and cache the `One-Frame` result for 5 mins. 
    - This reduces the `One-Frame` requests number, but still we won't meet our '10k' requirement.
 3. Use scalacache with Caffeine, make `One-Frame` api call for all the currency pairs and cache the results under one dummy key.
    - the downside of this is that on parallel `Forex` requests at the same time when cache should be reloaded, multiple `One-Frame` requests will be issued as the Caffeine cache is sync.
 4. Use a cache with a periodic loader which queries `One-Frame` every 4 mins for all pairs at once.
    - This would consume `360 (24*60/4)` `One-Frame` requests only. As the loader will happen in background, 
    1 min will be reserved for the `One-Frame` request completion, so the response data is not stale ( < 5 mins).
 
 The last option meets our requirement, so we will implement it. 
 
 Another question we should answer is what type of cache should it be:
  1. local, in-memory
    - each `Forex` node should have its own copy of cache, each new node will double the `One-Frame` requests number.
  2. distributed (Redis, Memcached, others)
 
 We would go with the simpler, 1st option. 
 We might want to have just 2 Forex nodes (the second node for the availability purpose).
 2 `Forex` nodes should produce less than 1k `One-Frame` requests and should also handle much more than 10k requests per day.


## Things to improve
 - add more unit tests to cover more scenarios
 - handle `One-Frame` errors more accurately
   - `One-Frame` returns 200(OK) for quota reached errors, and for invalid currency pairs too, currently those will be mapped to `Forex` GenericError (500).
 - add logging, metrics, tracing
 - create generic error handlers, so they could be reused
 - handle stale cache data when the `One-Frame` is down for a long time.
 - custom value classes for bid and ask (one-frame response), or just ignore them and don't parse
