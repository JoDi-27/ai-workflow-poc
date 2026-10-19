# T04 One request validator

## Task

Move duplicated request validation from controllers into one validator, with no behavior change.

## Acceptance criteria

- AC-1: The create and update validation rules for companies, contacts, and deals live in one place. The controllers no longer write them out.
- AC-2: No behavior change: every request the API accepted before is still accepted, and every request it rejected still gets the same `400` response, naming the same fields with the same messages.
- AC-3: Creating a deal still requires a company; updating one still does not.
- AC-4: The existing tests pass unchanged.
