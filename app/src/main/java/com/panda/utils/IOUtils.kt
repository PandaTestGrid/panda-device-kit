package com.panda.utils

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * IO 工具类
 * 处理客户端通信的数据序列化和反序列化
 */
object IOUtils {
    
    /**
     * 写入 32 位整数 (Big Endian)
     */
    fun writeInt(output: OutputStream, value: Int) {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 设置为 Big Endian
        buffer.putInt(value)
        buffer.flip()  // 准备读取
        val bytes = ByteArray(4)
        buffer.get(bytes)
        output.write(bytes)
    }
    
    /**
     * 读取 32 位整数 (Big Endian)
     */
    fun readInt(input: InputStream): Int {
        val bytes = ByteArray(4)
        var offset = 0
        while (offset < 4) {
            val read = input.read(bytes, offset, 4 - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
        return ((bytes[0].toInt() and 0xFF) shl 24) or
               ((bytes[1].toInt() and 0xFF) shl 16) or
               ((bytes[2].toInt() and 0xFF) shl 8) or
               (bytes[3].toInt() and 0xFF)
    }
    
    /**
     * 写入 64 位长整数 (Big Endian)
     */
    fun writeLong(output: OutputStream, value: Long) {
        val buffer = ByteBuffer.allocate(8)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 设置为 Big Endian
        buffer.putLong(value)
        buffer.flip()  // 准备读取
        val bytes = ByteArray(8)
        buffer.get(bytes)
        output.write(bytes)
    }
    
    /**
     * 读取 64 位长整数 (Big Endian)
     */
    fun readLong(input: InputStream): Long {
        val bytes = ByteArray(8)
        var offset = 0
        while (offset < 8) {
            val read = input.read(bytes, offset, 8 - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 设置为 Big Endian
        return buffer.long
    }
    
    /**
     * 写入字符串 (长度 + UTF-8 数据)
     */
    fun writeString(output: OutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(output, bytes.size)
        output.write(bytes)
    }
    
    /**
     * 读取字符串
     */
    fun readString(input: InputStream): String {
        val length = readInt(input)
        if (length == 0) return ""
        
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
        return String(bytes, Charsets.UTF_8)
    }
    
    /**
     * 写入字节数组 (长度 + 数据)
     */
    fun writeBytes(output: OutputStream, data: ByteArray) {
        writeInt(output, data.size)
        output.write(data)
    }
    
    /**
     * 读取字节数组
     */
    fun readBytes(input: InputStream): ByteArray {
        val length = readInt(input)
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = input.read(bytes, offset, length - offset)
            if (read == -1) throw java.io.EOFException()
            offset += read
        }
        return bytes
    }
    
    /**
     * 写入布尔值
     */
    fun writeBoolean(output: OutputStream, value: Boolean) {
        writeInt(output, if (value) 1 else 0)
    }
    
    /**
     * 读取布尔值
     */
    fun readBoolean(input: InputStream): Boolean {
        return readInt(input) != 0
    }
    
    /**
     * 写入错误信息
     */
    fun writeError(output: OutputStream, errorCode: Int, message: String) {
        writeInt(output, errorCode)
        writeString(output, message)
    }
    
    /**
     * 写入成功状态
     */
    fun writeSuccess(output: OutputStream) {
        writeInt(output, 0)
    }
    
    /**
     * 写入 32 位浮点数 (Big Endian)
     */
    fun writeFloat(output: OutputStream, value: Float) {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 设置为 Big Endian
        buffer.putFloat(value)
        buffer.flip()  // 准备读取
        val bytes = ByteArray(4)
        buffer.get(bytes)
        output.write(bytes)
    }
    
    /**
     * 读取 32 位浮点数 (Big Endian)
     */
    fun readFloat(input: InputStream): Float {
        val bytes = ByteArray(4)
        var offset = 0
        while (offset < 4) {
            val read = input.read(bytes, offset, 4 - offset)
            if (read == -1) throw java.io.EOFException("Unexpected end of stream")
            offset += read
        }
        val buffer = ByteBuffer.wrap(bytes)
        buffer.order(ByteOrder.BIG_ENDIAN)  // 设置为 Big Endian
        return buffer.float
    }
    
    /**
     * 获取异常信息字符串
     */
    fun getStackTrace(throwable: Throwable): String {
        return throwable.stackTraceToString()
    }
}

