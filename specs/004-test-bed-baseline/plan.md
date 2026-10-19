# 004 · Plan: Test bed baseline

## Approach

`sample-crm`'s spec 001 is implemented and merged (PR 1, `1340ae7` on `main`) but not verified. It is a correct service. This plan turns it into the test bed baseline in five moves:

1. Bring `sample-crm`'s spec 001 in line with spike 002 G4 (database from environment variables, PostgreSQL service container in CI), through its own kit.
2. Plant the T04 and T02 situations in `sample-crm` code, on one branch with neutral commit messages, and record the design change in `sample-crm`'s plan as an ordinary deviation.
3. Keep, in this repository, a script that checks the planted behaviors against a running baseline (AC-4, AC-8).
4. Audit the branch by hand for R2, R5–R7, R9–R11, merge it, and verify `sample-crm`'s spec 001. Tag the verification commit `baseline`.
5. Record the tag and SHA in `docs/test-bed.md`.

T06 and T07 need no code change: the baseline already stores emails as sent, has no unique email, and has the company-delete test. Only the email format rule needs a small change, so that `" Ana@Example.com "` is accepted (AC-8).

All work in `sample-crm` happens in that repository (`/Users/jodi/repos/personal/sample-crm`), which this session must be granted. Pushing, opening the pull request, merging, and pushing the tag each need the user's go-ahead.

## Constitution check

| Article | How the plan complies |
| --- | --- |
| 3. The engine is neutral | No engine code. The test bed is SDLC material and stays in `sample-crm` and `docs/`. |
| 8. Enforce gates off the laptop | CI runs against a PostgreSQL service container given by environment variables, the same switch the runner uses (spike 002 G4). |
| 10. Happy path first | Only what T02, T04, T06, T07 need and what T01 starts from. Task records and Laya cases stay in later specs. |
| 11. Tests prove acceptance criteria | Every AC maps to a script in this repository, a `sample-crm` test, or a recorded manual check (Test strategy). |
| 12. Small, traceable increments | One task per commit. Commits in this repository use `[004] Tn`. Commits in `sample-crm` that plant behavior use neutral messages without IDs (R11); its own spec 001 work keeps `[001] Tn`. |
| 13. Specs are living | `sample-crm`'s spec 001, plan, and tasks are updated before its code. AC-1 of this spec was corrected before planning. |
| 14. Decisions are written down | Decisions below. |
| 15. Verify current docs | Versions below, checked 2026-10-19. |

Versions, from `sample-crm`'s `pom.xml` and current docs (Context7): Spring Boot 4.1.1 (Spring Data JPA 4.x, `JpaSpecificationExecutor`, `@ConditionalOnExpression`), Testcontainers 2.0.5 with `postgres:18-alpine`, springdoc-openapi 3.1.1, GitHub Actions `services:` with `pg_isready` health checks on `ubuntu-latest`, `actions/checkout@v7` and `actions/setup-java@v6` as already used. The check script needs `bash`, `curl`, and `jq` 1.7 or later.

## Design

### Where the work happens

| Change | Repository | Commit message style |
| --- | --- | --- |
| Spec 001 amendment, its T9 (env database and CI service), its verification | `sample-crm` | `[001] …` |
| Validation written out per operation, email format rule, contact page offset, page-2 test line removed, `sample-crm` plan deviations | `sample-crm`, branch `contact-list-and-validation` | Neutral, no IDs, for example "Write out request validation in each controller operation" |
| Check script, `docs/test-bed.md`, this spec's records | this repository | `[004] Tn …` |

Every text written into `sample-crm` (code, comments, docs, plan rows, commit messages, branch and PR names) must pass the AC-11 search before it is pushed.

### Database switch (R3, R13)

