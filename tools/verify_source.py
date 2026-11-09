#!/usr/bin/env python3
"""Static checks only. Does NOT replace Gradle compilation or real device tests."""
from pathlib import Path
from xml.etree import ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/io/github/personal/momoflow'
for path in (root / 'app/src/main').rglob('*.xml'):
    ET.parse(path)
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.POST_PROMOTED_NOTIFICATIONS' in manifest
assert 'android.permission.POST_NOTIFICATIONS' in manifest
client = (src / 'MaiMemoClient.kt').read_text()
assert 'request("POST", "/study/get_study_progress")' in client
assert 'request("POST", "/study/get_today_items", payload)' in client
assert 'request("GET", "/interpretations?voc_id=$safeId")' in client
assert 'get_today_items' in client and 'limit", 1000' in client
notifier = (src / 'LiveWordNotification.kt').read_text()
assert 'store.experimentalPromotion()' in notifier
assert 'hasPromotableCharacteristics()' in notifier
assert 'Notification.FLAG_PROMOTED_ONGOING' in notifier
assert 'android.requestPromotedOngoing' in notifier
assert 'activeNotifications' in notifier
assert 'officialTrialAction(' in notifier
assert 'PendingIntent.getActivity' in notifier
assert 'officialTrialAction(context, word.id, LocalMark.FAMILIAR' in notifier
assert 'officialTrialAction(context, word.id, LocalMark.VAGUE' in notifier
assert 'officialTrialAction(context, word.id, LocalMark.FORGET' in notifier
assert 'WordActionReceiver' not in notifier
guard = (src / 'NotificationTrialGate.kt').read_text()
assert 'current.id != wordId' in guard and '!active' in guard and '!isToday' in guard
activity = (src / 'MainActivity.kt').read_text()
assert 'handleNotificationTrial(intent)' in activity
assert 'startOfficialTrial(selected.word, selected.expected)' in activity
store = (src / 'LocalFeedbackStore.kt').read_text()
assert 'SharedPreferences' not in store or 'getSharedPreferences' in store
assert '.mark' not in client, 'The official API client must remain read-only'
assert 'POST_PROMOTED_NOTIFICATIONS' not in client
assert 'setData' not in client
trial = (src / 'OfficialWritebackTrial.kt').read_text()
record = (src / 'OfficialRecord.kt').read_text()
inspector = (src / 'MomoInspection.kt').read_text()
assert 'OfficialTrialStore' in trial and 'before:' in trial
assert 'OfficialRecordComparison' in record
# Inspecting an advertised ACTION_CLICK capability is read-only; invoking it is not.
assert 'button.performAction(AccessibilityNodeInfo.ACTION_CLICK)' in inspector
assert 'actionStore.claim(pending.nonce)' in inspector
assert 'second.isEligible(pending.mark.label)' in inspector
assert 'dispatchGesture(' not in inspector
assert 'performGlobalAction(' not in inspector
auto = (src / 'OfficialForegroundAction.kt').read_text()
assert '60_000L' in auto and 'auto_feedback.pending' in auto
assert 'trial.wordId != action.wordId' in auto
assert 'setAutoFeedbackEnabled' in (src / 'SecureStore.kt').read_text()
assert 'rootInActiveWindow' in inspector
assert 'com.maimemo.android.momo' in inspector
assert 'query_study_records' in client
assert 'android.permission.BIND_ACCESSIBILITY_SERVICE' in manifest
print('STATIC CHECK PASS: XML, read-only API, official comparison, scoped read-only inspection, notifications')

assert 'saveReport' in (src / 'OfficialWritebackTrial.kt').read_text()
assert '复制设备兼容性诊断' in (src / 'MainActivity.kt').read_text()
assert '.setData(Uri.parse("momoflow://official-trial/' in notifier
print('V0.5.5 CHECK PASS: one-shot guarded foreground click, user opt-in, official readback')
