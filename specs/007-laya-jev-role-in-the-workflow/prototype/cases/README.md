# Laya's first cases

Ten hand-made cases for spike 007: a good and a flawed variant for five test-bed tasks (`docs/test-bed/README.md`, "Laya's first cases"). Claude drafted them in a session separate from any evaluation. No variant has been scored or evaluated.

Each patch is `git diff baseline` in `sample-crm`, against the tag `baseline` (`0916dbf`), tests included. Apply one to a fresh checkout of `baseline` with `git apply`. Each task has one requirements doc, Laya's `requirements_doc` input, shared by both variants.

| Case | Requirements | Patch | The flaw |
| --- | --- | --- | --- |
| T01 good | `T01-requirements.md` | `T01-good.patch` | — |
| T01 flawed | `T01-requirements.md` | `T01-flawed.patch` | Same code as good. The tests only cover the unfiltered count; the `companyId` filter has no test. |
| T03 good | `T03-requirements.md` | `T03-good.patch` | — |
| T03 flawed | `T03-requirements.md` | `T03-flawed.patch` | `DealStage.canMoveTo` also allows `LEAD → LOST`. The test list of invalid moves leaves that pair out, so the tests pass. |
| T04 good | `T04-requirements.md` | `T04-good.patch` | — |
| T04 flawed | `T04-requirements.md` | `T04-flawed.patch` | The email format check drops `.strip()`, so an email with surrounding spaces, accepted before, is now rejected with a `400`. No existing test sends one. |
| T06 good | `T06-requirements.md` | `T06-good.patch` | — |
| T06 flawed | `T06-requirements.md` | `T06-flawed.patch` | The duplicate check is an exact match (`existsByEmail`), so `Ana@Example.com` or ` ana@example.com` gets past `ana@example.com`. Its tests use exact matches only. |
| T11 good | `T11-requirements.md` | `T11-good.patch` | — |
| T11 flawed | `T11-requirements.md` | `T11-flawed.patch` | The filter is pasted into the SQL text (`SEARCH.formatted(...)`), so it is open to SQL injection; a quote in the filter breaks the query. Its tests have no quote or wildcard cases. |

Apart from its flaw (and the tests the flaw would fail), each flawed variant is the same as its good one.

## Build and tests

Each variant was applied to a fresh `baseline` checkout and built with `./mvnw -B verify`, Testcontainers PostgreSQL on Docker, Java 25. Results are in the table below.

| Case | `git apply` | `./mvnw -B verify` | Tests run (baseline: 84) |
| --- | --- | --- | --- |
| T01 good | clean | passed | 88, 0 failures, 0 errors |
| T01 flawed | clean | passed | 86, 0 failures, 0 errors |
| T03 good | clean | passed | 105, 0 failures, 0 errors |
| T03 flawed | clean | passed | 104, 0 failures, 0 errors |
| T04 good | clean | passed | 84, 0 failures, 0 errors |
| T04 flawed | clean | passed | 84, 0 failures, 0 errors |
| T06 good | clean | passed | 92, 0 failures, 0 errors |
| T06 flawed | clean | passed | 86, 0 failures, 0 errors |
| T11 good | clean | passed | 95, 0 failures, 0 errors |
| T11 flawed | clean | passed | 93, 0 failures, 0 errors |

## Size

Laya's model has a 1024-token context. Patch sizes, for packing:

| Patch | Bytes | Lines |
| --- | --- | --- |
| `T01-flawed.patch` | 3039 | 77 |
| `T01-good.patch` | 3876 | 99 |
| `T03-flawed.patch` | 11319 | 297 |
| `T03-good.patch` | 11317 | 297 |
| `T04-flawed.patch` | 17998 | 363 |
| `T04-good.patch` | 18006 | 363 |
| `T06-flawed.patch` | 5450 | 127 |
| `T06-good.patch` | 6434 | 150 |
| `T11-flawed.patch` | 7542 | 181 |
| `T11-good.patch` | 8132 | 198 |

No patch fits in 1024 tokens together with a rendered rubric and the labels: the smallest is about 3 KB, and T04's are 18 KB. A packet needs a trimmed diff or a summary, as `docs/test-bed/README.md` allows.
