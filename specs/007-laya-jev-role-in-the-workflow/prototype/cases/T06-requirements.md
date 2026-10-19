# T06 No duplicate contact emails

## Task

Reject duplicate contacts by email on create.

## Acceptance criteria

- AC-1: `POST /contacts` with an email that another contact already has returns `409` as a problem response and creates nothing.
- AC-2: Two emails are the same when they differ only in letter case or surrounding whitespace, so `" Ana@Example.com "` duplicates `ana@example.com`.
- AC-3: A contact with a different email is created as before.
- AC-4: Updating a contact is out of scope and behaves as before.
- Each criterion is covered by an automated API test, and the existing tests still pass.
