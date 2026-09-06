# Spring Boot 4 vs 3.x — 이 학습 프로젝트에서 실제로 연습할 차이

Spring Boot 4.0(2025-11-20)은 Framework 7 위에서 모듈화된 starter, JSpecify null-safety, Jackson 3 기본값, Java 25 지원(Java 17 유지), HTTP Service Client 자동설정, API 버전 관리를 제공한다. 4.1.0(2026-06-10)은 그 위에 gRPC, HTTP 클라이언트 SSRF 필터(`InetAddressFilter`), OpenTelemetry 관측 강화가 추가된다. 이 저장소는 이미 Boot 4.1.0의 모듈 스타터(`webmvc`, `restclient`)와 Jackson 3, 공통 HTTP timeout을 쓰고 있다. 아래는 Boot 3.x에서 이미 있던 기능과 Boot 4/Framework 7에서 새로 1급이 된 기능을 구분하고, GitHub 연동 학습 범위에서 다음에 연습할 항목만 고른다.

범례: **신규** = Boot 4 / Framework 7에서 처음. **1급 자동설정** = 프레임워크 API는 이전에 있었고 Boot 4에서 starter·프로퍼티가 붙음. **이전부터 존재** = Boot 3.x / Framework 6.x에도 있음.

---

## Boot 4 vs 3: 실제 차이

### 모듈 스타터 (신규, Boot 4)

Boot 3.5까지는 거대한 단일 `spring-boot-autoconfigure` jar(약 2 MiB)가 모든 기술을 담았다. Boot 4는 기술별로 모듈을 쪼갠다. 기존 starter는 관련 모듈을 끌어오지만, 이전에 starter가 없던 기술(Flyway 등)은 이제 전용 starter가 필요하다. 테스트도 `spring-boot-starter-<technology>-test`가 짝으로 있다.

이 프로젝트와 직접 관련된 이름 변경:

