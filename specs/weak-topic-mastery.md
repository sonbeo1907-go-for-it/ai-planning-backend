# Weak Topic reinforcement and mastery specification

## Status

Implemented on the `feature/weak-topic-reinforcement-mastery` backend and
frontend branches, pending merge and operational review. The product-approved
mastery threshold is 80% on a dedicated five-question check.

The repository already contains `WeakTopic`, mastery-check quiz generation,
quiz attempts, and the states `UNRESOLVED`, `IN_REVIEW`, and `MASTERED`.
Mastery scoring must remain an explicit policy separate from the ordinary
daily-quiz pass result, even while both thresholds are 80%. Active `IN_REVIEW`
records must also remain part of Weak Topic planning until they are mastered.

## Improvised user story

As a `USER`, I want to complete a focused reinforcement check for one of my
Weak Topics after the day on which the weakness was detected, so that the
system can record evidence of mastery and stop prioritizing that weakness in
future Daily Plans.

This story does not delete Weak Topic history. It changes the current status
only when the exact mastery rule succeeds.

## Why the original story needs refinement

The phrase "on the following day" must not mean that the USER permanently
loses the opportunity if the quiz is not taken tomorrow. It defines the
earliest eligible local date, not a one-day deadline.

The phrase "remove from Weak Topics" means remove from the active Weak Topic
planning set. The historical record and quiz attempts remain available.

The daily Micro-Quiz pass threshold and the mastery threshold represent
different decisions:

- Daily Micro-Quiz pass: existing evaluation policy, currently 80%.
- Weak Topic mastery: at least 80% on a dedicated five-question check (4/5).

## Scope and actors

- An authenticated `USER` may view and attempt mastery checks only for Weak
  Topics owned by that USER.
- `ADMIN` has no access to personal Weak Topics, quizzes, answers, attempts, or
  mastery results.
- AI creates quiz questions; the backend owns eligibility, scoring, state
  transitions, and authorization.
- Manual Daily Plan creation remains available when AI quiz generation is
  unavailable.

## Domain ownership

A Weak Topic belongs to:

- one USER;
- one Roadmap;
- one exact RoadmapVersion;
- one exact Learning Unit within that version.

A mastery-check Quiz belongs to:

- the same USER;
- the exact Weak Topic;
- the same Roadmap and RoadmapVersion;
- the Learning Unit targeted by the Weak Topic.

A QuizAttempt is immutable historical evidence. Retrying creates another
attempt; it never overwrites an earlier result.

## Eligibility rules

- A new Weak Topic starts as `UNRESOLVED`.
- Its first mastery check becomes eligible on the calendar day after
  `unresolvedAt`, calculated using the USER's timezone.
- The detection date must be persisted or interpreted using a timezone
  snapshot so that a later profile-timezone change does not rewrite history.
- The USER may attempt the check on any eligible later date. Missing the first
  eligible day does not archive or master the Weak Topic.
- Additional weak evidence while the topic remains active updates its evidence
  but does not postpone the original eligibility date. Reopening a `MASTERED`
  topic starts a new next-day eligibility window.
- An existing generated but unsubmitted mastery quiz is returned instead of
  creating a duplicate.
- A `MASTERED` Weak Topic cannot generate another mastery check unless later
  learning evidence explicitly reopens it under the re-detection rule.
- Same-day attempts before eligibility return a business validation error; the
  frontend may show the next eligible local date.

## State machine

```text
                    review scheduled or opened
UNRESOLVED --------------------------------------> IN_REVIEW
     ^                                                  |
     |                                                  |
     | failed or incomplete mastery attempt             | at least 80% mastery attempt
     +--------------------------------------------------+------> MASTERED

MASTERED -- later explicit weak evidence for the same current Learning Unit --> UNRESOLVED
```

Rules:

- `UNRESOLVED` and `IN_REVIEW` are both active Weak Topic states.
- Merely scheduling a REVIEW task may change `UNRESOLVED` to `IN_REVIEW`, but
  it must not remove the Weak Topic from planning context.
- A mastery attempt below 80% returns the Weak Topic to `UNRESOLVED`, records
  the attempt, and permits a later retry.
- Only a submitted mastery attempt with at least four of five correct answers changes the state to
  `MASTERED` and records `masteredAt`.
- State transitions use optimistic or pessimistic concurrency protection so
  two submissions cannot produce inconsistent results.

## Re-planning rules

- Daily Plan context includes both `UNRESOLVED` and `IN_REVIEW` Weak Topics
  from the active RoadmapVersion.
