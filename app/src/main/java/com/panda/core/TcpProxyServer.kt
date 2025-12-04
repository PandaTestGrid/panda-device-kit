package com.panda.core

import android.net.LocalSocket
import com.panda.utils.Logger
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP 反向代理服务器
 * 监听 TCP 端口，将请求转发到 LocalSocket
 * 实现反向代理功能，支持远程访问
 */
object TcpProxyServer {
    private const val DEFAULT_TCP_PORT = 43305
    private const val BUFFER_SIZE = 524288  // 512KB
    private const val SOCKET_NAME = "panda-1.1.0"
    
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var tcpPort = DEFAULT_TCP_PORT
    
    /**
     * 启动 TCP 代理服务器
     * @param port TCP 监听端口，默认 43305
     */
    fun start(port: Int = DEFAULT_TCP_PORT) {
        if (isRunning) {
            Logger.log("TCP proxy server already running on port $tcpPort")
            return
        }
        
        tcpPort = port
        
        Thread {
            try {
                serverSocket = ServerSocket(port, 128)
                isRunning = true
                Logger.log("TCP proxy server started on port $port")
                Logger.log("Forwarding TCP connections to LocalSocket: $SOCKET_NAME")
                
                var connectionCount = 0
                
                while (isRunning) {
                    try {
                        val tcpClient = serverSocket?.accept()
                        if (tcpClient != null) {
                            connectionCount++
                            Logger.log("TCP client connected (#$connectionCount) from ${tcpClient.remoteSocketAddress}")
                            
                            // 在新线程中处理 TCP 客户端
                            Thread {
                                handleTcpClient(tcpClient, connectionCount)
                            }.start()
                        }
                    } catch (e: Exception) {
                        if (isRunning) {
                            Logger.error("Error accepting TCP client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("Error starting TCP proxy server", e)
                isRunning = false
            }
        }.start()
    }
    
    /**
     * 停止 TCP 代理服务器
     */
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
            serverSocket = null
            Logger.log("TCP proxy server stopped")
        } catch (e: Exception) {
            Logger.error("Error stopping TCP proxy server", e)
        }
    }
    
    /**
     * 检查服务器是否运行中
     */
    fun isRunning(): Boolean = isRunning
    
    /**
     * 获取当前监听端口
     */
    fun getPort(): Int = tcpPort
    
    /**
     * 处理 TCP 客户端连接
     * 将 TCP 数据转发到 LocalSocket，将 LocalSocket 响应转发回 TCP
     */
    private fun handleTcpClient(tcpClient: Socket, clientId: Int) {
        var localSocket: LocalSocket? = null
        
        try {
            // 连接到 LocalSocket
            localSocket = LocalSocket()
            localSocket.connect(android.net.LocalSocketAddress(SOCKET_NAME))
            localSocket.sendBufferSize = BUFFER_SIZE
            localSocket.receiveBufferSize = BUFFER_SIZE
            
            Logger.log("TCP client #$clientId: Connected to LocalSocket")
            
            // 配置 TCP socket
            tcpClient.sendBufferSize = BUFFER_SIZE
            tcpClient.receiveBufferSize = BUFFER_SIZE
            tcpClient.tcpNoDelay = true  // 禁用 Nagle 算法，降低延迟
            
            val tcpInput = BufferedInputStream(tcpClient.getInputStream(), BUFFER_SIZE)
            val tcpOutput = BufferedOutputStream(tcpClient.getOutputStream(), BUFFER_SIZE)
            val localInput = BufferedInputStream(localSocket.inputStream, BUFFER_SIZE)
            val localOutput = BufferedOutputStream(localSocket.outputStream, BUFFER_SIZE)
            
            // 启动双向转发
            val tcpToLocal = Thread {
                forwardStream(tcpInput, localOutput, "TCP->Local", clientId)
            }
            val localToTcp = Thread {
                forwardStream(localInput, tcpOutput, "Local->TCP", clientId)
            }
            
            tcpToLocal.start()
            localToTcp.start()
            
            // 等待任一方向完成
            tcpToLocal.join()
            localToTcp.join()
            
            Logger.log("TCP client #$clientId: Connection closed")
            
        } catch (e: Exception) {
            Logger.error("Error handling TCP client #$clientId", e)
        } finally {
            try {
                tcpClient.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                localSocket?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    /**
     * 转发数据流
     */
    private fun forwardStream(
        input: InputStream,
        output: OutputStream,
        direction: String,
        clientId: Int
    ) {
        val buffer = ByteArray(8192)  // 8KB buffer
        
        try {
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) {
                    break  // EOF
                }
                if (bytesRead > 0) {
                    output.write(buffer, 0, bytesRead)
                    output.flush()
                }
            }
        } catch (e: Exception) {
            if (e.message?.contains("Connection reset") == true ||
                e.message?.contains("Broken pipe") == true ||
                e is java.io.EOFException) {
                // 正常关闭，不记录错误
            } else {
                Logger.error("Error forwarding $direction for client #$clientId", e)
            }
        } finally {
            try {
                // 关闭输出流以通知另一端
                output.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}


