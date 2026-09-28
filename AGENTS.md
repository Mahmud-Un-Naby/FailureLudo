# FailureLudo — shared agent instructions

## Scope and conventions
- Native Android Ludo: Kotlin + Jetpack Compose in `app/`; deterministic, pure/local
  rules engine in `game-engine/`; bot training in `ai-training/`.
- `main` contains the merged offline improvements and is the shared release baseline.
  Continue Android online development on `feat/online-testing`. The former
  `feat/offline-improvements` branch is retired; do not recreate it.
  Online builds remain for private testing; production publication requires authorization.
  See [online testing preparation](docs/online-testing.md) for isolation requirements.
- Rebuild/polish layout, board, pawns, movement, dice, sound, and supporting screens.
  The playable presentation is under user review; later user feedback supersedes proposals.
- Preserve rules, bots, saves, undo/redo, history/replay, and reliable pawn selection.
  Offline startup/play must require no internet, sign-in, or backend.
- Android online/Firebase development is authorized on `feat/online-testing`.
  Web and parity work remain paused unless explicitly requested. Backend deployment
  requires explicit authorization. Preserve deferred work and existing history.
- Follow nearby architecture and production-quality error handling. Keep presentation
  separate from rules; avoid speculative abstractions and unrelated cleanup.

## Focused workflow
- Treat each request as a bounded task, not a repository audit or the entire roadmap.
  Complete its implementation and appropriate verification without unnecessary approval stops.
- Use instructions already in context; do not reread unchanged files each turn.
  For code edits, check branch/worktree state, then inspect relevant files and direct
  dependencies. Use targeted `rg` searches; expand only when evidence requires it.
- Do not dump the directory, all source, plans, logs, or generated/dependency files
  into context. Read applicable nested instructions when working in their directories.
- Load the references below only for the task they describe; a link is not a reading
  checklist. Read relevant sections and latest handoff before historical proposals.
- Skip formal plans for small, clear edits. For complex work, use a short plan and
  update only the affected handoff when decisions or remaining work materially change.
- Use one agent by default; delegate only when requested. Research, screenshots,
  benchmarks, and repeated review passes need a concrete task-related purpose.
- Keep tool output bounded and updates/final answers concise: outcome, validation,
  and remaining blockers. Stop when the requested work and checks are complete.

## Verification
- Run the smallest meaningful checks for the affected behavior; add regression tests
  for bugs or rule changes. Broaden for shared-engine, persistence, build, or release risk.
- Documentation-only edits need link/diff checks, not an Android build. For code,
  use relevant tests/compilation; build an APK when needed for the change or handoff.
- Batch checks after a coherent change. Repeat only after relevant edits or failures.
  Do not run web/online suites for an isolated offline change.
- Device/visual/audio review is currently left to the user unless requested. Report
  unrun checks honestly; do not treat manual or release validation as completed.

## References — on demand
- Scope decisions, release preparation, target API validation:
  [plan 009](plans/009-offline-android-redesign-goal.md). API 36 migration is applied;
  the recorded Play notice deadline is 1 November 2026. Release checks remain open;
  production publication requires the user's authorization.
- Presentation changes/continuing the redesign: relevant sections and latest feedback
  in [plan 010](plans/010-offline-android-fresh-design-plan.md).
- Rules or replay behavior: [rule contract](docs/game-rules-live.md).
- Significant bot/ML experiments, model changes, or policy evaluation: follow
  [AI logging guidance](docs/ai-work-log/README.md). Routine assistant work is not an AI experiment.
- Older plans and other docs are background; open only when the task needs them.

## Git delivery
- Preserve unrelated user changes. Stage explicit task files, review the staged diff
  for scope and secrets; do not use blanket `git add -A`.
- Retain automatic commit/push for completed major edits, once per coherent verified
  task rather than each intermediate edit. Do not create empty commits for questions.
- Commit messages: sentence case, imperative verb, no period.
- Never force-push to `main` or skip hooks. Stay on the current effort's branch.
