# match-skill

Web application where users register their skills and are matched with people
who want to learn or teach them, then schedule a meeting.

This file is the contract every agent working in this repository reads. The
harness injects it into every request, so keep it short: state rules that
change a decision, not descriptions the agent can read from the code itself.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java + Spring Boot, REST API |
| Frontend | React |
| Database | Supabase |
| Backend hosting | Render |
| Frontend hosting | Vercel |
| Source | GitHub |

Backend layering is explicit: `entity`, `repository`, `service`, `controller`,
`dto`. A controller never touches a repository directly, and an entity never
crosses the HTTP boundary — map to a DTO first.

## Language

All code, identifiers, comments, commit messages, and documentation are written
in **English**. Conversation with the user is in **Portuguese**.

## Directory ownership

The coordinator delegates work to the backend, frontend, and tester agents
in Orca. Agents share the filesystem, but not their conversation histories.
Keep implementation ownership separate to avoid concurrent overwrites:

| Path | Owner |
|---|---|
| `backend/` | Backend agent |
| `frontend/` | Frontend agent, including integration of Open Design screens |
| `AGENTS.md` | Coordinator |
| `docs/` | All agents; announce changes and use separate task files |

The tester validates both surfaces and reports failures. Coordinate any test
file edits with the implementation owner. Route frontend requests for backend
changes through the coordinator, with the required API contract documented.

Documentation lives in `docs/` at the repository root. Do not create a
directory named after the project inside the project (`match_skill/`), and do
not keep a second copy of a document under one — a duplicate has already been
written and removed once.

Before editing a file outside your own area, say so in the reply rather than
writing silently. There is no lock between the two: the last writer wins, and
the loser's work disappears with no error.

## Planning before implementation

Before developing, refactoring, or fixing code, first inspect the relevant
documentation and code, then save a task-specific plan as a Markdown file in
the repository-root `docs/` directory. This applies to every agent, including
backend and frontend, and to follow-up implementation tasks.

Each plan must state the objective, evidence from the current code, scope and
owned files, ordered implementation steps, API compatibility and dependencies,
risks, and build/test acceptance criteria. Use a distinct descriptive filename
per task and owner; do not overwrite another agent's plan. Keep the plan current
when scope changes and record actual validation results before reporting done.
Saving a plan is mandatory; it does not introduce a separate approval gate for
work already authorized by the user. Document cross-owner dependencies and
send them to the coordinator while continuing independent work.

## Harness rules

Never pass `sandbox_permissions` to a tool call. This session already runs at
`danger-full-access`, the widest sandbox mode, so there is nothing to escalate
to and every attempt fails three ways: a value outside the enum, a missing
`justification`, or a target that is narrower than the current mode and so is
rejected as "not strictly wider". Just call the tool with its normal arguments.

## Creating agent-teams members

`provider` and `model` name the LLM route a member runs on. They are not the
member's job title. Filling them with the role name fails with "no adapter
registered for provider".

`provider` is always `ollama`. For `model` and `reasoningEffort`, use exactly
the values named in the message asking for the member — do not carry over a
model from an earlier run or from elsewhere in this file, because which local
model is the right one changes as they are measured against each other.

The member's job goes in `name` and `role`, never in `provider` or `model`.

Always set `reasoningEffort` on a member. Leaving it empty lets the model
reason at its default, and on the qwen3 models the reasoning consumes the
whole output budget before any visible answer appears — the turn then ends
with no content and the agent loop fails.

## Definition of done

A change is finished when it builds and its tests pass. Report the actual
result — if a test fails, say which and paste the failing line. Do not describe
work as complete because the code was written.