- A Weak Topic is prioritized evidence, not permission to exceed the Daily
  Plan budget.
- Existing Daily Plan policy still permits at most one REVIEW task and at most
  30% of available daily time for all review work.
- A `MASTERED` Weak Topic is excluded from required or prioritized Weak Topic
  review context.
- Mastery does not delete an already activated Daily Plan Item. It affects
  subsequent planning only.
- Mastery does not silently mark a Roadmap Learning Unit complete or rewrite
  historical Learning Unit progress.

## RoadmapVersion behavior

- A Weak Topic remains attached to the exact RoadmapVersion and Learning Unit
  that produced it.
- Activating a new RoadmapVersion does not mutate the historical Weak Topic.
- If an unresolved Learning Unit is carried into a new version with preserved
  lineage, a dedicated carry-forward operation may create or map an active
  Weak Topic for the new Learning Unit.
- Title similarity alone must never transfer Weak Topic identity between
  versions.
- A historical Weak Topic that is not mapped to the active version remains
  readable in history but does not enter the new version's Daily Plan prompt.

## Mastery quiz requirements

- A mastery check tests only the targeted Learning Unit.
- The AI context may include the Learning Unit, parent Topic, parent Milestone,
  and relevant source-backed content.
- Roadmap and source text are untrusted data, never AI instructions.
- The generated mastery quiz contains exactly five questions so that four
  correct answers can meet the 80% threshold.
- Every question contains one correct answer, plausible options, and a concise
  explanation.
- AI output passes strict schema validation. An invalid response may retry at
  most twice after the original request.
- Quiz answers are graded only by the backend against the persisted answer key.
- Every question must be answered exactly once before submission.
- Mastery is true only when:

```text
total questions == 5
and correct answers >= 4
and score >= 80%
```

- Scoring uses integer correct/total counts; rounding cannot turn a failing
  result into mastery.

## Asynchronous generation

- Mastery quiz generation uses `AiExecution` with purpose
  `QUIZ_GENERATION`, target type `WEAK_TOPIC`, and the Weak Topic ID as target.
- Submission returns `202 Accepted` with the execution resource.
- The USER may close the UI while generation continues.
- Polling reads the owner-scoped execution until `SUCCEEDED` or `FAILED`.
- Success references the generated Quiz by result ID.
- Provider response content, prompt content, quiz questions, and answer keys do
  not enter execution failure messages, logs, or audit metadata.
- Idempotency prevents duplicate active executions for the same Weak Topic and
  request key.

## API

