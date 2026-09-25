package android.util

// Stub for android.util.Log used in unit tests.
// Provides static methods via companion object to match the Android Log API.
class Log {
    companion object {
        @JvmStatic fun d(tag: String, msg: String): Int = 0
        @JvmStatic fun i(tag: String, msg: String): Int = 0
        @JvmStatic fun w(tag: String, msg: String): Int = 0
        @JvmStatic fun e(tag: String, msg: String): Int = 0
        @JvmStatic fun w(tag: String, msg: String, tr: Throwable?): Int = 0
        @JvmStatic fun e(tag: String, msg: String, tr: Throwable?): Int = 0
        @JvmStatic fun i(tag: String, msg: String, tr: Throwable?): Int = 0
    }
}
