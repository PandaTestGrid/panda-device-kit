#!/usr/bin/env python3
"""
内存监控测试脚本
测试命令: 205
"""

import socket
import struct
import sys
import os

USE_TCP = True
TCP_HOST = 'localhost'
TCP_PORT = 9999
UNIX_SOCKET = '\0panda-1.1.0'

def connect():
    """连接到 Panda 服务"""
    if USE_TCP:
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.connect((TCP_HOST, TCP_PORT))
            return sock
        except Exception as e:
            print(f"TCP 连接失败: {e}")
            print("提示: 请确保已运行 'adb forward tcp:9999 localabstract:panda-1.1.0'")
            sys.exit(1)
    else:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            sock.connect(UNIX_SOCKET)
            return sock
        except Exception as e:
            print(f"Unix socket 连接失败: {e}")
            sys.exit(1)

def format_bytes(bytes_val):
    """格式化字节数"""
    for unit in ['B', 'KB', 'MB', 'GB']:
        if bytes_val < 1024.0:
            return f"{bytes_val:.2f} {unit}"
        bytes_val /= 1024.0
    return f"{bytes_val:.2f} TB"

def test_memory_usage(sock, pid):
    """测试命令 205: 获取进程内存使用"""
    print(f"\n=== 测试进程内存使用 (命令 205) ===")
    print(f"PID: {pid}")
    try:
        sock.sendall(struct.pack('>I', 205))
        sock.sendall(struct.pack('>I', pid))
        
        pss = struct.unpack('>Q', sock.recv(8))[0]
        private_dirty = struct.unpack('>Q', sock.recv(8))[0]
        shared_dirty = struct.unpack('>Q', sock.recv(8))[0]
        
        print(f"PSS (Proportional Set Size): {format_bytes(pss * 1024)}")
        print(f"Private Dirty: {format_bytes(private_dirty * 1024)}")
        print(f"Shared Dirty: {format_bytes(shared_dirty * 1024)}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("内存监控测试")
    print("=" * 50)
    
    sock = connect()
    
    # 测试当前进程的内存使用
    pid = os.getpid()
    result = test_memory_usage(sock, pid)
    
    # 也可以测试系统进程（PID 1）
    print("\n--- 测试系统进程 (PID 1) ---")
    test_memory_usage(sock, 1)
    
    # 打印测试结果
    print("\n" + "=" * 50)
    print("测试结果汇总")
    print("=" * 50)
    status = "✓ 通过" if result else "✗ 失败"
    print(f"内存使用监控: {status}")
    
    sock.close()
    
    sys.exit(0 if result else 1)

if __name__ == '__main__':
    main()

