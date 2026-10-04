package com.example.powerai.core.model

/**
 * Namespacing for knowledge-base package ids so results and UI can tell a directory the user
 * imported (`user:<sha>`) apart from the bundled assets (`asset:...`, or a bare hash) and from
 * manual/legacy rows (`null`).
 */
object KnowledgePackages {
    const val USER_PREFIX: String = "user:"

    fun isUserPackage(packageId: String?): Boolean = packageId?.startsWith(USER_PREFIX) == true
}
