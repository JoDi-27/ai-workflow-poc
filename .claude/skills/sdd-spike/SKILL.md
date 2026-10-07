---
name: sdd-spike
description: Run a spike as an interview with the user. Prepares a briefing and questions with the spike-researcher agent, asks them in small batches, records answers and any information the user adds as it comes, and keeps going until the spike's goal questions are resolved. Use for any spike spec, any PLAN.md Phase 0 spike, or whenever the user adds information about an active spike.
argument-hint: <spike ID | PLAN spike number or topic> [new information]
---

# Spike

Input: $ARGUMENTS

A spike is resolved together with the user. You and the spike-researcher agent find everything that can be looked up; the user supplies what only they know or decide. Keep the loop going across turns until the spike is resolved or the user stops. The spike file is the single record: anything learned that is not in it is lost.

## 1. Open the spike

- If the input starts with the ID of a spike spec, open `specs/<ID>-*/spec.md`.
- Otherwise find the matching spike in the `BACKLOG.md` "Spikes" section, by number or topic. Check `specs/_kit/sdd status` for an existing spec on it. If there is none, create one with `specs/_kit/sdd new spike "<title>"` and fill in the frontmatter (`phase: 0`, `depends_on` with the spec IDs of the spikes it depends on, if they exist, and `refs` with the BACKLOG and PLAN items), Why, and Goal questions (one checkbox per question from BACKLOG.md, numbered G1, G2, …).
- Claim it: `specs/_kit/sdd claim <ID>`. Keep the claim for as long as the interview runs in this session. If another session holds it, do not interview: write any notes the user gave into the spike's Inbox (change only that section), tell the user who holds the spike, and stop. If the claim is shown as stale and the user confirms that session is gone, take it over with `specs/_kit/sdd release <ID> --force` and claim again.
- If a spec in `depends_on` is not `done`, say so and ask whether to go ahead.
- Any text after the ID is new information from the user: take it in (step 3) before anything else.

## 2. Prepare

Do this in the first session, and again whenever no question in "Questions for you" is open while goal questions remain.

- Launch the `spike-researcher` agent with the spike file path (and, when preparing again, a focus on the goal questions still open).
- Write its Briefing and Researched sections into Briefing, keeping the sources. Write its questions into "Questions for you", numbered after the existing ones (Q1, Q2, …), each one a checkbox. Put its "Things to try" into Follow-ups or ask the user whether to run them now.
- If the status is `draft`, set it to `in-progress`. Add a changelog line and run `specs/_kit/sdd status --write`.

## 3. Take in information

Information arrives as answers to questions, text after the ID, notes in the Inbox section, or anything the user says about the topic during the session. For each piece:

- Append it to the Log: date, source (answer to Q3, note from the user, inbox, research), and the content, condensed but keeping the user's specifics (names, numbers, links, wording of constraints) exact.
- Update Findings under every goal question it affects, labelling each point as a **decision** (by the user), a **fact** (with source), or an **assumption** (to be confirmed).
- Tick the questions it answers. If the user skips one, tick it and append `(skipped: <reason>)`.
- New questions it raises: look up facts yourself, or launch spike-researcher with that focus for anything substantial, and log the result. Add questions only the user can answer to "Questions for you".
- If it contradicts something earlier in the Log, point out the conflict and ask which holds.
- Remove processed items from the Inbox.
- Tick a goal question once its Findings answer it completely.

## 4. Ask

- Show one progress line: goal questions answered n of m, questions for the user still open k.
- Ask the next 3 or 4 open questions, highest priority first. Use AskUserQuestion for questions with real options (the suggested option first, labelled Recommended). Ask open-ended questions in chat as a short numbered list, with the context the user needs to answer.
- Never ask what the docs or research can answer.
- Once per session, remind the user that they can add anything at any time, in chat, after the ID (`/sdd-spike <ID> <notes>`), or in the spike's Inbox section.

Then wait. When the user replies, go back to step 3.

## 5. Resolve

When every goal question is ticked:

- Draft "Recommendation and decisions" (one row per decision, alternatives, and why) and Follow-ups, and show them to the user for agreement. Revise until they agree.
- Tick the first deliverable, release the spike (`specs/_kit/sdd release <ID>`), run `specs/_kit/sdd status --write`, and suggest `/sdd-verify <ID>` to close the spike and update BACKLOG.md and PLAN.md.

If the user stops before that, leave the file consistent (Log and Findings up to date, Inbox processed, status `in-progress`), release the spike, and report what is left.