| Boot 3.x | Boot 4 |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` (구 이름은 deprecated) |
| (web starter에 RestClient가 섞임) | `spring-boot-starter-restclient` (imperative HTTP 클라이언트 전용) |
| `spring-boot-starter-test` 한 덩어리 | 기술별 `*-test` starter (`webmvc-test`, `restclient-test`, `actuator-test` …) |

이행용 `spring-boot-starter-classic` / `spring-boot-starter-test-classic`은 모든 모듈을 넣되 전이 의존성은 제외한다. 장기적으로는 쓰지 말라고 문서화되어 있다.

부수 효과: WebClient만 쓰는 앱에 웹 서버 auto-config가 올라오지 않는다. Actuator 없이 Micrometer만 쓰는 것도 가능하다.

### JSpecify (신규, Framework 7 / Boot 4)

포트폴리오 API가 JSR 305 기반 `org.springframework.lang` 대신 JSpecify(`@NullMarked`, `@Nullable` type-use)로 옮겼다. Spring 어노테이션은 deprecated. IDE(IntelliJ 2025.3+) 경고로 NPE를 줄이거나, 앱 패키지에 `@NullMarked`를 붙이고 NullAway로 빌드 시점에 강제할 수 있다. Kotlin 2는 JSpecify를 네이티브 nullability로 번역한다.

### Jackson 3 (신규 기본값, Framework 7 / Boot 4)

Boot 4는 Jackson 3를 기본 JSON 라이브러리로 쓴다. 패키지/그룹이 `com.fasterxml.jackson` → `tools.jackson`(annotations는 예외로 `com.fasterxml` 유지). 가변 `ObjectMapper` 대신 불변 `JsonMapper`. `Jackson2ObjectMapperBuilder` 대체물은 없고 Jackson `JsonMapper.builder()`를 쓴다. Jackson 2 auto-config는 deprecated 호환 모듈(`spring-boot-jackson2`, `spring.jackson2.*`, `spring.jackson.use-jackson2-defaults`)로만 남는다.

### Java / Jakarta 기준선 (Framework 7 / Boot 4)

- Java 17 유지, **Java 25를 최신 LTS로 권장**. 이 프로젝트는 Java 21.
- Jakarta EE 11: Servlet 6.1(Tomcat 11 / Jetty 12.1), JPA 3.2, Bean Validation 3.1.
- Kotlin 2.2, JUnit 6, Gradle 9 지원(8.14+ 유지).
- Undertow는 Servlet 6.1 미지원으로 Boot 4에서 제거.

### Framework 7에서 실제로 새로운 것

| 항목 | 구분 |
| --- | --- |
| HTTP Service Groups (`@ImportHttpServices`, `HttpServiceProxyRegistry`) | **신규**. HTTP Interface 자체는 Framework 6.0 |
| API 버전 관리 (`version` on `@RequestMapping` / `@HttpExchange`, `spring.mvc.apiversion.*`) | **신규** |
| `@Retryable`, `@ConcurrencyLimit`, `RetryTemplate` in `spring-core` | **신규** (Spring Retry 프로젝트의 축소·이관) |
| `RestTestClient` | **신규** (`WebTestClient`의 비반응형 대응) |
| `JmsClient` | **신규** |
| Programmatic `BeanRegistrar` | **신규** |
| `RestTemplate` 문서상 deprecated (7.1에서 `@Deprecated` 예정) | 정책 변경. API는 Framework 6.1부터 `RestClient`가 권장 |
| Jackson 3, JSpecify | **신규 기본값** |

### 마케팅과 헷갈리기 쉬운 것 (Boot 3.x / Framework 6.x에 이미 있음)

- **RestClient**: Framework 6.1, Boot 3.2부터. Boot 4의 변화는 전용 starter `spring-boot-starter-restclient`와 공통 프로퍼티 `spring.http.clients.*`.
- **HTTP Interface (`@HttpExchange`, `HttpServiceProxyFactory`)**: Framework 6.0. Boot 4의 변화는 그룹 등록·프로퍼티 자동설정.
- **ProblemDetail (RFC 9457)**: Framework 6.0. Boot 3에서도 `spring.mvc.problemdetails.enabled`로 기본 오류 페이지에 켤 수 있었다. 이 프로젝트는 `@RestControllerAdvice`에서 직접 만든다.
- **Caffeine / Spring Cache**: Boot 3에도 있음. Boot 4는 `spring-boot-starter-cache`가 독립 starter가 된 정도.
- **Actuator + Prometheus**: Boot 3에도 있음.
- **공통 HTTP timeout 프로퍼티**: Boot 3.4/3.5는 `spring.http.client.connect-timeout`(단수). Boot 4는 `spring.http.clients.*`(복수)로 이름만 바뀌고, 모든 HTTP 클라이언트에 공통 적용되는 쪽이 정리됨.

---

## 이 프로젝트가 이미 연습하는 것

| 연습 중인 것 | 구분 | 코드 |
| --- | --- | --- |
| 모듈 스타터 `webmvc` / `restclient` + 짝 test starter | Boot 4 **신규 패키징** | [`build.gradle`](../build.gradle) |
| HTTP Interface + RestClient로 GitHub 호출 | Interface는 **이전부터**. 조립은 Boot 4 `@ImportHttpServices` | [`GitHubClient.java`](../src/main/java/com/example/developeractivity/developer/GitHubClient.java), [`GitHubClientConfig.java`](../src/main/java/com/example/developeractivity/developer/GitHubClientConfig.java) |
| 공통 connect/read timeout | 프로퍼티 이름은 Boot 4. 개념은 3.4+ | [`application.properties`](../src/main/resources/application.properties) `spring.http.clients.*`, [`GitHubTimeoutIntegrationTests.java`](../src/test/java/com/example/developeractivity/developer/GitHubTimeoutIntegrationTests.java) |
| Jackson 3 snake_case DTO | Boot 4 **기본값** | `tools.jackson` — [`GitHubUserResponse.java`](../src/main/java/com/example/developeractivity/developer/GitHubUserResponse.java) 등 |
| ProblemDetail 오류 본문 | **이전부터** (Framework 6) | [`DeveloperExceptionHandler.java`](../src/main/java/com/example/developeractivity/developer/DeveloperExceptionHandler.java) |
| Caffeine 메모리 캐시 + stale fallback | 라이브러리 직접 사용. Spring Cache starter 아님 | [`DeveloperCache.java`](../src/main/java/com/example/developeractivity/developer/DeveloperCache.java) |
| Actuator health/metrics + Prometheus | **이전부터** | `spring-boot-starter-actuator`, `micrometer-registry-prometheus` |
| OpenTelemetry span (cache/outcome/attempt) | Boot 4 **신규 starter** | `spring-boot-starter-opentelemetry`, [`DeveloperService.java`](../src/main/java/com/example/developeractivity/developer/DeveloperService.java) |
| Java 21 | Boot 4 기준선(17–25) 안 | `java { toolchain { languageVersion = 21 } }` |
| Boot 4 테스트 패키지 | 모듈화로 패키지 이동 | `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest` |
| RestTestClient로 우리 서버 테스트 | Framework 7 **신규** | [`DeveloperControllerTests.java`](../src/test/java/com/example/developeractivity/developer/DeveloperControllerTests.java), [`PrometheusEndpointTests.java`](../src/test/java/com/example/developeractivity/developer/PrometheusEndpointTests.java) |

이미 쓰는 Boot 4 경로: `@ImportHttpServices`, `spring.http.serviceclient.github.base-url`, Framework 7 `RetryTemplate`(D-009), OpenTelemetry starter(D-010), `RestTestClient`. 아직 안 쓰는 것: API versioning, JSpecify/`@NullMarked`, `InetAddressFilter`.

---

## 다음에 연습할 Boot 4 기능 (현재 범위 = 외부 API 회복력)

순위는 GitHub 연동·timeout·rate limit·캐시라는 현재 학습 축과의 맞음. retry는 D-009로 적용했다.

### 1. HTTP Service Client 자동설정 — 가장 맞음

**신규(그룹/자동설정) + Interface는 이전부터.** `GitHubClient`는 `@ImportHttpServices(group = "github")`로 등록하고, 호스트는 `spring.http.serviceclient.github.base-url`이다. GitHub 헤더와 선택적 token만 `RestClientHttpServiceGroupConfigurer`가 붙인다.

```properties
spring.http.serviceclient.github.base-url=https://api.github.com
spring.http.serviceclient.github.connect-timeout=2s
spring.http.serviceclient.github.read-timeout=5s
```

공통값 `spring.http.clients.*`는 유지하고, GitHub만 다른 timeout이 필요해지면 D-006이 예고한 “그룹별 설정”이 이 경로다. 헤더(`Accept`, `User-Agent`, Bearer token)는 `RestClientHttpServiceGroupConfigurer` 빈으로 옮긴다.

공식: [Boot 4.1 Calling REST Services — HTTP Service Interface Clients](https://docs.spring.io/spring-boot/4.1/reference/io/rest-client.html#io.rest-client.httpservice), [Framework 7 HTTP Service Groups](https://docs.spring.io/spring-framework/reference/7.0/integration/rest-clients.html#rest-http-service-client-group-config).

블로그 초안의 `spring.http.client.service.*`는 마일스톤 이름이다. 4.1 문서는 `spring.http.serviceclient.<group>`가 정본.

### 2. Framework 7 retry — 적용됨 (D-009)

**신규.** `RetryTemplate` + `RetryPolicy`(maxRetries=1, delay 50ms). timeout과 GitHub 5xx만 재시도하고 4xx·429·404·연결 실패는 제외한다. 오래된 캐시가 있으면 재시도하지 않는다. 시도마다 `developer.github.calls`를 남긴다. `@Retryable`/`@EnableResilientMethods`는 같은 정책을 애노테이션으로 옮길 때 재검토한다.

공식: [Framework 7 Resilience](https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html).

### 3. OpenTelemetry starter — 적용됨 (D-010)

**신규 starter (Boot 4.0).** `spring-boot-starter-opentelemetry`로 GitHub 조회 한 번에 span을 남긴다. 조회 span은 `cache=hit|miss|stale`과 `github.outcome`을 붙이고, 바깥 호출은 시도마다 자식 span이다. 신선한 저장 적중은 바깥 호출 span을 만들지 않는다. 숫자는 Prometheus에 두고, 흐름은 OTLP로 Jaeger에 보낸다. 메트릭을 트레이스로 대체하지 않는다.

공식: [Boot 4.1 Observability — OpenTelemetry](https://docs.spring.io/spring-boot/4.1/reference/actuator/observability.html#actuator.observability.opentelemetry).

### 4. `RestTestClient` — 적용됨

**신규 (Framework 7, Boot 4 자동설정).** 우리 서버를 치는 테스트는 `RestTestClient`다. 컨트롤러 슬라이스와 timeout 통합은 MockMvc에 묶고, Prometheus 스크랩은 랜덤 포트 서버에 묶는다. GitHub로 나가는 흉내(`MockRestServiceServer`)는 그대로 둔다. 회복력 로직과 공개 API는 바꾸지 않는다.

### 5. JSpecify + NullAway — 품질, 회복력은 아님

**신규.** `package-info.java`에 `@NullMarked`, GitHub DTO·캐시 반환의 null을 `@Nullable`로 명시. Java 21은 NullAway JSpecify 모드에 `-XDaddTypeAnnotationsToSymbol=true`(Oracle JDK 제외, 21.0.8+)가 필요할 수 있다. 공식은 Java 25 toolchain + `--release 17/21`을 권한다.

공식: [Framework 7 Null-safety](https://docs.spring.io/spring-framework/reference/7.0/core/null-safety.html).

### 6. API 버전 관리 — 공개 API를 깨지 않고 바꿀 때

**신규.** `spring.mvc.apiversion.use.header=X-Version` + `@GetMapping(..., version = "1.1")`. 클라이언트는 `RestClient`/`@GetExchange`의 `version`. 이 서비스는 내부 조회 API 하나라 지금은 과하다. `/developers` 응답 필드를 바꾸거나 GitHub DTO와 공개 모델을 더 벌릴 때 연습한다.

공식: [Boot servlet API versioning](https://docs.spring.io/spring-boot/4.1/reference/web/servlet.html#web.servlet.spring-mvc.api-versioning).

### 7. `InetAddressFilter` (Boot 4.1) — SSRF, 현재 위협 모델에는 약함

**신규 (4.1).** 나가는 HTTP의 목적지 IP를 제한한다. base URL이 고정 GitHub이면 실익이 작다. 사용자 입력을 URL에 붙이는 날이 오면 그때.

---

## Boot 4이지만 여기엔 안 맞는 것

| 기능 | 이유 |
| --- | --- |
| **gRPC (Boot 4.1)** | GitHub는 REST. `.proto`·별도 포트(기본 9090)가 현재 범위 밖. |
| `JmsClient` / Kafka / Rabbit / Pulsar / Redis `@RedisListener` | 메시징·브로커 없음. |
| Spring Batch Mongo, DataSource lazy fetch, embedded LDAP SSL | DB/LDAP 없음. |
| OAuth2 JWT `authorities-claim-expressions` | 인증 없음. GitHub token은 선택적 Bearer. |
| Kotlin Serialization JSON starter | Java 21 프로젝트. |
| `spring-boot-starter-cache`로 교체 | D-007은 Caffeine을 직접 써서 fresh/stale TTL을 구현. Spring Cache 추상화는 그 의미를 지운다. |
| Java 25로 toolchain만 올리기 | 가능하지만 회복력 학습과 무관. |
| Programmatic `BeanRegistrar` | 빈 등록이 단순하다. |
| Classic starter | 이 프로젝트는 이미 모듈 스타터를 쓴다. |

---

## 출처

Primary (요청된 URL):

- [Spring Boot 4.0.0 available now](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)
- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
- [Spring Boot 4.1.0 available now](https://spring.io/blog/2026/06/10/spring-boot-4)
- [Modularizing Spring Boot](https://spring.io/blog/2025/10/28/modularizing-spring-boot)
- [Null-safe applications with Spring Boot 4](https://spring.io/blog/2025/11/12/null-safe-applications-with-spring-boot-4)
- [Spring Framework 7.0 General Availability](https://spring.io/blog/2025/11/13/spring-framework-7-0-general-availability)

공식 문서:

- [HTTP Service Clients (Boot 4.1)](https://docs.spring.io/spring-boot/4.1/reference/io/rest-client.html#io.rest-client.httpservice)
- [API Versioning (Boot MVC)](https://docs.spring.io/spring-boot/4.1/reference/web/servlet.html#web.servlet.spring-mvc.api-versioning)
- [RestClient / 공통 HTTP 클라이언트 (Boot 4.1)](https://docs.spring.io/spring-boot/4.1/reference/io/rest-client.html)
- [REST Clients (Framework 7)](https://docs.spring.io/spring-framework/reference/7.0/integration/rest-clients.html)
- [JSpecify null-safety (Framework 7)](https://docs.spring.io/spring-framework/reference/7.0/core/null-safety.html)
- [Resilience (Framework 7)](https://docs.spring.io/spring-framework/reference/7.0/core/resilience.html)
- [OpenTelemetry (Boot 4.1)](https://docs.spring.io/spring-boot/4.1/reference/actuator/observability.html#actuator.observability.opentelemetry)
- [gRPC (Boot 4.1)](https://docs.spring.io/spring-boot/4.1/reference/io/grpc.html)
- [Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-7.0-Release-Notes)
- [Boot 3.5 Calling REST Services](https://docs.spring.io/spring-boot/3.5/reference/io/rest-client.html) — RestClient·`spring.http.client.*`가 3.x에도 있었음을 대조

관련 공식 블로그 (위 릴리즈가 가리키는 설명):

- [HTTP Service Client Enhancements](https://spring.io/blog/2025/09/23/http-service-client-enhancements)
- [API Versioning in Spring](https://spring.io/blog/2025/09/16/api-versioning-in-spring)
- [Core Spring Resilience Features](https://spring.io/blog/2025/09/09/core-spring-resilience-features)
- [Introducing Jackson 3 support in Spring](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring)
