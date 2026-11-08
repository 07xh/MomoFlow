package io.github.personal.momoflow

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WordActionReceiver : BroadcastReceiver() {
    companion object {
        const val WORD_ID = "word_id"
        const val NEXT = "io.github.personal.momoflow.NEXT"
        const val REVEAL = "io.github.personal.momoflow.REVEAL"
        const val STOP = "io.github.personal.momoflow.STOP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val store = SecureStore(context)
        if (intent.action == STOP) {
            store.setActive(false)
            LiveWordNotification.stop(context)
            WordWidget.updateAll(context)
            return
        }
        val snapshot = store.snapshot() ?: run {
            LiveWordNotification.stop(context)
            return
        }
        if (!store.isToday(snapshot)) {
            store.setActive(false)
            LiveWordNotification.stop(context)
            WordWidget.updateAll(context)
            return
        }
        // No interaction can alter a closed session or a different currently displayed word.
        if (!store.active()) return
        val current = snapshot.words.getOrNull(store.index()) ?: return
        if (intent.getStringExtra(WORD_ID) != current.id) return
        when (intent.action) {
            NEXT -> if (snapshot.words.isNotEmpty()) {
                store.setIndex((store.index() + 1) % snapshot.words.size)
                store.setReveal(false)
            }
            REVEAL -> store.setReveal(!store.reveal())
        }
        LiveWordNotification.publish(context)
        WordWidget.updateAll(context)
    }
}
