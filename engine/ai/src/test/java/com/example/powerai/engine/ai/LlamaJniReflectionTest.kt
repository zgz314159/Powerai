package com.example.powerai.engine.ai

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Characterization tests for [LlamaJniReflection] using fake classes only.
 *
 * No real native library is loaded: the reflection target class is injected
 * through the `nativeLibClassCache` seam, and `android.util.Log` is stubbed in
 * test sources. Covers class lookup (fatal and cached), map unwrapping,
 * callback proxies, primitive/default returns, missing members, invocation
 * exceptions and the recoverable vs fatal error paths.
 */
class LlamaJniReflectionTest {
    @Before
    fun setUp() {
        LlamaJniReflection.nativeLibClassCache = null
        LlamaJniReflection.aarStructureLogged = false
    }

    @After
    fun tearDown() {
        LlamaJniReflection.nativeLibClassCache = null
        LlamaJniReflection.aarStructureLogged = false
    }

    @Test
    fun `class lookup returns the cached fake class`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionBareNativeLib::class.java

        assertSame(ReflectionBareNativeLib::class.java, LlamaJniReflection.getNativeLibClass())
        // second call short-circuits on the cache
        assertSame(ReflectionBareNativeLib::class.java, LlamaJniReflection.getNativeLibClass())
    }

    @Test
    fun `missing native class is a fatal class not found error`() {
        LlamaJniReflection.nativeLibClassCache = null

        assertThrows(ClassNotFoundException::class.java) {
            LlamaJniReflection.getNativeLibClass()
        }
    }

    @Test
    fun `static map field yields the load capable session`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionMapNativeLib::class.java

        val instance = LlamaJniReflection.getNativeLibInstance(null)

        assertTrue("expected the session, got $instance", instance is ReflectionFakeSession)
        assertTrue((instance as ReflectionFakeSession).nativeLoadStateFile("state.bin"))
    }

    @Test
    fun `nested maps resolve to the first value because recursive unwrap is blocked`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionNestedMapNativeLib::class.java

        val instance = LlamaJniReflection.getNativeLibInstance(null)

        // Recursive unwrapping cannot run on the JVM (iterator access is
        // blocked), so the field strategy falls back to the first value: the
        // nested map itself.
        assertTrue("expected the nested map, got $instance", instance is java.util.Map<*, *>)
        assertFalse(instance is ReflectionFakeSession)
    }

    @Test
    fun `iterator lookup on map values is blocked on the jvm`() {
        val map = mutableMapOf<String, Any>("inner" to ReflectionFakeSession())
        val values = map.javaClass.getMethod("values").invoke(map)
        val iterator = values.javaClass.getMethod("iterator")

        // LinkedHashMap$LinkedValues is not an exported java.base class, so the
        // iterator lookup that unwrapMapForInstance relies on cannot be invoked
        // on a modern JVM; production therefore always takes the first-value
        // fallback for map-typed instances here.
        assertThrows(java.lang.IllegalAccessException::class.java) {
            iterator.invoke(values)
        }
    }

    @Test
    fun `map without load capable values falls back to the first value`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionFallbackMapNativeLib::class.java

        assertEquals("plain-value", LlamaJniReflection.getNativeLibInstance(null))
    }

    @Test
    fun `static factory method result is used when no instance field exists`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionFactoryNativeLib::class.java

        val instance = LlamaJniReflection.getNativeLibInstance(null)

        assertTrue("expected factory session, got $instance", instance is ReflectionFakeSession)
    }

    @Test
    fun `no arg constructor is the last resort instance source`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionBareNativeLib::class.java

        val instance = LlamaJniReflection.getNativeLibInstance(null)

        assertTrue("expected constructor instance, got $instance", instance is ReflectionBareNativeLib)
    }

    @Test
    fun `throwing factory method is recoverable via constructor`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionThrowingFactoryNativeLib::class.java

        val instance = LlamaJniReflection.getNativeLibInstance(null)

        assertTrue(
            "invocation exception must not escape, got $instance",
            instance is ReflectionThrowingFactoryNativeLib,
        )
    }

    @Test
    fun `unconstructable class resolves to null instead of throwing`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionUnconstructableNativeLib::class.java

        assertNull(LlamaJniReflection.getNativeLibInstance(null))
    }

    @Test
    fun `ensure native instance short circuits on an existing instance`() {
        val existing = ReflectionFakeSession()

        assertSame(existing, LlamaJniReflection.ensureNativeInstance(null, existing))
    }

    @Test
    fun `ensure native instance null path uses parameterized constructors`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionIntCtorNativeLib::class.java

        val instance = LlamaJniReflection.ensureNativeInstance(null, null)

        assertTrue("expected constructed instance, got $instance", instance is ReflectionIntCtorNativeLib)
    }

    @Test
    fun `ensure native instance does not fall back to a no arg constructor`() {
        LlamaJniReflection.nativeLibClassCache = ReflectionBareNativeLib::class.java

        // Unlike getNativeLibInstance, the ensure path only knows parameterized
        // constructors and static factories.
        assertNull(LlamaJniReflection.ensureNativeInstance(null, null))
    }

    @Test
    fun `callback proxy handles identity methods`() {
        val proxy = LlamaJniReflection.createNamedCallbackProxy(ReflectionFakeCallback::class.java, "stream")

        assertEquals(
            "stream-proxy(com.example.powerai.engine.ai.ReflectionFakeCallback)",
            proxy.toString(),
        )
        assertEquals(System.identityHashCode(proxy), proxy.hashCode())
        assertTrue(proxy == proxy)
        assertFalse(proxy == LlamaJniReflection.createNamedCallbackProxy(ReflectionFakeCallback::class.java, "stream"))
        assertFalse(proxy.equals(null))
    }

    @Test
    fun `stream callback proxy dispatches tokens and returns defaults`() {
        @Suppress("UNCHECKED_CAST")
        val callback = LlamaJniReflection.createNamedCallbackProxy(ReflectionFakeCallback::class.java, "stream") as ReflectionFakeCallback

        // primitive and reference defaults for unknown members
        assertEquals(0L, callback.total())
        assertFalse(callback.isActive())
        assertNull(callback.label())

        // stream-specific members fall through to defaults without throwing
        assertEquals(0, callback.onToken("chunk"))
        assertEquals(0, callback.onToken(""))
        callback.onDone()
        callback.onToolCall("search", "{}")
        callback.onError("boom")
        callback.onFinish()
    }

    @Test
    fun `non stream callback proxy still finishes on done`() {
        @Suppress("UNCHECKED_CAST")
        val callback = LlamaJniReflection.createNamedCallbackProxy(ReflectionFakeCallback::class.java, "generic") as ReflectionFakeCallback

        callback.onDone()
        assertEquals(0L, callback.total())
        assertFalse(callback.isActive())
    }

    @Test
    fun `default return values cover every primitive type`() {
        assertNull(LlamaJniReflection.defaultReturnValue(java.lang.Void.TYPE))
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Boolean.TYPE) == false)
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Integer.TYPE) == 0)
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Long.TYPE) == 0L)
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Float.TYPE) == 0f)
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Double.TYPE) == 0.0)
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Short.TYPE) == 0.toShort())
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Byte.TYPE) == 0.toByte())
        assertTrue(LlamaJniReflection.defaultReturnValue(java.lang.Character.TYPE) == 0.toChar())
        assertNull(LlamaJniReflection.defaultReturnValue(String::class.java))
    }

    @Test
    fun `proxy instances implement the requested interface`() {
        val proxy = LlamaJniReflection.createNamedCallbackProxy(ReflectionFakeCallback::class.java, "stream")

        assertTrue(Proxy.isProxyClass(proxy.javaClass))
        assertTrue(ReflectionFakeCallback::class.java.isInstance(proxy))
    }
}

