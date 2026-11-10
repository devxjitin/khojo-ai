# ADR-004: Lambda container vs always-on server for text search

- **Status:** Proposed - decided with the Sprint 2 cold-start test
- **Context:** Every search needs a text embedding from the same checkpoint as the images. Lambda is free when idle but loads a multi-GB model on cold start; an always-on small instance answers fast but costs about 2,500 to 5,800 INR per month (blueprint 14.3). Target: text search p95 under 1.5 s on 4G.
- **Plan:** deploy the winning model's text tower three ways and measure cold and warm latency and cost:
  1. Lambda container image (8 to 10 GB memory),
  2. Lambda with provisioned concurrency,
  3. one small always-on instance (t3.medium to m6i.large) behind the same API.
- **Decision rule:** pilot (50 users) uses Lambda if warm p95 and the occasional cold start are acceptable. Move text encoding to the always-on instance when cold starts hurt search or when monthly search volume makes it cheaper. Image indexing stays on Spot GPU batch workers either way.
- **Result / Decision:** _pending the test_
