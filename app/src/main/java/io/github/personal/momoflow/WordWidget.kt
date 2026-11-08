package io.github.personal.momoflow

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class WordWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateOne(context, appWidgetManager, it) }
    }

    companion object {
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, WordWidget::class.java))
            ids.forEach { updateOne(context, manager, it) }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, id: Int) {
            val store = SecureStore(context)
            val snapshot = store.snapshot()?.takeIf { store.isToday(it) }
            val word = snapshot?.words?.getOrNull(store.index().coerceAtLeast(0))
            val view = RemoteViews(context.packageName, R.layout.word_widget)
            view.setTextViewText(R.id.widget_word, word?.spelling ?: "打开墨墨流云助手")
            view.setTextViewText(R.id.widget_caption, if (word == null) "尚无今日真实单词" else "墨墨今日 ${snapshot?.progress?.finished ?: 0}/${snapshot?.progress?.total ?: 0}")
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val pending = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            view.setOnClickPendingIntent(R.id.widget_root, pending)
            manager.updateAppWidget(id, view)
        }
    }
}
