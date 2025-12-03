#!/usr/bin/env python3
"""
网络流量统计测试脚本
测试命令: 230, 231, 232
"""

import socket
import struct
import sys
import time

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
    for unit in ['B', 'KB', 'MB', 'GB', 'TB']:
        if bytes_val < 1024.0:
            return f"{bytes_val:.2f} {unit}"
        bytes_val /= 1024.0
    return f"{bytes_val:.2f} PB"

def test_network_usage_by_uid(sock, uid):
    """测试命令 230: 获取指定 UID 的网络流量"""
    print(f"\n=== 测试网络流量 (命令 230) ===")
    print(f"UID: {uid}")
    try:
        sock.sendall(struct.pack('>I', 230))
        sock.sendall(struct.pack('>I', uid))
        
        total_rx = struct.unpack('>Q', sock.recv(8))[0]
        total_tx = struct.unpack('>Q', sock.recv(8))[0]
        wifi_rx = struct.unpack('>Q', sock.recv(8))[0]
        wifi_tx = struct.unpack('>Q', sock.recv(8))[0]
        mobile_rx = struct.unpack('>Q', sock.recv(8))[0]
        mobile_tx = struct.unpack('>Q', sock.recv(8))[0]
        
        print(f"总接收: {format_bytes(total_rx)}")
        print(f"总发送: {format_bytes(total_tx)}")
        print(f"WiFi 接收: {format_bytes(wifi_rx)}")
        print(f"WiFi 发送: {format_bytes(wifi_tx)}")
        print(f"移动网络接收: {format_bytes(mobile_rx)}")
        print(f"移动网络发送: {format_bytes(mobile_tx)}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_total_network_usage(sock):
    """测试命令 231: 获取总网络流量"""
    print("\n=== 测试总网络流量 (命令 231) ===")
    try:
        sock.sendall(struct.pack('>I', 231))
        
        total_rx = struct.unpack('>Q', sock.recv(8))[0]
        total_tx = struct.unpack('>Q', sock.recv(8))[0]
        
        print(f"总接收: {format_bytes(total_rx)}")
        print(f"总发送: {format_bytes(total_tx)}")
        print(f"总流量: {format_bytes(total_rx + total_tx)}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_network_usage_by_package(sock, package_name):
    """测试命令 232: 获取指定包名的网络流量"""
    print(f"\n=== 测试包名网络流量 (命令 232) ===")
    print(f"包名: {package_name}")
    try:
        sock.sendall(struct.pack('>I', 232))
        # 发送包名
        package_bytes = package_name.encode('utf-8')
        sock.sendall(struct.pack('>I', len(package_bytes)))
        sock.sendall(package_bytes)
        
        uid = struct.unpack('>I', sock.recv(4))[0]
        rx = struct.unpack('>Q', sock.recv(8))[0]
        tx = struct.unpack('>Q', sock.recv(8))[0]
        
        print(f"UID: {uid}")
        print(f"接收: {format_bytes(rx)}")
        print(f"发送: {format_bytes(tx)}")
        print(f"总流量: {format_bytes(rx + tx)}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("网络流量统计测试")
    print("=" * 50)
    
    sock = connect()
    
    results = []
    
    # 测试总网络流量
    results.append(("总网络流量", test_total_network_usage(sock)))
    time.sleep(0.5)
    
    # 测试指定 UID 的网络流量（通常 Android 应用的 UID 从 10000 开始）
    # 测试 UID 1000 (系统)
    results.append(("UID 1000 网络流量", test_network_usage_by_uid(sock, 1000)))
    time.sleep(0.5)
    
    # 测试指定包名的网络流量
    # 使用常见的系统包名
    results.append(("com.android.settings 网络流量", 
                   test_network_usage_by_package(sock, "com.android.settings")))
    
    # 打印测试结果
    print("\n" + "=" * 50)
    print("测试结果汇总")
    print("=" * 50)
    for name, result in results:
        status = "✓ 通过" if result else "✗ 失败"
        print(f"{name}: {status}")
    
    sock.close()
    
    # 返回退出码
    all_passed = all(result for _, result in results)
    sys.exit(0 if all_passed else 1)

if __name__ == '__main__':
    main()

