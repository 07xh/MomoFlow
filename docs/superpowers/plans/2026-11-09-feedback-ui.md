# MomoFlow 0.5.6 implementation plan

Goal: Complete the existing foreground feedback handoff and simplify the native UI.
Architecture: Keep native Android Views, existing official read-only API and the guarded one-shot accessibility action. No new platform or dependencies.

1. Feedback: route app and notification taps through the same single pending trial; cancel clears the action; baseline/readback callbacks check token, day, selected word and trial identity. A baseline already completed is rejected. Matching official feedback triggers an authoritative list/progress refresh; failures retain pending trial and old cache.
2. UI: two tabs (study/settings); word, official progress, session and three official-feedback controls on study; token editing in dialog, switches and optional diagnostics in settings. Local practice remains a separate secondary dialog. Insets include keyboard/cutout, content max width 640dp, buttons wrap at large font sizes, preserve scroll position across redraws.
3. Integrate useful PR #4 changes (empty JSON progress request, public meaning priority and explicit source), preserving all 0.5.5 feedback guards.
4. Verification: regressions for same-word/day/current-trial eligibility and authoritative refresh; source validation, Gradle unit tests and APK build on the same PR revision; inspect UI on emulator if available. Physical OnePlus and real account remain explicitly unverified.

Review focus: cancellation during baseline; token change during any worker; switching words during pending trial; process/activity recreation; delayed official sync and empty day list.
