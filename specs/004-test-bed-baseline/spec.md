---
id: 004
title: "Test bed baseline"
type: feature
status: draft
phase: 0
depends_on: [002]
refs: ["PLAN Phase 0 / Spikes / 2. Test bed", "BACKLOG Feedback loop / Seed data", "docs/test-bed.md / Repository / Baseline"]
updated: 2026-10-18
---

# 004 · Test bed baseline

## Problem

Spike 002 chose `sample-crm` as the repository where runs do real work, along with twelve tasks, T01 to T12 (`docs/test-bed.md`). The repository has only a README. The task records, the hidden acceptance tests, and Laya's first cases all need a baseline to start from, and four tasks only take their expected path if the baseline contains certain defects and tests on purpose: T02, T04, T06, and T07.

`sample-crm` is developed with its own copy of the spec kit. Its spec 001 (`sample-crm/specs/001-crm-service-baseline/`) describes the service as a normal product. Drivers will work in that repository and can read everything in it, so the planted defects must not appear there. This spec holds what only the test bed needs: the planted defects, the tests the tasks rely on, the guarantee that nothing gives them away, and the recorded baseline commit. It is verified from this repository.

`sample-crm`'s own plan and tasks must not mention the planted defects either. The planting steps live in this spec's plan and are carried out with neutral commit messages. Its spec 001 is written so that the planted defects pass its acceptance criteria: for example, it never checks which contacts a page after page 1 contains.

## Scope

**In:**

- The `sample-crm` baseline, built and verified through its spec 001.
- The planted defects and existing tests that T02, T04, T06, and T07 rely on.
- No feature that a task asks for exists yet, and nothing in `sample-crm` names a planted defect or a task.
- CI that runs the tests and reports on the commit.
- The baseline tag, recorded in `docs/test-bed.md` as T01's base commit.

**Out:**

- Task records, expected results, and hidden acceptance tests: a later spec, which needs this baseline (`docs/test-bed.md` / Task records).
- Laya's first cases: a later spec, after spike 6 settles the typed question.
- Running any task, and the branch scheme for reruns: they belong to the runs themselves.
- Planted setups that are not in the baseline code (T08's low iteration limit, T10's scripted early merge): they belong to the task records.

## User scenarios

### S1 · Start the first task

The owner wants to start T01 from a known commit, so that every run and rerun begins from the same code.

1. They look up the baseline tag and T01's base commit in `docs/test-bed.md`.
2. They check out `sample-crm` at that commit, and the tests pass locally and in CI.

### S2 · A planted task takes its expected path

A driver works on T02, T04, T06, or T07 and meets the situation the task was designed around.

1. T02: page 2 of the contact list repeats the last contact of page 1, and no existing test fails because of it.
2. T04: the same request validation is written out in several places, and existing tests cover its behavior.
3. T06: emails are stored as sent, and the existing tests use emails that match exactly.
4. T07: an existing test asserts that contacts survive their company's deletion with the company cleared.

### S3 · The driver cannot see the plan

A driver explores `sample-crm`, including its specs and git history, and finds nothing that says which defects were planted or which tasks will come.

## Requirements

Baseline:

- **R1** The test bed baseline shall be `sample-crm` at a commit on `main` where its spec 001 is verified.
- **R2** If a task in `docs/test-bed.md` asks for a behavior, then the baseline shall not already have that behavior.
- **R3** The baseline's CI shall run every test on each push and pull request with `./mvnw -B verify` against a PostgreSQL service container given by environment variables, and report the result on the commit, identified by its SHA.
- **R13** Where a PostgreSQL is given by environment variables, the baseline's tests shall use it and start no Testcontainers database; otherwise they shall start PostgreSQL with Testcontainers.

Planted defects and tests:

