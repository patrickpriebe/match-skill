# Match Skill Project Documentation

Current implementation reference, reconciled on 2026-09-07. See
[project-status.md](project-status.md) for verification status and outstanding
external requirements; historical planning documents are not proof of delivery.

## Project Overview
The Match Skill project is a web application designed to help users find matches based on their skills and interests. Users can register, submit their skills, and find others who are interested in learning or teaching specific skills.

## Key Features
- **User Registration**: Users can create an account and submit their skills.
- **Skill Matching**: The system matches users based on their skills and interests.
- **Scheduling**: Users can schedule meetings with matched users.
- **Documentation**: Comprehensive documentation to guide users and developers.

## User Flow

The product is a skill exchange: each user declares what they can teach and
what they want to learn, and the system pairs them with people whose lists
complement their own.

### 1. Login

The login screen is the entry point. Two ways in:

- **Local account** — handled by the Java backend. The user signs up with an
  email and a strong password, then signs in with those credentials.
- **Google** — sign-in through OAuth 2.

### 2. First-time skill registration

A user reaching the application for the first time is sent to skill
registration before anything else. Two lists are collected:

- **Skills offered** — what the user can teach.
- **Skills wanted** — what the user wants to learn.

This step runs once. A returning user goes straight to the home page.

### 3. Home page — automatic matches

Finishing the first-time registration triggers a match search immediately, with
no action from the user. The home page then opens already showing the other
users worth scheduling a conversation with.

### 4. Search page

A dedicated page for looking up a skill and seeing which users offer it. This
is the deliberate path, as opposed to the automatic suggestions on the home
page.

### 5. Profile page

Shows a single user's skills — what they offer and what they want — so someone
can decide whether to reach out before scheduling.

### 6. Exchange lifecycle

A match is only a suggestion. It becomes an exchange through an explicit
sequence, and every later feature hangs off it:

| State | Meaning |
|---|---|
| `REQUESTED` | One user invited the other; awaiting an answer |
| `DECLINED` | The invitation was refused — terminal |
| `ACCEPTED` | Both agreed; a date and time are being settled |
| `SCHEDULED` | Date and time fixed |
| `COMPLETED` | The exchange happened |
| `CANCELLED` | Called off after acceptance — terminal |

Only a `COMPLETED` exchange enters the history and opens feedback. Without a
terminal success state there is nothing for either to describe, which is why
this section precedes both.

Each exchange records which skill travels in each direction, so a trade is
always legible as "A taught X, B taught Y".

On a `PARTIAL` request only the receiver's skill is known when the invitation
is sent — the requester's side is chosen by the receiver at acceptance, from
the requester's offered list. That is the whole point of allowing `PARTIAL`:
the second half of the trade is discovered, not declared up front.

### Where the meeting happens

The platform arranges the exchange; it does not host it. Both sides settle a
date and time here, then meet on whatever tool they prefer — Zoom, Meet, or
anything else — by sharing a link on the scheduled exchange.

`COMPLETED` is therefore marked by the participants, not detected by the
system. The platform never observes the meeting itself.

**Link validation.** A free URL field invites abuse: an exchange is a channel
to a stranger, and an unvalidated link is a phishing vector. The link is
checked server-side against an allowlist of meeting-tool hosts (for example
`zoom.us`, `meet.google.com`, `teams.microsoft.com`, `whereby.com`) and
refused otherwise, with `https` required. The allowlist is configuration, not
code, so hosts are added without a release.

This is a deliberate trade: it blocks tools nobody thought to list. The
alternative — accepting any URL — makes the platform a delivery mechanism for
whatever a bad actor wants to send.

### 7. Exchange history

A record of the exchanges a user has taken part in: how many, with which
users, and which skills were traded in each direction. The history is the
user's own ledger of the platform.

### 8. Feedback

After a `COMPLETED` exchange, each side can rate it and leave a comment saying
whether it went well. Feedback is attached to the exchange, so both the history
entry and the participants' profiles can surface it.