/** Fake JNI session exposing the probe method used by map unwrapping. */
class ReflectionFakeSession {
    fun nativeLoadStateFile(path: String): Boolean = path.isNotBlank()
}

/** Fake target class whose static INSTANCE map holds a load-capable session. */
class ReflectionMapNativeLib {
    companion object {
        @JvmField
        val INSTANCE: MutableMap<String, Any> = mutableMapOf("session" to ReflectionFakeSession())
    }
}

/** Fake target class whose static INSTANCE map nests another map. */
class ReflectionNestedMapNativeLib {
    companion object {
        @JvmField
        val INSTANCE: MutableMap<String, Any> =
            mutableMapOf("outer" to mutableMapOf<String, Any>("inner" to ReflectionFakeSession()))
    }
}

/** Fake target class whose static INSTANCE map holds no load-capable value. */
class ReflectionFallbackMapNativeLib {
    companion object {
        @JvmField
        val INSTANCE: MutableMap<String, Any> = mutableMapOf("only" to "plain-value")
    }
}

/** Fake target class exposing only a static factory method. */
class ReflectionFactoryNativeLib {
    companion object {
        @JvmStatic
        fun getInstance(): ReflectionFakeSession = ReflectionFakeSession()
    }
}

/** Fake target class with nothing but a no-arg constructor. */
class ReflectionBareNativeLib

/** Fake target class whose factory always throws. */
class ReflectionThrowingFactoryNativeLib {
    companion object {
        @JvmStatic
        fun getInstance(): Any = error("factory exploded")
    }
}

/** Fake target class with a parameterized constructor only. */
class ReflectionIntCtorNativeLib(n: Int) {
    val value: Int = n
}

/** Fake target class that cannot be instantiated through reflection. */
class ReflectionUnconstructableNativeLib(val session: ReflectionFakeSession)

/** Fake callback interface driving the dynamic proxy paths. */
interface ReflectionFakeCallback {
    fun onDone()

    fun onToken(text: String): Int

    fun isActive(): Boolean

    fun label(): String?

    fun total(): Long

    fun onToolCall(
        name: String,
        payload: String,
    )

    fun onError(message: String)

    fun onFinish()
}