- **R4** (T02) When a client lists contacts with a page number of 2 or more, the baseline shall start that page one contact too early, so that its first contact is the last contact of the previous page.
- **R5** (T02) The baseline shall have no test that fails because of the defect in R4, and no test that checks which contacts a page after page 1 contains.
- **R6** (T04) The baseline shall write its request validation rules out separately in each create and update operation of companies, contacts, and deals (six places), instead of keeping them in one shared place.
- **R7** (T04) The baseline's tests shall cover the validation behavior of every operation that has the duplicated validation of R6, so that a refactor that changes behavior makes a test fail.
- **R8** (T06) The baseline shall store contact email addresses as sent, without changing their case or surrounding whitespace, and shall accept a contact whose email address is the same as another contact's.
- **R9** (T06) Every baseline test that creates contacts shall use email addresses that differ in more than case or surrounding whitespace.
- **R10** (T07) The baseline shall include a test asserting that after a company is deleted, its contacts still exist and have no company.

Nothing gives it away:

- **R11** Nothing in `sample-crm` (code, comments, tests, documentation, specs, commit messages, branch names, or tag names) shall name a planted defect, a test-bed task, or this repository's test-bed documents.

Baseline commit:

- **R12** When the baseline is verified, `docs/test-bed.md` shall record the baseline tag (`baseline`), its commit SHA, and that commit as T01's base commit.

## Acceptance criteria

- **AC-1** (R1) Given `sample-crm` at the baseline tag, when its spec 001 is read, then its status is `done` and its verification evidence names that commit.
- **AC-2** (R2) Given the baseline, when each of T01 to T12 is compared with the API description and the code, then none of the behaviors they ask for exists yet. Recorded as a manual check, one line per task.
- **AC-3** (R3) Given the baseline commit, when its checks are viewed on GitHub, then a CI run that executed every test passed on that SHA. Recorded as a manual check with a link.
- **AC-4** (R4) Given the running baseline with five contacts, when page 1 and page 2 are listed with page size 2, then the first contact of page 2 is the second contact of page 1. Recorded as a check kept in this repository.
- **AC-5** (R5) Given the baseline, when its tests run, then all pass, and a search of the tests finds no assertion on the contents of a page after page 1. Recorded as a manual check.
- **AC-6** (R6) Given the baseline code, when its validation is read, then the same rules appear in each place R6 names and in no shared validator. Recorded as a manual check that lists the places.
- **AC-7** (R7) Given the baseline, when the validation in one place of R6 is changed to accept bad input, then at least one existing test fails. Recorded as a manual check, repeated for each record type.
- **AC-8** (R8) Given the running baseline, when a contact is created with the email `" Ana@Example.com "` and then another with `"ana@example.com"`, then both are accepted and each is read back exactly as sent. Recorded as a check kept in this repository.
- **AC-9** (R9) Given the baseline tests, when every email address they use to create contacts is listed, then no two differ only in case or surrounding whitespace. Recorded as a manual check.
- **AC-10** (R10) Given the baseline tests, when they are searched, then one test deletes a company and asserts that its contacts still exist with no company, and it passes.
- **AC-11** (R11) Given `sample-crm` at the baseline tag, when its files and full git history (messages, branches, tags) are searched for the task IDs in their two-digit form (T01 to T12), the task titles, "planted", "defect", "off-by-one", "test bed", and this repository's name, then nothing is found. Recorded as a manual check with the search commands used.
- **AC-12** (R12) Given the baseline verified, when `docs/test-bed.md` is read, then it names the baseline tag, the commit SHA, and that SHA as T01's base commit, and the SHA matches the tag in `sample-crm`.
- **AC-13** (R13) Given the baseline and a running PostgreSQL given by environment variables, when `./mvnw -B verify` runs on a machine with no container runtime, then every test passes; and given no such variables on a machine with Docker, the same command passes using Testcontainers. Recorded as a manual check.

## Open questions

- Q: Where is the request validation written out separately (R6)? A: In each create and update operation of the three record types, six places (user, 2026-10-18).
- Q: What is the baseline tag called? A: `baseline` (user, 2026-10-18).

## Changelog

- 2026-10-18 Created. `sample-crm` is developed with its own spec kit; its spec 001 holds the product behavior, and this spec holds what only the test bed needs (decided by the user, 2026-10-18).
- 2026-10-18 Clarified R6 (six places) and the tag name.
- 2026-10-18 From spike 002 G4: R3 now runs CI against a PostgreSQL service container; added R13 (external database switch) and AC-13. `sample-crm`'s spec 001 and draft plan need the matching neutral change.
