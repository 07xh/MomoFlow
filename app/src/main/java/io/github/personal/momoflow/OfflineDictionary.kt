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
    fun lookup(context: Context, words: List<StudyWord>): Map<String, String> {
        if (words.isEmpty()) return emptyMap()
        val database = openDatabase(context) ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        try {
            for (word in words) {
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
                        result[word.id] = display
                    }
                }
            }
        } catch (_: Exception) {
            // A missing or broken optional dictionary must never destroy official study data.
        } finally {
            database.close()
        }
        return result
    }
}
