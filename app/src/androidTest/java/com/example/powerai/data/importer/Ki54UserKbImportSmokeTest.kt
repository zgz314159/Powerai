package com.example.powerai.data.importer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.powerai.AppConfig
import com.example.powerai.core.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * KI-54 / checklist B1 (data path): import a staged PaddleModels directory via
 * [FileKbDirectorySource] (no SAF). Host stage:
 * `adb push <dir> /data/local/tmp/powerai_user_kb/B_sample`
 * (knowledge_base.json + shots/). Test copies into app filesDir via shell
 * because apps cannot read /data/local/tmp on modern SELinux.
 */
@RunWith(AndroidJUnit4::class)
class Ki54UserKbImportSmokeTest {
    @Test
    fun import_staged_user_kb_directory() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val staged = materializeStagedKb(ctx)
        assumeTrue(
            "stage KB under /data/local/tmp/powerai_user_kb/B_sample",
            File(staged, KB_JSON_FILE_NAME).isFile,
        )

        val db =
            Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        try {
            val dao = db.knowledgeDao()
            val importer = UserKbPackageImporter(ctx, dao)

            val first = runBlocking { importer.importFromSource(FileKbDirectorySource(staged)) }
            assertTrue("first import: $first", first is UserKbImportResult.Imported)
            first as UserKbImportResult.Imported
            assertTrue("entries > 0", first.entries > 0)
            assertTrue("assets mirrored report > 0", first.assets > 0)
            assertEquals("room entries", first.entries, runBlocking { dao.getAll() }.size)
            assertEquals("fts", first.entries, runBlocking { dao.countFts() })

            val mirrorRoot = File(ctx.filesDir, AppConfig.USER_KB_MIRROR_DIR)
            assertTrue("mirror root exists", mirrorRoot.isDirectory)
            val mirrored = mirrorRoot.walkTopDown().filter { it.isFile }.count()
            assertTrue("mirrored files > 0 (got $mirrored)", mirrored > 0)

            val second = runBlocking { importer.importFromSource(FileKbDirectorySource(staged)) }
            assertTrue("unchanged skip: $second", second is UserKbImportResult.Skipped)

            val pkgs = runBlocking { importer.listPackages() }
            assertEquals(1, pkgs.size)
            assertTrue(pkgs[0].packageId.startsWith("user:"))
            val removed = runBlocking { importer.removePackage(pkgs[0].packageId) }
            assertTrue("remove: $removed", removed is UserKbPackageRemovalResult.Removed)
            assertEquals(0, runBlocking { dao.getAll() }.size)
        } finally {
            db.close()
        }
    }

    private fun materializeStagedKb(ctx: Context): File {
        val dest = File(ctx.cacheDir, "ki54_B_sample")
        val ui = InstrumentationRegistry.getInstrumentation().uiAutomation
        val pkg = ctx.packageName
        // Debuggable: shell `run-as` can write into app private storage.
        drainShell(ui.executeShellCommand("run-as $pkg rm -rf cache/ki54_B_sample"))
        drainShell(
            ui.executeShellCommand(
                "run-as $pkg cp -a /data/local/tmp/powerai_user_kb/B_sample cache/ki54_B_sample",
            ),
        )
        return dest
    }

    private fun drainShell(pfd: android.os.ParcelFileDescriptor) {
        java.io.FileInputStream(pfd.fileDescriptor).use { it.readBytes() }
        pfd.close()
    }
}
