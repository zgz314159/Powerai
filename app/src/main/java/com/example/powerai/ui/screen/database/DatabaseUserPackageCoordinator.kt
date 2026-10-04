package com.example.powerai.ui.screen.database

import android.net.Uri
import com.example.powerai.data.importer.UserKbImportResult
import com.example.powerai.data.importer.UserKbPackageImporter
import com.example.powerai.data.importer.UserKbPackageRemovalResult
import kotlinx.coroutines.CancellationException

/**
 * User-package lifecycle for the database drawer: list, update (re-select the same directory,
 * unchanged content is skipped) and remove one confirmed package. A removal/update failure is
 * reported through [DatabaseUiState.userPackageMessage], never as a false success, and the list is
 * driven by the importer's own state flow.
 */
internal class DatabaseUserPackageCoordinator(
    private val userKbPackageImporter: UserKbPackageImporter,
    private val reduce: (DatabaseUiState.() -> DatabaseUiState) -> Unit,
    private val reload: () -> Unit,
) {
    /** Re-read the user package list (entry count, version time, PDF association). */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh() {
        try {
            userKbPackageImporter.refreshPackages()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // Best-effort refresh: keep the last known list on failure.
        }
    }

    fun requestRemove(packageId: String) {
        reduce { copy(pendingRemovePackageId = packageId) }
    }

    fun cancelRemove() {
        reduce { copy(pendingRemovePackageId = null) }
    }

    /** Remove exactly the selected user package; a failure is reported, never a false success. */
    suspend fun confirmRemove(packageId: String) {
        reduce { copy(pendingRemovePackageId = null, userPackageMessage = null) }
        val message =
            when (val result = userKbPackageImporter.removePackage(packageId)) {
                is UserKbPackageRemovalResult.Removed -> "已移除用户知识库包：${result.entries} 条条目"
                is UserKbPackageRemovalResult.Failed -> "移除失败：${result.reason}"
            }
        reduce { copy(userPackageMessage = message) }
        reload()
    }

    /** Import or update the package behind [treeUri]; unchanged content is skipped. */
    @Suppress("TooGenericExceptionCaught")
    suspend fun update(treeUri: Uri) {
        reduce { copy(userPackageMessage = "正在导入/更新…") }
        val message =
            try {
                when (val result = userKbPackageImporter.importDirectory(treeUri)) {
                    is UserKbImportResult.Imported -> "更新完成：${result.displayName} · ${result.entries} 条"
                    is UserKbImportResult.Skipped -> "内容未变化，已跳过：${result.displayName}"
                    is UserKbImportResult.Failed -> "更新失败：${result.reason}"
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (t: Throwable) {
                "更新失败：${t.message ?: "未知错误"}"
            }
        reduce { copy(userPackageMessage = message) }
        reload()
    }
}
