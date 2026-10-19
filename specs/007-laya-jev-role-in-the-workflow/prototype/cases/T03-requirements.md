# T03 Deal stages

## Task

Deal stages `LEAD → QUALIFIED → WON / LOST`; invalid transitions return 409.

## Acceptance criteria

- AC-1: Every deal has a `stage`, shown in deal responses. A new deal starts at `LEAD`.
- AC-2: `PUT /deals/{id}/stage` with `{"stage": "..."}` moves the deal and returns it. The allowed moves are `LEAD → QUALIFIED`, `QUALIFIED → WON`, and `QUALIFIED → LOST`.
- AC-3: Every other move, including staying at the same stage, returns `409` as a problem response and leaves the stage unchanged.
- AC-4: A missing or unknown stage value returns `400` naming the `stage` field. An unknown deal returns `404`.
- AC-5: `PUT /deals/{id}` does not change the stage.
- Each criterion is covered by an automated API test, and the existing tests still pass.