Reviews are mutually blind. A submitted review is private to its author until
both participants submit for the completed exchange. Both then publish together;
there is no timeout release. The participant feedback endpoint exposes submission
state without returning the hidden counterpart's rating or comment.

### 9. Reputation

Ratings aggregate into a score shown on the profile and used to order search
results. This is what keeps feedback from being decorative: without it people
leave comments that nobody consults when choosing a partner.

Aggregate only over `COMPLETED` exchanges with both participant reviews published,
and show the number of ratings next
to the score — a single five-star rating is not the same claim as forty.

### 10. Availability and time zone

Each user records the times they are generally free and the time zone they are
in. Scheduling between strangers in different cities is the normal case here,
not the exception, and a proposed time is ambiguous without a zone.

Availability also narrows matching: two complementary skill lists that never
overlap in time are not a usable match.

## Match rules

A match exists when another user **offers a skill this user wants**. That
single condition is enough to see them and to send them a request.

Matches come in two strengths:

| Strength | Condition |
|---|---|
| `PARTIAL` | They offer a skill I want. Nothing of mine is on their wanted list |
| `MUTUAL` | They offer a skill I want **and** I offer a skill they want |

`MUTUAL` is the complete trade — both sides already declared they want what the
other teaches. `PARTIAL` is deliberately kept: the request still travels, and
the receiver can look at the requester's skills and find something worth
learning that was never on their wanted list. Closing that door would discard
real trades that neither side could have predicted.

Both strengths follow the same approval path: a request is sent and the other
side approves or refuses. Nothing is automatic in either case.

**Ranking.** `MUTUAL` always comes before `PARTIAL` on the home page and in
search results. Within each group, order by reputation, then by overlap in
availability. Without this ordering `PARTIAL` volume would bury the trades that
are already complete on both sides.

## Skill vocabulary

Skills are the matching key, so free text breaks the product: `JS`,
`JavaScript` and `Java Script` become three unrelated skills and the users
behind them never meet.

Skills come from a controlled list. When a user types, the input suggests from
that list and they pick an entry rather than inventing a string. Terms with no
entry go to a queue for review instead of silently creating a new skill.

This is the engine of the product, not a form detail: match quality is bounded
by how consistently skills are named.

## Data model

The JPA entities own the schema; the tables below are what the application
creates on startup. Every id is a UUID.

### User

| Field | Notes |
|---|---|
| `id` | UUID |
| `email` | unique |
| `passwordHash` | null for accounts created through Google only |
| `googleSubject` | Google's stable user id; null for local-only accounts |
| `displayName` | shown on profile, search and invitations |
| `bio` | optional free text |
| `timeZone` | IANA name, e.g. `America/Sao_Paulo` |
| `skillsRegistered` | false until first-time registration completes |
| `createdAt` | |

An account may hold both a password and a Google subject: signing in with
Google using an address that already exists links the two rather than creating
a second account.

### Skill

The controlled vocabulary. Users select from it; they do not create rows.

| Field | Notes |
|---|---|
| `id` | UUID |
| `name` | canonical display name, unique |
| `slug` | normalized key used for matching |
| `status` | `APPROVED` or `PENDING_REVIEW` |

A term a user types with no entry is stored as `PENDING_REVIEW` and does not
participate in matching until approved.

### UserSkill

| Field | Notes |
|---|---|
| `id` | UUID |
| `userId` | |
| `skillId` | |
| `direction` | `OFFERED` or `WANTED` |

Unique on (`userId`, `skillId`, `direction`). The same skill may appear as both
for one user — that is not an error, and it is how someone teaching a basic
level while wanting an advanced one is represented today.

### Availability

| Field | Notes |
|---|---|
| `id` | UUID |
| `userId` | |
| `dayOfWeek` | |
| `startTime` / `endTime` | local to the user's `timeZone` |

Recurring weekly windows. Absolute dates are not stored here.

### Exchange

