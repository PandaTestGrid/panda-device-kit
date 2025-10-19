package com.panda

import android.net.LocalServerSocket
import android.net.LocalSocket
import android.system.Os
import com.panda.core.CommandDispatcher
import com.panda.utils.Logger
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Panda - Android 系统服务工具
 * 提供应用管理、WiFi控制、剪贴板、通知、自动点击等功能
 * @version 1.1.0 (算法优化版)
 */
object Main {
    private const val VERSION = "1.1.0"
    private const val SOCKET_NAME = "panda-$VERSION"
    
    @JvmStatic
    fun main(args: Array<String>) {
        if (args.isEmpty()) {
            // 主进程 - fork 子进程
            forkChildProcess()
        } else {
            // 子进程 - 运行服务
            runService(args[0] == "debug")
        }
    }
    
    private fun forkChildProcess() {
        Logger.log("Main process start $VERSION")
        val classPath = System.getProperty("java.class.path") ?: ""
        Logger.log("[fork] class path: $classPath")
        
        // Fork 子进程
        val processBuilder = ProcessBuilder(
            "/system/bin/app_process",
            "/",
            Main::class.java.canonicalName,
            "fork"
        )
        processBuilder.environment()["CLASSPATH"] = classPath.split(File.pathSeparator)[0]
        
        val process = processBuilder.start()
        Logger.log("[fork] start child process ${getPid(process)}")
        
        // 读取子进程输出
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        Thread {
            try {
                reader.forEachLine { line ->
                    println(line)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
        
        Logger.log("Main process exit")
    }
    
    private fun runService(debug: Boolean) {
        if (!debug) {
            Logger.log("Child process start")
            Os.setsid()  // 从父进程分离
            Logger.log("Detach from parent")
        }
        
        // 设置未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.error("Uncaught exception in $thread", throwable)
        }
        
        try {
            val serverSocket = LocalServerSocket(SOCKET_NAME)
            Logger.log("Started. Version: $VERSION")
            Logger.log("Current uid: ${Os.getuid()}")
            Logger.log("Listening on socket: $SOCKET_NAME")
            
            var connectionCount = 0
            
            // 主循环 - 接受客户端连接
            while (true) {
                val client = serverSocket.accept()
                connectionCount++
                
                Logger.log("Client connected (#$connectionCount)")
                
                // 配置 socket
                client.sendBufferSize = 524288  // 512KB
                
                // 创建输出流
                val output = BufferedOutputStream(client.outputStream, 524288)
                
                // 在新线程中处理客户端请求
                Thread {
                    try {
                        handleClient(client, output, connectionCount)
                    } catch (e: Exception) {
                        Logger.error("Error handling client #$connectionCount", e)
                    } finally {
                        try {
                            client.close()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        Logger.log("Client disconnected (#$connectionCount)")
                    }
                }.start()
            }
        } catch (e: java.io.IOException) {
            if (e.message?.contains("Address already in use") == true) {
                Logger.error("Service already running on socket: $SOCKET_NAME")
            } else {
                throw e
            }
        }
    }
    
    private fun handleClient(client: LocalSocket, output: BufferedOutputStream, clientId: Int) {
        val input = client.inputStream
        val dispatcher = CommandDispatcher(client, output)
        
        // 持续处理命令
        while (!Thread.interrupted()) {
            try {
                // 读取命令码 (4 bytes, big-endian)
                val commandBytes = ByteArray(4)
                val bytesRead = input.read(commandBytes)
                if (bytesRead != 4) {
                    break  // 连接关闭
                }
                
                val command = ((commandBytes[0].toInt() and 0xFF) shl 24) or
                              ((commandBytes[1].toInt() and 0xFF) shl 16) or
                              ((commandBytes[2].toInt() and 0xFF) shl 8) or
                              (commandBytes[3].toInt() and 0xFF)
                
                Logger.log("Client #$clientId - Command: $command")
                
                // 分发命令
                dispatcher.dispatch(command)
                
                // 刷新输出
                output.flush()
                
            } catch (e: Exception) {
                if (e is java.io.EOFException || e.message?.contains("Connection reset") == true) {
                    Logger.log("Client #$clientId disconnected")
                    break
                }
                Logger.error("Error processing command for client #$clientId", e)
                break
            }
        }
    }
    
    private fun getPid(process: Process): Int {
        return try {
            val field = process.javaClass.getDeclaredField("pid")
            field.isAccessible = true
            field.getInt(process)
        } catch (e: Exception) {
            -1
        }
    }
}