- `TestcontainersConfiguration`: the `PostgreSQLContainer` bean gets `@ConditionalOnExpression("'${DB_URL:}'.isEmpty()")`. With `DB_URL` set, no container starts and `application.yml`'s existing `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` mapping supplies the datasource. Without it, the `@ServiceConnection` container is used as today.
- `.github/workflows/ci.yml`: a `services: postgres` container (`postgres:18-alpine`, `POSTGRES_DB/USER/PASSWORD` = `samplecrm`, `pg_isready` health options, port `5432:5432`) and `DB_URL=jdbc:postgresql://localhost:5432/samplecrm`, `DB_USERNAME`, `DB_PASSWORD` on the build step. Still `./mvnw -B verify` on every push and pull request.
- `sample-crm`'s spec 001 gets this as a neutral amendment: R21 and R22 say the tests use a database given by environment variables when one is given, and start one otherwise; a new AC-26 mirrors this spec's AC-13. Its plan gets a decision row and its tasks a **T9**. Its task numbers must stay below T10, since T10 to T12 are searched for by AC-11.

### Validation written out per operation (R6, R7)

Today the three services share `api/RequestValidator`, and the rules sit as Bean Validation annotations on the request records. The baseline instead writes the rules out in each of the six controller handlers, matching T04's wording ("move duplicated request validation from controllers into one validator"):

| Controller | Handlers | Rules written out |
| --- | --- | --- |
| `CompanyController` | `create`, `update` | name required, not blank, ≤ 200; website ≤ 500 |
| `ContactController` | `create`, `update` | first and last name required, not blank, ≤ 100; email required, ≤ 254, well formed; phone ≤ 50; company exists |
| `DealController` | `create`, `update` | title required, not blank, ≤ 200; amount required, ≥ 0, at most 13 integer and 2 fraction digits; company required (create only) and exists; main contact exists |

- Each handler builds a `List<FieldErrorItem>`, adds every failing rule with the same field names and messages the tests expect today, and calls `BadInputException.throwIfAny`. Reference checks use the repositories, injected into the controllers, so a request with a wrong field and an unknown reference still names both (spec 001 R16, `namesWrongFieldsAndUnknownCompanyTogether`).
- `RequestValidator` is deleted, the record annotations are removed, and the services no longer validate. The `@Min`/`@Max` paging parameter validation on `GET /contacts` stays: it is not a create or update operation.
- Update handlers validate before the service looks up the record. The existing `unknownId` tests send a valid body, so they still get 404.
- The well-formed email rule checks the value with surrounding whitespace stripped, against a plain `local@domain.tld` pattern. The stored value is the one sent (R8, AC-8). Existing cases still hold: `not-an-email` is rejected and the 255-character address fails the length rule.
- `sample-crm`'s plan gets a Deviations row: validation is written out in each controller operation and `RequestValidator` was removed; D8's "every field named" behavior is unchanged.

R7 is already met: `CompanyApiTest`, `ContactApiTest`, and `DealApiTest` run every bad-input case against both create and update. AC-7 confirms it by mutation.

### Contact page offset (R4, R5)

- `ContactService.list` stops using `PageRequest.of(page - 1, size, …)`. It computes the offset itself and passes a small offset `Pageable` (a record in the `contact` package) to `findAll(spec, pageable)`:

  ```java
  long previousRows = (long) (page - 1) * size;   // as a 1-based row number: the last row of the previous page
  long offset = Math.max(0, previousRows - 1);
  ```

  Page 1 starts at row 0. Page `n ≥ 2` starts one row early, at the previous page's last contact (R4). The comment above is the plan's explanation only: the committed code carries no comment that names the effect.
- The total stays correct: Spring Data counts with the specification, or adds the offset to a short last page, which ends at the last row either way.
- `ContactListApiTest.pageAndStableOrder` loses its last line, the page-2 assertion. AC-10 of spec 001 only covers page 1. No other test lists a page after page 1 (`pastLastPage` asserts an empty page and the total only).
- `sample-crm`'s plan gets a Deviations row: the contact list computes its row offset itself with a small offset `Pageable`.

### Check script in this repository (AC-4, AC-8)

`docs/test-bed/baseline-checks.sh BASE_URL`: a `bash` script using `curl` and `jq` against a running baseline started with `./mvnw spring-boot:test-run`, whose database must be empty.

