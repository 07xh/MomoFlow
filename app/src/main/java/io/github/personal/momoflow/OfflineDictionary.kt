package io.github.personal.momoflow

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * ECDICT offline fallback. The database is generated from the pinned MIT ECDICT
 * dataset during the CI build. This is NOT the proprietary MaiMemo app dictionary.
 */
object OfflineDictionary {
    private const val DATABASE_ASSET = "ecdict.db"
    private const val DATABASE_FILE = "ecdict-v1.db"

    private fun openDatabase(context: Context): SQLiteDatabase? {
        val file = File(context.noBackupFilesDir, DATABASE_FILE)
        if (!file.exists()) {
            val temp = File(context.noBackupFilesDir, "$DATABASE_FILE.tmp")
            try {
                context.assets.open(DATABASE_ASSET).use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                if (!temp.renameTo(file)) {
                    temp.delete()
                    return null
                }
            } catch (_: Exception) {
                temp.delete()
                return null
            }
        }
        return try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (_: Exception) {
            null
        }
    }

    /** Call from a worker thread, never from notification/UI main thread. */
    fun prefill(context: Context, words: List<StudyWord>): Int {
        if (words.isEmpty()) return 0
        val database = openDatabase(context) ?: return 0
        val prefs = context.getSharedPreferences("momoflow_local", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        var inserted = 0
        try {
            for (word in words) {
                val cached = prefs.getString("meaning.${word.id}", null)?.trim().orEmpty()
                // Repair a placeholder saved by older builds when official meanings were empty.
                if (cached.isNotEmpty() &&
                    cached != "墨墨未返回公开释义" &&
                    cached != "墨墨未返回用户自建释义" &&
                    cached != "暂无可用释义"
                ) continue
                database.query(
                    "lexicon", arrayOf("translation", "phonetic"),
                    "word = ? COLLATE NOCASE", arrayOf(word.spelling.trim()),
                    null, null, null, "1"
                ).use { cursor ->
                    if (!cursor.moveToFirst()) return@use
                    val meaning = cursor.getString(0)?.trim().orEmpty()
                    if (meaning.isNotBlank()) {
                        val phonetic = cursor.getString(1)?.trim().orEmpty()
                        val display = if (phonetic.isBlank()) meaning else "[$phonetic] $meaning"
                        editor.putString("meaning.${word.id}", display)
                        inserted++
                    }
                }
            }
            editor.apply()
        } catch (_: Exception) {
            // A missing or broken optional dictionary must never destroy official study data.
        } finally {
            database.close()
        }
        return inserted
    }
}
