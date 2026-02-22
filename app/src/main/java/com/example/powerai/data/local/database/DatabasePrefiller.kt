package com.example.powerai.data.local.database

import android.content.Context
import java.io.FileOutputStream
import java.io.InputStream

object DatabasePrefiller {
    /**
     * Copy a prefilled database from assets into the app's database path if it does not already exist.
     * @param context application context
     * @param assetPath path inside assets (e.g. "databases/prefilled_knowledge.db")
     * @param dbName destination database file name (e.g. "powerai.db")
     * @return true if a file was copied, false if it already existed
     */
    fun createFromAsset(context: Context, assetPath: String, dbName: String = "powerai.db"): Boolean {
        val dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) return false

        // Ensure parent directories exist
        dbFile.parentFile?.mkdirs()

        context.assets.open(assetPath).use { input: InputStream ->
            FileOutputStream(dbFile).use { out ->
                val buf = ByteArray(8 * 1024)
                var len: Int
                while (input.read(buf).also { len = it } > 0) {
                    out.write(buf, 0, len)
                }
                out.flush()
            }
        }

        return true
    }
}