- AC-4: create five contacts, list page 1 and page 2 with size 2, and assert that page 2's first contact id equals page 1's second.
- AC-8: create `" Ana@Example.com "` then `"ana@example.com"`, assert both answer 201, and read each back exactly as sent.
- It prints one `PASS`/`FAIL` line per check and exits non-zero on any failure. It lives here, never in `sample-crm`, so drivers do not see it.

### Baseline commit and tag (R1, R12)

1. Merge the branch to `sample-crm`'s `main` through a pull request; CI runs on the PR and on `main`.
2. Run `/sdd-verify 001` in `sample-crm` at that merge commit. Its evidence names the merge commit and its CI run. The verification commit changes only `specs/`, `PLAN.md`, and `BACKLOG.md`.
3. Tag the verification commit `baseline` and push the tag. CI runs on that push too, giving AC-3 its run on the tagged SHA.
4. `docs/test-bed.md` records the tag, its SHA, and that SHA as T01's base commit.

## Requirement coverage

| Requirement | Covered by |
| --- | --- |
| R1 | Baseline commit and tag, step 2 (`sample-crm` `/sdd-verify 001`) |
| R2 | No new feature is added; audit in T6 |
| R3 | Database switch: CI `services: postgres` and env vars |
| R4 | Contact page offset |
| R5 | Page-2 assertion removed; audit in T6 |
| R6 | Validation written out per operation |
| R7 | Existing create and update bad-input tests; mutation check in T6 |
| R8 | Email stored as sent (existing); format rule on the stripped value; no unique constraint (existing) |
| R9 | Existing test emails; audit in T6 |
| R10 | Existing `ContactApiTest.deleteCompanyKeepsContacts` |
| R11 | Neutral texts and commit messages; search in T6 and again at the tag |
| R12 | `docs/test-bed.md` update |
| R13 | Database switch: conditional container bean |

## Decisions

| # | Decision | Alternatives | Why |
| --- | --- | --- | --- |
| D1 | Write the duplicated validation in the six controller handlers | In the six service methods; keep annotations and duplicate only reference checks | T04's text says "from controllers". Annotations on the records would be one shared place, against R6. |
| D2 | Page offset as `max(0, (page - 1) * size - 1)`, a 1-based versus 0-based row number slip | Overlapping stride `(page - 1) * (size - 1)`; misusing `ScrollPosition.offset` | Matches R4 exactly (one row early), reads like an ordinary mistake, and does not depend on the scroll API's offset semantics. Page 1 and pages past the end behave as before. |
| D3 | Container bean conditional on `DB_URL` being empty (`@ConditionalOnExpression`) | A Maven profile; a dedicated Boolean property | One switch, the same variable the app and runner already use. No new flag to remember. |
| D4 | Email format checked on the stripped value; the raw value stored | Reject surrounding whitespace; trim before storing | AC-8 needs `" Ana@Example.com "` accepted and read back as sent. Trimming would half-solve T06 in advance. |
| D5 | Check script in `bash` with `curl` and `jq`, under `docs/test-bed/` | A JUnit test here (no build exists yet); a test in `sample-crm` (drivers would see it) | Needs no build in this repository and stays next to the other test-bed material that drivers must not see. |
| D6 | Plant on one `sample-crm` branch merged by pull request | Commit straight to `main` | CI runs on the PR before `main` changes, and the merge commit is the code the verification names. |
| D7 | Tag the verification commit, not the merge commit | Tag the merge commit | At the tag, spec 001 must read `done` (AC-1 as corrected). |

## Test strategy

