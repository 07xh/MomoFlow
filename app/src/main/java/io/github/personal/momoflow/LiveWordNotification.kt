package io.github.personal.momoflow

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.net.Uri

/** Adapted from FluidCapsule AospLiveUpdatePublisher (MIT). See THIRD_PARTY_NOTICES.md. */
object LiveWordNotification {
    private const val CHANNEL = "word_session_v1"
    private const val NOTIFICATION_ID = 2208
    private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

    private fun action(context: Context, name: String, requestCode: Int, vocId: String? = null): PendingIntent {
        val intent = Intent(context, WordActionReceiver::class.java).setAction(name)
        // Unique PendingIntent identity per displayed word; stale cards cannot act on a different word.
        if (vocId != null) {
            intent.putExtra(WordActionReceiver.WORD_ID, vocId)
            intent.data = Uri.parse("momoflow://word-action/${Uri.encode(name)}/${Uri.encode(vocId)}")
        }
        return PendingIntent.getBroadcast(context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }


    fun canPost(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun stop(context: Context) { manager(context).cancel(NOTIFICATION_ID) }

    fun publish(context: Context) {
        val store = SecureStore(context)
        val snap = store.snapshot() ?: return stop(context)
        if (!store.active() || !store.isToday(snap) || snap.words.isEmpty()) return stop(context)
        if (!canPost(context)) return
        val word = snap.words[store.index().coerceIn(0, snap.words.lastIndex)]
        val shownMeaning = if (store.reveal()) store.cachedMeaning(word.id) ?: "释义请在 App 内查询" else "轻触应用查看释义"
        manager(context).createNotificationChannel(NotificationChannel(CHANNEL, "正在背词", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "由用户主动开启的背词会话"
        })
        val open = PendingIntent.getActivity(context, 1, Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(word.spelling)
            .setContentText(shownMeaning)
            .setSubText("墨墨 · 今日 ${snap.progress.finished}/${snap.progress.total}")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "下一个", action(context, WordActionReceiver.NEXT, 2, word.id)).build())
            .addAction(Notification.Action.Builder(null, if (store.reveal()) "隐藏释义" else "显示释义",
                action(context, WordActionReceiver.REVEAL, 3, word.id)).build())
            .addAction(Notification.Action.Builder(null, "结束", action(context, WordActionReceiver.STOP, 4)).build())
        if (Build.VERSION.SDK_INT >= 36 && store.experimentalPromotion()) {
            builder.setShortCriticalText(word.spelling.take(7))
            // The OS decides whether to promote this notification; this does not guarantee a capsule.
            builder.setProgress(snap.progress.total.coerceAtLeast(1), snap.progress.finished.coerceAtLeast(0).coerceAtMost(snap.progress.total.coerceAtLeast(1)), false)
                .setStyle(Notification.ProgressStyle().setStyledByProgress(false)
                    .setProgress(snap.progress.finished.coerceAtLeast(0)))
                .addExtras(Bundle().apply { putBoolean("android.requestPromotedOngoing", true) })
        }
        manager(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun diagnostics(context: Context): String {
        if (Build.VERSION.SDK_INT < 36) return "Android 16 以下：使用标准通知"
        val allowed = manager(context).canPostPromotedNotifications()
        val experimental = SecureStore(context).experimentalPromotion()
        return "实验性请求：${if (experimental) "开启" else "关闭"} · 提升权限：${if (allowed) "允许" else "未允许或不受支持"}（是否显示由系统决定）"
    }
}
