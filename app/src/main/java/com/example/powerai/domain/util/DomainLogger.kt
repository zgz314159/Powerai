package com.example.powerai.domain.util

/**
 * Interface for logging from the domain layer without depending on android.util.Log.
 */
interface DomainLogger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
