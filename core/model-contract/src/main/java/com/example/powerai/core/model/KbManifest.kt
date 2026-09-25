package com.example.powerai.core.model

/**
 * Manifest for a knowledge base package.
 * Used for external KB management (download, versioning, rollback).
 */
data class KbManifest(
    val packageId: String,
    val version: String,
    val schemaVersion: String,
    val releaseTimestamp: Long,
    val contentHash: String,
    val displayName: String,
    val description: String? = null,
    val minAppVersion: Int = 1,
    val files: List<KbFileInfo> = emptyList()
)

data class KbFileInfo(
    val path: String,
    val size: Long,
    val sha256: String,
    val type: String // "json", "pdf", "index"
)
