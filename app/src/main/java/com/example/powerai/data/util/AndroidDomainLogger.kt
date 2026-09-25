package com.example.powerai.data.util

import android.util.Log
import com.example.powerai.domain.util.DomainLogger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidDomainLogger @Inject constructor() : DomainLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