Existing endpoint groups remain appropriate:

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/roadmaps/{roadmapId}/weak-topics` | List owner-scoped Weak Topics, filterable by status |
| `POST` | `/api/v1/weak-topics/{weakTopicId}/mastery-check/generate` | Queue an eligible mastery check |
| `GET` | `/api/v1/weak-topics/{weakTopicId}/mastery-check/execution` | Recover the latest generation execution |
| `GET` | `/api/v1/weak-topics/{weakTopicId}/mastery-check/{quizId}` | Read one owner-owned check and its result |
| `GET` | `/api/v1/weak-topics/{weakTopicId}/mastery-checks` | Read the check history |
| `GET` | `/api/v1/ai-executions/{executionId}` | Read asynchronous generation state |
| `POST` | `/api/v1/weak-topics/{weakTopicId}/mastery-check/{quizId}/submit` | Submit one complete attempt |

The list endpoint defaults to active states for the normal planning UI. History
views may explicitly request `MASTERED`.

Every nested identifier is checked against the authenticated owner and exact
Weak Topic relationship. Foreign or mismatched IDs return
`RESOURCE_NOT_FOUND` without disclosing resource existence.

## Response requirements

A Weak Topic response includes:

- Weak Topic ID and state;
- Roadmap and exact RoadmapVersion IDs;
- Learning Unit, parent Topic, and parent Milestone context;
- trigger type and detected time;
- earliest eligible mastery date;
- latest mastery-attempt score when present;
- mastered time when applicable.

A mastery submission response includes:

- attempt ID;
- score and correct/total counts;
- `mastered` boolean;
- resulting Weak Topic status;
- whether another attempt is allowed;
- a localized USER-facing outcome message.

Correct answers and explanations are disclosed only according to the quiz
submission policy, never before submission.

## Failure and exception flows

- No default quiz provider: return a provider-configuration error; keep the
  Weak Topic active.
- Provider timeout or failure: mark only the `AiExecution` failed; do not alter
  Weak Topic state.
- Invalid AI output after retries: fail generation without persisting a partial
  quiz.
- Incomplete or duplicate answers: reject submission without grading or state
  change.
- Fewer than four correct answers: persist the attempt, return the state to
  `UNRESOLVED`, and do not set `masteredAt`.
- Duplicate submission of the same attempt: return an idempotent result or a
  stable conflict; never create duplicate attempt history.
- Concurrent attempts: only one state transition is committed, while every
  valid immutable attempt remains traceable according to the attempt policy.

## Error categories

- `WEAK_TOPIC_NOT_FOUND`
- `WEAK_TOPIC_NOT_ELIGIBLE`
- `WEAK_TOPIC_ALREADY_MASTERED`
- `QUIZ_NOT_FOUND`
- `QUIZ_ALREADY_SUBMITTED`
- `QUIZ_ANSWERS_INCOMPLETE`
- `QUIZ_ANSWER_DUPLICATE`
- `AI_PROVIDER_DEFAULT_REQUIRED`
- `AI_PROVIDER_UNAVAILABLE`
- `AI_OUTPUT_INVALID`
- `CONFLICT`
- `ACCESS_DENIED`

## Persistence considerations

- Do not delete the Weak Topic row when it becomes `MASTERED`.
- Preserve every Quiz and QuizAttempt used as mastery evidence.
- Enforce the USER, Roadmap, RoadmapVersion, Learning Unit, and Weak Topic
  relationships at both service and database levels where practical.
- One active generated-but-unsubmitted mastery quiz per Weak Topic is the safe
  default.
- Store date/time instants in UTC and preserve the timezone snapshot needed for
  the local eligibility date.
- Index active Weak Topics by owner, RoadmapVersion, and status for Daily Plan
  context queries.
- Hard deletion of referenced Weak Topics or mastery evidence is prohibited.

## Privacy and audit

- Weakness evidence, questions, answers, scores, and explanations are personal
  learning data.
- Logs and audit metadata contain no question content, answer content, prompt,
  source excerpt, or provider response.
- Audit events record resource IDs only for generation queued/succeeded/failed,
  mastery attempt submitted, Weak Topic mastered, and Weak Topic reopened.
- ADMIN cannot use audit access to retrieve personal learning content.

## Required tests

### Domain and service tests

- Four or five correct answers out of five mark the Weak Topic `MASTERED`.
- Three or fewer correct answers leave it active, regardless of rounding.
- A failed attempt returns `IN_REVIEW` to `UNRESOLVED`.
- `UNRESOLVED` and `IN_REVIEW` both remain in active planning context.
- `MASTERED` is excluded from later Daily Plan Weak Topic context.
- Mastery does not change Roadmap progress or historical Daily Plans.
- Eligibility uses the recorded local-date boundary and remains available after
  the first eligible day.
- Duplicate and concurrent submissions do not create contradictory state.

### AI and persistence tests

- Generation is owner-scoped and idempotent.
- A quiz cannot reference another Learning Unit, RoadmapVersion, or USER.
- Invalid output retries at most twice and persists no partial quiz.
- Every attempt remains readable after mastery.
- New-version carry-forward uses lineage, never titles.

### Controller and security tests

- Owner USER can list, generate, poll, submit, and view the result.
- Another USER receives `RESOURCE_NOT_FOUND`.
- ADMIN receives `403 Forbidden`.
- Premature generation returns `WEAK_TOPIC_NOT_ELIGIBLE`.

## Acceptance criteria

1. An unresolved Weak Topic becomes eligible for a mastery check on the next
   local calendar day and remains eligible afterward.
2. The mastery quiz targets exactly one owner-owned Learning Unit and preserves
   its RoadmapVersion context.
3. Only a submitted score of at least 80% (four of five correct) changes the Weak Topic to
   `MASTERED`.
4. Any result below 80% remains historical evidence and leaves the Weak Topic
   active for later review.
5. Both `UNRESOLVED` and `IN_REVIEW` remain available to Daily Plan re-planning;
   `MASTERED` does not.
6. Mastery removes the item from active Weak Topic planning without deleting
   Weak Topic or QuizAttempt history.
7. Generation failure never changes Weak Topic state and never blocks manual
   Daily Plan workflows.
8. Every operation is owner-scoped; ADMIN has no personal-learning access.

## Explicitly out of scope

- Automatic mastery based only on time spent or task completion.
- Deleting Weak Topic or quiz history after mastery.
- A general quiz engine for arbitrary public quizzes.
- Weighted mastery or knowledge maps.
- ADMIN correction of USER mastery state.
- Forcing review work beyond the Daily Plan time budget.