| Field | Notes |
|---|---|
| `id` | UUID |
| `requesterId` / `receiverId` | |
| `skillFromReceiver` | what the requester wants to learn — set at request |
| `skillFromRequester` | what the receiver will learn — null until acceptance on a `PARTIAL` |
| `strength` | `PARTIAL` or `MUTUAL`, computed at request time |
| `status` | `REQUESTED`, `DECLINED`, `ACCEPTED`, `SCHEDULED`, `COMPLETED`, `CANCELLED` |
| `scheduledAt` | absolute instant; null before `SCHEDULED` |
| `meetingUrl` | allowlist-validated; null before `SCHEDULED` |
| `createdAt` / `updatedAt` | |

### Feedback

| Field | Notes |
|---|---|
| `id` | UUID |
| `exchangeId` | |
| `authorId` | must be one of the two participants |
| `rating` | 1–5 |
| `comment` | optional |
| `createdAt` | |

Unique on (`exchangeId`, `authorId`) — one rating per person per exchange, and
only on a `COMPLETED` exchange.

Reputation is derived from this table, not stored on `User`: average rating
and count, computed over completed exchanges with bilateral feedback publication.

## API contract

REST over JSON. All paths are prefixed `/api`. Registration, login and Google
OAuth entry/callback routes are public. Other routes, including `/auth/me`,
require a bearer JWT.

### Auth

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/auth/register` | Create a local account (email, password, displayName, timeZone) |
| `POST` | `/auth/login` | Exchange credentials for a JWT |
| `GET` | `/auth/google` | Start the Google OAuth 2 flow |
| `GET` | `/auth/google/callback` | Complete it; links or creates the account |
| `GET` | `/auth/me` | Current user, including `skillsRegistered` |

The frontend routes a user with `skillsRegistered: false` to skill
registration instead of the home page.

Login and registration return `{token}`. After storing it, the frontend calls
`GET /auth/me` for the user and onboarding state. The frontend OAuth landing
route is `/auth/callback`; the backend provider callback is `/api/auth/google/callback`.

### Skills

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/skills?query=` | Autocomplete over the approved vocabulary |
| `POST` | `/skills/suggest` | Submit an unknown term for review |

### My skills

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/me/skills` | Both lists for the current user |
| `PUT` | `/me/skills` | Replace both lists; completes first-time registration |
| `DELETE` | `/me/skills/{id}` | Remove one entry |

### Availability

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/me/availability` | Current windows |
| `PUT` | `/me/availability` | Atomically replace windows and optional account timeZone |

The request is `{windows: [{dayOfWeek, startTime, endTime}], timeZone?}`.
Omitting timeZone or sending null preserves the current zone. Window clock
values remain local. Invalid input leaves both the zone and windows unchanged.
The response remains an array of windows; refresh `/auth/me` after saving a zone.

### Matches and search

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/matches` | Home feed, `MUTUAL` first, then `PARTIAL` |
| `GET` | `/search?skill=` | Users offering a given skill |
| `GET` | `/users/{id}` | Public profile: skills, reputation, availability |

### Exchanges

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/exchanges` | Send a request (`receiverId`, `skillFromReceiver`) |
| `GET` | `/exchanges?status=` | Incoming and outgoing, filterable |
| `GET` | `/exchanges/{id}` | One exchange |
| `POST` | `/exchanges/{id}/accept` | Accept; requires `skillFromRequester` for either strength |
| `POST` | `/exchanges/{id}/decline` | Refuse |
| `POST` | `/exchanges/{id}/schedule` | Set `scheduledAt` and `meetingUrl` |
| `POST` | `/exchanges/{id}/complete` | Mark it done |
| `POST` | `/exchanges/{id}/cancel` | Call it off after acceptance |

State transitions are endpoints rather than a `PATCH` on `status`, so each one
carries its own payload and its own authorization rule: only the receiver may
accept or decline, either side may schedule, complete or cancel.

