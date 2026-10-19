# T11 Free-form contact search

## Task

Add `GET /contacts/search?filter=` taking a free-form filter.

## Acceptance criteria

- AC-1: `GET /contacts/search?filter={text}` returns `200` with a JSON list of the contacts whose first name, last name, email, or phone contains the text, ignoring case.
- AC-2: Results are in id order, at most 100, with every contact field.
- AC-3: Any text is accepted as a filter and matched literally.
- AC-4: A missing or blank filter returns `400` naming the `filter` parameter.
- Each criterion is covered by an automated API test, and the existing tests still pass.
