# Building a Production-Grade URL Shortener in Spring Boot 3 (and the Two Bugs I Only Caught by Running It)

Short links are a deceptively simple problem — `POST` a long URL, get back a short code, follow the code, get redirected. But "make it work" and "make it hold up in production" are very different bars. I built a URL shortener as a compact showcase of how I approach backend services, and the interesting part wasn't the happy path — it was the two bugs that only surfaced when I actually ran the thing end to end.

> **Code:** https://github.com/AfranUsmani/Snip

## The design in one breath

- **Java 21 · Spring Boot 3.3**, layered as controller → service → repository.
- **Short codes are random, unguessable Base62 tokens** (7 chars from a `SecureRandom` source). They're deliberately unrelated to the database id, so the link set can't be enumerated by counting — see the security note below. (The first cut derived codes from the id; that was the mistake this writeup owns up to.)
- **Cache-aside reads.** The redirect path (`GET /{code}`) is the hot path, so resolutions are cached (Redis in production, in-memory locally) and only miss through to the database.
- **Atomic hit counting** via a single `UPDATE ... SET hit_count = hit_count + 1`, so concurrent redirects don't race.
- **Observable by default** — Spring Boot Actuator health checks and a `/actuator/prometheus` scrape endpoint via Micrometer.
- **OpenAPI/Swagger UI**, a consistent JSON error contract, Docker + Docker Compose, and GitHub Actions CI.

It runs with **zero infrastructure locally** (in-memory H2 + in-memory cache) and has a production-like Docker Compose path (PostgreSQL + Redis).

## Bug #1: the short code that was never there

My first cut of `create()` did the obvious thing:

1. Save the row to get its generated id.
2. Encode the id into a short code.
3. Save again.

With `GenerationType.IDENTITY`, that blew up: `NULL not allowed for column "SHORT_CODE"`. IDENTITY ids force an immediate `INSERT` to obtain the id — and that first insert goes in with a null short code, violating the `NOT NULL` constraint before step 2 ever runs.

Switching to a `SEQUENCE` id got me the id *before* the insert, but Hibernate snapshots the entity's state at `persist()` time — so the short code I set afterwards still wasn't in the queued insert.

The clean fix: derive the short code inside a **`@PrePersist` lifecycle callback**. By the time it fires, the sequence id is assigned, so a single `INSERT` carries a non-null short code. One write, no race, no null.

## Bug #2: the Prometheus endpoint that "worked" but 404'd in tests

My integration test hit `/actuator/prometheus` and got a `404`. But when I ran the packaged app and curled the same endpoint, it returned `200` with real metrics. Same code, opposite result.

The cause: **Spring Boot disables metrics export inside `@SpringBootTest` by default** so tests don't spin up exporters. The scrape endpoint simply isn't registered in the test context. The fix was `@AutoConfigureObservability`, plus moving the integration test to a real embedded server (`RANDOM_PORT` + `TestRestTemplate`) instead of `MockMvc` — a more faithful test anyway, since it exercises the actual HTTP stack.

## Bug #3 (the one that shipped): sequential codes are enumerable

The `@PrePersist` fix above was elegant — and quietly insecure. Encoding a monotonic id means the codes *are* the sequence: `/1`, `/2`, `/3`. Anyone with one link can walk the entire system by incrementing the number, and the codes leak exactly how many links exist and in what order they were made. "Unique by construction" solved the wrong problem.

The fix was to stop encoding identity and start generating it: a `ShortCodeGenerator` draws a 7-character code uniformly from a Base62 alphabet using `SecureRandom` (keyspace ≈ 3.5×10¹²). The service assigns it before persist and retries against the unique index on the astronomically rare collision. The `@PrePersist` id-derivation is gone.

A nice side effect: since the id is now purely internal, I could raise the sequence `allocationSize` and turn on Hibernate JDBC batching — the id no longer has to be dense or user-meaningful, so inserts (especially the bulk endpoint) get cheaper.

## Why this matters

The first two bugs were invisible to unit tests with mocks. They only appeared when the code met a real database and a real server. That's the whole point of the test pyramid having an integration layer — and the reason I don't consider generated or hand-written code "done" until I've watched it run. The third wasn't a crash at all: the code worked perfectly and was wrong anyway, which is the kind of bug tests won't catch unless you think adversarially about what the output *reveals*. The final suite is 39 tests: fast unit tests for the code generator and service logic (including collision-retry), and end-to-end integration tests against a live server covering create → redirect → stats, validation, error handling, the dashboard served at the root path, and the Prometheus endpoint.

## Takeaways

- **Don't leak identity in public tokens.** Deriving short codes from a monotonic id was tidy and enumerable; a random `SecureRandom` code trades a little collision-handling for links that can't be walked. For a *public* identifier, unguessable beats convenient.
- **Know your ORM's lifecycle.** *When* a value is set relative to `persist()`/flush is as important as *what* it is.
- **Test the environment, not just the logic.** Mocks verify your intent; a real server verifies reality.
- **Run it.** The bugs that embarrass you in production are the ones that never showed up in a green mock-based build.

*Built with Java 21, Spring Boot 3, Redis, Prometheus, and Docker. Feedback and PRs welcome.*