### Feedback

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/exchanges/{id}/feedback` | Rate a completed exchange |
| `GET` | `/exchanges/{id}/feedback` | Participant state: mine, theirs, counterpartSubmitted |
| `GET` | `/users/{id}/feedback` | Published received feedback plus the viewer's own private submission |

Participant state is available only on completed exchanges. Nonparticipants
receive 403, missing exchanges 404, other states 409. Repeated submission returns
409 `FEEDBACK_ALREADY_SUBMITTED`. See
[backend-integration-contracts.md](backend-integration-contracts.md) for exact payloads.

### Conventions

Errors return the same shape throughout: an HTTP status plus a body carrying a
stable `code` and a human `message`. The `code` is what the frontend branches
on; the `message` is for people.

Vocabulary, matches, search and exchanges return `{items,page,size,total}`.
Page is nonnegative, size is positive and capped at 100. Availability and profile
feedback currently return arrays; own skills return offered/wanted collections.

## Screens

| Screen | Purpose |
|---|---|
| Login | Local sign-up and sign-in, plus Google OAuth 2 |
| Skill registration | First-time capture of offered and wanted skills |
| Home | Automatic matches, populated right after registration |
| Search | Look up a skill and the users offering it |
| Profile | One user's offered and wanted skills, plus reputation |
| Invitations | Incoming and outgoing requests, with accept and decline |
| Scheduling | Settling date and time on an accepted exchange |
| History | Completed exchanges: who, which skills, how many |
| Feedback | Rating and comment left after a completed exchange |
| Availability | The times a user is free, and their time zone |

## Project Structure
- **Frontend**: Built using React, with a focus on modern UI/UX practices and responsive design.
- **Backend**: Built using Java Spring Boot, following REST API standards and modern best practices.
- **Database**: PostgreSQL. During development it runs locally in Docker; a
  hosted instance (Supabase) comes later. Supabase is used as a Postgres host
  only — not for authentication — so the move is a connection-string change.
- **Schema**: JPA mappings describe application storage. Local development may
  use automatic updates. Existing hosted data requires reviewed schema rollout
  and index operations before schema validation; see the deployment guide.
- **Authentication**: handled entirely by the Spring backend. JWT for local
  sessions, and Google OAuth 2 as a second sign-in route.
- **Redis**: backs atomic rate-limit counters; unavailable storage fails closed.
  No application response cache or background-job queue is implemented.
- **API Documentation**: maintained in these Markdown contracts. Swagger/SpringDoc
  is not currently installed.

## Getting Started
1. Open the existing project folder. This workspace currently contains no Git
   metadata; obtain the intended remote from the owner before initializing or publishing.
2. Install Java 21 and Node compatible with the frontend's package.json. Run
   `npm ci` in frontend/ and `./mvnw.cmd -B verify` in backend/ on Windows.
3. Start the development PostgreSQL and Redis services with
   `docker compose up -d`. This uses persistent development volumes; do not use
   those datasets for destructive tests. The separate
   `docker-compose.validation.yml` provides disposable validation services.
4. Configure database/Redis settings and JWT credentials. Copy frontend/.env.example
   to a local environment file as needed; `/api` uses the Vite proxy. Google login
   requires separately configured provider credentials and registered callbacks.
5. Run `npm run dev` in frontend/ and `./mvnw.cmd spring-boot:run` in backend/.
   See [deployment-readiness.md](deployment-readiness.md) for hosted configuration.

## Contributing
- **Code Style**: Follow the Google Java Style Guide for backend code and the Airbnb JavaScript style guide for frontend code.
- **Testing**: JUnit 5/Spring tests for backend, Vitest for frontend units and
  adapters, Playwright/Edge for browser checks. Fixture and live-service results
  must be identified separately. Save implementation plans in docs/ first.
- **Code Review**: All contributions must be reviewed by at least one other developer to ensure code quality and consistency.

## License
No LICENSE file is present in this workspace. Licensing must be established by
the project owner before public distribution; no license is inferred here.
