#!/usr/bin/env python3
"""
GPU 性能监控测试脚本
测试命令: 203
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

def test_gpu_usage(sock):
    """测试命令 203: 获取 GPU 使用率和频率"""
    print("\n=== 测试 GPU 使用率和频率 (命令 203) ===")
    try:
        sock.sendall(struct.pack('>I', 203))
        usage = struct.unpack('>f', sock.recv(4))[0]
        freq = struct.unpack('>I', sock.recv(4))[0]
        
        print(f"GPU 使用率: {usage:.2f}%")
        if freq > 0:
            freq_mhz = freq / 1000.0
            print(f"GPU 频率: {freq} kHz ({freq_mhz:.2f} MHz)")
        else:
            print("GPU 频率: 未获取到数据")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("GPU 性能监控测试")
    print("=" * 50)
    
    sock = connect()
    
    # 测试 GPU 使用率和频率
    result = test_gpu_usage(sock)
    
    # 打印测试结果
    print("\n" + "=" * 50)
    print("测试结果汇总")
    print("=" * 50)
    status = "✓ 通过" if result else "✗ 失败"
    print(f"GPU 使用率和频率: {status}")
    
    sock.close()
    
    sys.exit(0 if result else 1)

if __name__ == '__main__':
    main()

