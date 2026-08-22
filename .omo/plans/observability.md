# Observability meters for Developer Activity

## Intent
Make cache and GitHub-call behavior measurable on Actuator. No second provider, Redis, DB, frontend, or retry.

## Tier
HEAVY — new observability contract across service + actuator. Self-review only (not a ulw-plan reviewer gate).

## Seams
- `DeveloperService` + real `SimpleMeterRegistry`
- `GET /actuator/metrics/{name}`
- `GET /developers/{username}` (regression)

## Existing
`DeveloperService` already increments untested `developer.cache.requests{outcome=cache.hit|cache.stale}` and untagged `github.client.requests`. Rename to the contract names and prove them.

## Contract names
- `developer.cache.hits` counter
- `developer.cache.stale` counter
- `developer.github.calls` timer tagged `outcome=success|timeout|not_found|rate_limited|unavailable`

## Steps
1. RED/GREEN cache hits (service test)
2. RED/GREEN github.calls success (service test)
3. RED/GREEN cache stale + keep timeout 504
4. RED/GREEN 404 is not a cache hit
5. Expose actuator metrics; curl live proof; cleanup
6. Docs if names are mentioned; commit

## QA
See goal criteria 1–4.
