# Roadmap onboarding title specification

## Status

Planned. This specification refines Roadmap onboarding without changing the
ownership or versioning model.

## User story

As a `USER`, I want to name a Roadmap while completing its onboarding, so that
I can distinguish multiple independent learning goals.

## Current gap

The current onboarding contract persists the goal, proficiency, daily
commitment, and expected duration, but it neither accepts nor returns the
Roadmap title. A Roadmap started through onboarding therefore has no
user-controlled name. Although AI Roadmap output includes a proposed title,
that title is not a safe substitute for explicit user ownership of metadata.

## Responsibility boundary

- The frontend owns the title input, its live character count, and presentation
  of an AI suggestion.
- The backend owns normalization, durable resume state, ownership enforcement,
  title-origin tracking, and protection from silent AI overwrite.
- AI may suggest a title. It never has authority to replace a title authored or
  accepted by the USER.
- Manual Roadmap creation must remain available when AI is unavailable.

## API contract

The existing onboarding endpoint group remains unchanged:

| Method | Path | Title behavior |
|---|---|---|
| `POST` | `/api/v1/roadmap-onboarding` | Start/resume and return the saved title state |
| `GET` | `/api/v1/roadmap-onboarding/current` | Return the title state for the unfinished onboarding |
| `GET` | `/api/v1/roadmap-onboarding/{roadmapId}` | Return one owner-scoped title state |
| `PATCH` | `/api/v1/roadmap-onboarding/{roadmapId}` | Save a partial title together with other wizard fields |
| `POST` | `/api/v1/roadmap-onboarding/{roadmapId}/complete` | Guarantee that the resulting Roadmap has a usable title |

`SaveRoadmapOnboardingRequest` adds an optional `title` field because partial
saves may occur before the USER reaches or completes the title input.

`RoadmapOnboardingResponse` adds:

- `title`: the current normalized Roadmap title;
- `titleOrigin`: `USER`, `GOAL_DERIVED`, `AI_SUGGESTED`, or `FALLBACK`.

`titleOrigin` is server-controlled. A client cannot claim that an automated
title was authored by the USER.

## Business rules

1. The title belongs to the Roadmap, never to `UserProfile`.
2. A supplied title is trimmed, must not be blank after normalization, and is
   limited to 200 characters.
3. Saving a title explicitly entered by the USER sets `titleOrigin = USER`.
4. Completing onboarding must never leave the Roadmap title null or blank.
5. If the USER has not supplied a title, completion derives a concise title
   from the goal and records `titleOrigin = GOAL_DERIVED`.
6. If no safe title can be derived, the backend uses the localized fallback
   “Lộ trình từ khảo sát” and records `titleOrigin = FALLBACK`.
7. AI Roadmap generation may return a suggested title. It may automatically
   replace only a `GOAL_DERIVED` or `FALLBACK` title.
8. AI must never replace a title whose origin is `USER`.
9. Accepting or editing an AI suggestion makes the resulting title
   user-authoritative and sets `titleOrigin = USER`.
10. Regeneration creates Roadmap content versions but does not reset title
    authority or overwrite a USER title.
11. Changing the title of one Roadmap never changes another Roadmap.
12. All reads and writes derive the owner from the JWT subject. A foreign ID
    returns `RESOURCE_NOT_FOUND`.

## Concurrency rule

AI generation is asynchronous. If the USER edits the title while generation is
running, the later AI result must observe the current entity version and title
origin. It must preserve the USER title rather than applying an older
suggestion. Optimistic locking or an equivalent atomic condition is required.

## Persistence considerations

- Reuse the existing `roadmaps.title` column.
- Persist title provenance explicitly; do not infer authority from title text.
- Existing nonblank titles should be treated conservatively as USER-owned
  during transition unless reliable provenance is available.
- Existing null titles require an explicit migration or application repair
  strategy. They must not be silently populated from private content in audit
  metadata or logs.
- This change does not rename or repurpose Course/Module legacy concepts.

## Audit and logging

Audit events may record the Roadmap ID and that metadata changed. They must not
copy the title, goal, source text, or AI prompt into audit metadata or
application logs.

## Validation and errors

- Blank or overlength input returns `VALIDATION_FAILED`.
- Editing completed onboarding through the onboarding endpoint continues to
  return `INVALID_STATUS_TRANSITION`.
- Foreign or mismatched Roadmap IDs return `RESOURCE_NOT_FOUND`.
- AI title failure must not block manual Roadmap completion because goal-based
  and static fallbacks remain available.

## Required tests

- Save, reload, and resume a USER-entered onboarding title.
- Normalize whitespace and reject blank/overlength titles.
- Complete onboarding with a USER title.
- Complete onboarding without a USER title and derive a nonblank fallback.
- Keep titles independent across multiple Roadmaps owned by one USER.
- Reject another USER's Roadmap ID without information disclosure.
- Confirm that ADMIN cannot access personal onboarding.
- Allow AI to replace a derived/fallback title.
- Prevent AI generation and regeneration from overwriting a USER title.
- Preserve a concurrent USER edit when an older AI execution completes.
- Confirm that title/goal values do not enter logs or audit metadata.

## Out of scope

- AI renaming an activated Roadmap without explicit USER action.
- Automatically synchronizing a title with later goal edits.
- Sharing, public discovery, or collaborative Roadmap naming.

