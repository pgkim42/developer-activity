# Prometheus scrape surface

## Intent
Expose `/actuator/prometheus` so cache/GitHub meters can be scraped and accumulate. Docker compose Prometheus if Docker works.

## Seams
- GET /actuator/prometheus
- GET /actuator/metrics/developer.cache.hits
- Prometheus query API (optional)

## Steps
1. RED actuator prometheus test
2. Add registry + expose endpoint
3. GREEN + JSON metrics regression
4. compose + two scrapes if Docker
5. curl QA, cleanup, docs, commit
