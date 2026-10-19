# T01 Count contacts

## Task

Add `GET /contacts/count`, optionally filtered by company.

## Acceptance criteria

- AC-1: `GET /contacts/count` returns `200` with a JSON object `{"count": n}`, where `n` is the number of contacts.
- AC-2: `GET /contacts/count?companyId={id}` returns the number of contacts that belong to that company only.
- AC-3: A company with no contacts, or a company id that does not exist, gives `{"count": 0}`.
- AC-4: Existing endpoints, including `GET /contacts/{id}`, behave as before.
- Each criterion is covered by an automated API test, and the existing tests still pass.
