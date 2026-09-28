# Offline testing and feedback workflow

Prepared September 25, 2026. Scope: offline Android; no analytics added.

## Current status

The pregame UI update was released from `feat/offline-improvements` as 1.0.9
(code 13). A fresh Play API inventory confirms `alpha: completed 1.0.9 (13)`;
13 is the highest visible uploaded code. The signed release APK and AAB built
successfully. Device visual review and tester retests remain pending.

The September 25 API audit found 1.0.9 (code 11) published to closed alpha testing
in Bangladesh, while internal testing still had code 1. The owner reports the
additional 14-day period has already elapsed, with most recent improvements shipped
in two releases during the last two days. Current Console eligibility/counts and
the exact production-access decision are unverified. No tester reports imported yet.

Use the Console eligibility display to confirm the requirement: at least 12 closed
testers continuously opted in for 14 days for applicable personal accounts. Family
and friends are valid recruitment sources. Internal-only testers do not count.
Source: https://support.google.com/googleplay/android-developer/answer/14151465

## How we work

Owner recruits testers, shares instructions, checks Console eligibility, and brings
private testing feedback here. During working sessions the assistant organizes
feedback, investigates issues, implements agreed fixes, records retests, and prepares
application answers from actual evidence. No background monitoring or reminders
are configured. This setup sends no invitations, feedback replies, or releases.

Focus next on the current redesigned build. Do not invent a fresh 14-day restart
because of an update, or assume elapsed time proves current-build readiness.

## Collect feedback through Google Play

Testers use their opted-in account to open FailureLudo in the Play Store and find
the private feedback section (wording varies). Ask for honest observations.
Owner reads Play Console > Monitor and improve > Ratings and reviews > Testing
feedback and supplies copied text or screenshots with personal details removed.
Private testing feedback is not a public store review. Fastlane cannot fetch it
through the Reply to Reviews API, which covers production reviews only.

Sources:
- https://support.google.com/googleplay/answer/7003180
- https://developer.android.com/google/play/developer-api#reply-to-reviews

## Short report from a tester

```text
Tester label (T01, T02...):
Date:
Phone model / Android version:
App version / code:
Modes and features tried:
Game completed or scenario tested:
What worked / what was confusing:
Issue steps, expected result, actual result (if any):
Feedback source and date:
Optional screenshot, recording, or FLN export:
```

Duration and game counts are optional estimates. No need for every roll or a video
of every game. A specific successful test is useful too. For rules/replay problems,
History > Export provides an FLN file; use anonymous player names when sharing.
An FLN export helps reproduction but does not prove Play opt-in or engagement.
Record supplementary feedback's actual channel, such as WhatsApp, separately.

## Coverage and follow-up

Spread these checks across testers/devices through genuine play:
- Finish games against bots and with local friends; include team mode.
- Exercise pairs, captures, home entry, undo/redo, and history/replay.
- Check airplane mode, background/resume, close/reopen, and rotation.
- Check touch targets, readability, sound/mute, and reduced motion.
- Retest reported problems on the build containing their fixes.

No arbitrary daily minutes or feedback-post quota is part of this plan.
A code fix is not a confirmed tester retest. Track: reported -> reproduced ->
fixed_awaiting_retest -> verified_fixed, or deferred_with_reason.

## Records and application

Blank CSV templates live in docs/testing-templates. Actual reports and evidence
belong in testing-private, ignored by Git. Use tester labels; keep email lists in
Play Console. Unknown counts/dates remain unknown. No fabricated example records.

Before reapplying, check Console eligibility, feedback on the current build,
critical issues and retests, device coverage, pre-launch results, and declarations.
Summarize actual recruitment, engagement, feedback channels, changes and readiness.
Do not invent professional testing, crash-free claims, or guaranteed approval.
Raw gameplay logs are debugging aids, not an assumed application attachment.
