# Versioned system prompt management specification

## Status

Proposed for a later sprint and blocked on explicit product/security approval.
This capability expands ADMIN authority beyond provider/model configuration by
allowing controlled changes to production AI behavior.

## User story

As an `ADMIN`, I want to create, validate, publish, activate, and roll back
versioned System Prompt templates by AI purpose, so that AI behavior can be
changed safely without editing deployed code or overwriting history.

## Security boundary

- Prompt management is restricted to `ADMIN`.
- It does not grant ADMIN access to USER Roadmaps, plans, sources, progress,
  quizzes, answers, guidance, or rendered runtime prompts.
- System Prompt templates contain instructions and approved placeholders only.
  They never contain personal learning content or provider secrets.
- Runtime source/document/user content remains untrusted data and is inserted
  only through controlled delimiters after template selection.

## Domain model

### `AiPromptTemplate`

Long-lived root scoped to exactly one `AiPurpose`. It owns versions and points
to at most one active published version.

Suggested responsibilities:

- immutable purpose identity;
- display name and optional non-sensitive description;
- active version reference;
- enabled/archived lifecycle;
- optimistic-lock version.

### `AiPromptVersion`

Immutable after publication. Suggested fields:

- template ID;
- monotonically increasing version number;
- state: `DRAFT`, `PUBLISHED`, or `ARCHIVED`;
- template text;
- declared placeholders;
- creation/publish timestamps;
- creating/publishing ADMIN identity through AuditLog rather than plaintext
  duplication in business fields;
- optimistic-lock version while still DRAFT.

An `AiExecution` records the exact prompt-version ID resolved when it is queued
or started. Later activation or rollback never changes historical execution
meaning.

## State and activation rules

```text
create/edit copy
     |
     v
   DRAFT --validate/publish--> PUBLISHED --archive--> ARCHIVED
                                  |
                                  +--activate/rollback target
```

- Editing is allowed only while a version is `DRAFT`.
- Publishing validates content and makes that version immutable.
- At most one PUBLISHED version is active per purpose.
- Activation changes an atomic active-version pointer; it does not mutate the
  previously active version.
- Rollback activates an earlier PUBLISHED version.
- A DRAFT or ARCHIVED version can never be selected at runtime.
- Archiving a template/version cannot break an `AiExecution` historical
  reference.

## Purpose scope

The initial approved purpose allowlist must be decided before implementation.
Likely candidates already represented by `AiPurpose` include Roadmap
generation, Daily Plan generation, quiz generation, and task guidance.

Purpose-specific templates cannot be selected for another purpose. Provider
and model selection remains the responsibility of US-ADM-01; the first prompt
management implementation has no provider-specific override.

## Placeholder policy

- Each purpose has a code-owned allowlist of placeholder names and types.
- Validation rejects unknown placeholders, missing required placeholders,
  unsupported template syntax, and content beyond the approved size limit.
- Rendering fails closed when a required value is unavailable.
- Placeholder values are escaped/serialized by the server, never interpolated
  by arbitrary expression evaluation.
- Template syntax cannot invoke code, access environment variables, read files,
  or resolve secrets.
- Source and USER content is wrapped in explicit untrusted-data boundaries.

## Proposed API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/admin/ai-prompt-templates` | List templates and active versions |
| `POST` | `/api/v1/admin/ai-prompt-templates` | Create a purpose-scoped template root |
| `GET` | `/api/v1/admin/ai-prompt-templates/{templateId}` | Read template metadata and version summaries |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions` | Create a DRAFT version or editable copy |
| `GET` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}` | Read one version |
| `PATCH` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}` | Edit a DRAFT with optimistic locking |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}/validate` | Validate syntax and placeholders |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}/preview` | Render with approved synthetic data |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}/publish` | Publish an immutable version |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/versions/{versionId}/activate` | Atomically activate/roll back to a published version |
| `POST` | `/api/v1/admin/ai-prompt-templates/{templateId}/archive` | Archive without hard deletion |

All mutations require the last observed entity version. Stale changes return
`CONCURRENT_MODIFICATION`.

## Preview and testing

- Preview uses fixed or ADMIN-entered synthetic values only.
- It never queries a USER resource to populate placeholders.
- The first implementation should default to local rendering and validation,
  not a provider call.
- If provider-backed synthetic testing is later approved, it must use a
  separate explicit action, configured timeout/cost controls, sanitized result,
  and AuditLog event.

## Runtime resolution and fallback

- Runtime selects the active PUBLISHED version for the execution purpose.
- Selection and prompt-version reference are deterministic.
- The system never silently selects the newest DRAFT.
- The team must approve whether missing/invalid active configuration fails the
  execution or invokes a versioned code-bundled fallback.
- A fallback must be observable through sanitized execution metadata without
  exposing prompt text.
- Manual learning workflows remain available when prompt resolution or AI is
  unavailable.

## Secret and content controls

Reject template content containing known secret-reference syntax, credential
material, or forbidden placeholders. API responses may return prompt text only
to authorized ADMIN prompt-management screens; logs, AuditLog, and execution
failure metadata contain only IDs, versions, purposes, actions, and sanitized
codes.

Prompt text must not be copied into `AiExecution.failureMessage` or provider
analytics. Runtime rendered prompts and provider responses are never exposed by
ADMIN execution-history endpoints.

## Audit events

Audit at least:

- template created or archived;
- draft version created or updated;
- version validation failure/success where operationally useful;
- version published;
- active version changed or rolled back;
- provider-backed synthetic test, if ever approved.

Audit metadata contains actor, purpose, template/version IDs, action, request
ID, and timestamp. It does not contain prompt text, rendered context, personal
content, or secrets.

## Persistence and migration

- Introduce independent prompt template/version tables; do not store prompts in
  provider configuration fields.
- Enforce unique version numbers per template.
- Enforce at most one template root per purpose in the first implementation,
  unless the team approves another selection dimension.
- Enforce valid state values and referential integrity from execution to prompt
  version.
- Use restrictive deletion behavior. Archive rather than hard-delete records
  referenced by executions.
- Existing code-bundled prompts need an explicit seed/cutover strategy. Do not
  switch runtime behavior until equivalent seeded versions are validated.

## Required tests

- USER receives 403 for every prompt-management endpoint.
- ADMIN can create a template and DRAFT version.
- Unknown/missing placeholders and invalid syntax are rejected.
- DRAFT edits use optimistic locking.
- Published versions are immutable.
- Only a PUBLISHED version can be activated.
- Exactly one version is active per purpose under concurrent activation.
- Rollback changes only the active pointer and preserves all versions.
- Runtime resolves the correct purpose and records the exact version on the
  execution.
- Existing executions retain historical prompt-version references.
- Synthetic preview never reads USER data.
- Secrets, prompt text, runtime context, and provider responses are absent from
  logs, audit metadata, and execution diagnostics.
- Archiving cannot break referenced execution history.
- Manual workflows remain available during missing prompt/AI configuration.

## Decisions required before implementation

- Initial purpose allowlist.
- Whether every ADMIN can publish/activate or a stronger permission/workflow is
  required.
- Runtime failure versus approved code-bundled fallback.
- Prompt/version retention and archive policy.
- Maximum prompt size and template syntax.
- Whether provider-backed synthetic tests belong in a later story.

## Out of scope

- USER-authored prompts.
- Personal content in reusable prompt templates.
- Provider-specific prompt overrides in the first version.
- A/B testing, automatic prompt optimization, and automatic publishing.
- Viewing raw runtime prompts or provider responses.
