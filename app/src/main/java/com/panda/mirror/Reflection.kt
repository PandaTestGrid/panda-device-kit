package com.panda.mirror

import java.lang.reflect.Constructor
import java.lang.reflect.Method

/**
 * 反射工具类
 */

class RefMethod<T>(cls: Class<*>, methodName: String, vararg parameterTypes: Class<*>) {
    private val method: Method = cls.getDeclaredMethod(methodName, *parameterTypes).apply {
        isAccessible = true
    }

    @Suppress("UNCHECKED_CAST")
    fun call(receiver: Any?, vararg args: Any?): T {
        return try {
            method.invoke(receiver, *args) as T
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke method $method", e)
        }
    }
}

class RefStaticMethod<T>(cls: Class<*>, methodName: String, vararg parameterTypes: Class<*>) {
    private val method: Method = cls.getDeclaredMethod(methodName, *parameterTypes).apply {
        isAccessible = true
    }

    @Suppress("UNCHECKED_CAST")
    fun call(vararg args: Any?): T {
        return try {
            method.invoke(null, *args) as T
        } catch (e: Exception) {
            throw RuntimeException("Failed to invoke static method $method", e)
        }
    }
}

class RefConstructor<T>(cls: Class<*>, vararg parameterTypes: Class<*>) {
    private val constructor: Constructor<*> = cls.getDeclaredConstructor(*parameterTypes).apply {
        isAccessible = true
    }

    @Suppress("UNCHECKED_CAST")
    fun newInstance(vararg args: Any?): T {
        return try {
            constructor.newInstance(*args) as T
        } catch (e: Exception) {
            throw RuntimeException("Failed to create instance", e)
        }
    }
}

