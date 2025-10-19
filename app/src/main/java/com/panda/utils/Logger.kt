package com.panda.utils

import java.text.SimpleDateFormat
import java.util.*

object Logger {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    
    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        println("[$timestamp] [INFO] $message")
    }
    
    fun error(message: String, throwable: Throwable? = null) {
        val timestamp = dateFormat.format(Date())
        println("[$timestamp] [ERROR] $message")
        throwable?.printStackTrace()
    }
    
    fun debug(message: String) {
        val timestamp = dateFormat.format(Date())
        println("[$timestamp] [DEBUG] $message")
    }
}