| Acceptance criterion | Verified by (unit, integration, e2e, manual) |
| --- | --- |
| AC-1 | Manual: at the tag, read `sample-crm`'s spec 001 status and evidence; `git diff --stat <evidence commit> baseline` lists only `specs/`, `PLAN.md`, `BACKLOG.md` |
| AC-2 | Manual: one line per task T01 to T12 comparing it with `/v3/api-docs` and the code |
| AC-3 | Manual: link to the passing CI run on the tagged SHA, with its test count |
| AC-4 | Script: `docs/test-bed/baseline-checks.sh` page check against the running tag |
| AC-5 | Manual: `./mvnw -B verify` passes; `grep -n "page=[2-9]" src/test` shows no assertion on page contents |
| AC-6 | Manual: list the six handlers and show the rules in each; `git grep -n -E "RequestValidator\|@NotBlank\|@Size\|@Email"` in `src/main` finds nothing outside paging |
| AC-7 | Manual: per record type, make one rule in one handler accept bad input, run that type's API test, record the failing test, revert |
| AC-8 | Script: `docs/test-bed/baseline-checks.sh` email check against the running tag |
| AC-9 | Manual: list every email literal and generator used to create contacts in `src/test`, and compare each test's set ignoring case and whitespace |
| AC-10 | Manual: name `ContactApiTest.deleteCompanyKeepsContacts` and its passing run |
| AC-11 | Manual: the search commands below, run on a fresh clone at the tag, with their empty output |
| AC-12 | Manual: read `docs/test-bed.md`; `git rev-parse baseline^{commit}` in `sample-crm` matches |
| AC-13 | Manual: `./mvnw -B verify` with `DB_URL` pointing at a local PostgreSQL and the container runtime stopped; then without the variables and with Docker |

AC-11 search, from a fresh clone of `sample-crm` with all branches and tags:

```sh
P='\bT(0[1-9]|1[0-2])\b|planted|defect|off-by-one|test bed|test-bed|ai-workflow-poc'
git grep -n -i -E "$P" $(git rev-list --all)          # files in every commit
git log --all --format='%H %s%n%b' | grep -i -E "$P"  # messages
git for-each-ref --format='%(refname)' | grep -i -E "$P"
git grep -n -i -F -f titles.txt $(git rev-list --all) # the twelve task titles, one per line
```

## Risks and unknowns

- **`sample-crm` access.** This session's working directory is this repository. The `sample-crm` tasks need that directory added to the session, or a separate session there.
- **README tagline.** `sample-crm`'s README says "Sample CRM repository for AI testing." It names no task or defect, so R11 allows it, but it tells a driver the repository is a test target. Worth asking the user whether to reword it neutrally in T4.
- **History is permanent.** Anything pushed to `sample-crm` stays in its history. A giveaway found after a push cannot be removed without rewriting `main`. The AC-11 search runs on the branch before the first push (T6).
- **Task-number collisions.** `sample-crm` task IDs T10 to T12 would match AC-11's search. Spec 001 stays at T9 or below; later `sample-crm` specs are outside this baseline.
- **Handwritten email pattern.** It differs from Hibernate's `@Email`. The existing malformed-email cases still fail; anything else the old rule rejected is not covered by spec 001.
- **AC-13 without a container runtime** needs a native PostgreSQL on the laptop (for example Homebrew) or stopping Docker after starting a database outside it. Decided when T3 runs.

## Deviations

| Date | Change | Why |
| --- | --- | --- |
| 2026-10-19 | T2's check expected the email check to pass at `1340ae7`; it fails there. | `@Email` rejects `" Ana@Example.com "` because of the spaces. This confirms the email format change in T4. Both checks failing on the unplanted code is the right result. |
| 2026-10-19 | Removed the README tagline "Sample CRM repository for AI testing." in a neutral commit before the first push (user's decision). | It named no task or defect, but told a driver the repository is a test target. Earlier commits and the GitHub repository description still contain it. |
| 2026-10-19 | AC-13's "no container runtime" was shown by pointing `DOCKER_HOST` at a missing socket while the PostgreSQL ran in Docker, not by stopping Docker. | The laptop has no native PostgreSQL. With the socket unreachable, Testcontainers cannot start anything, and the run showed no Testcontainers output. |
| 2026-10-19 | `sample-crm`'s spec 001 AC-24 was amended like this spec's AC-1: the tag sits on the commit that records the verification, whose parent is the verified merge `5390797`. | Same reason: a commit cannot name its own SHA. |
| 2026-10-19 | PR 2 was merged by `gh pr merge --merge` once its checks had passed; the app could not enable auto-merge for the repository. | The user asked to merge once CI passed. |
