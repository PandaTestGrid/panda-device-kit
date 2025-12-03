#!/usr/bin/env python3
"""
电池信息测试脚本
测试命令: 220, 221, 222
"""

import socket
import struct
import sys
import time
from datetime import datetime

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

def test_battery_info(sock):
    """测试命令 220: 获取电池信息"""
    print("\n=== 测试电池信息 (命令 220) ===")
    try:
        sock.sendall(struct.pack('>I', 220))
        
        current = struct.unpack('>i', sock.recv(4))[0]  # 有符号整数
        voltage = struct.unpack('>I', sock.recv(4))[0]
        level = struct.unpack('>I', sock.recv(4))[0]
        charging = struct.unpack('>I', sock.recv(4))[0]
        timestamp = struct.unpack('>Q', sock.recv(8))[0]
        
        print(f"电流: {current} mA")
        print(f"电压: {voltage} mV ({voltage/1000:.2f} V)")
        print(f"电量: {level}%")
        print(f"充电状态: {'充电中' if charging == 1 else '未充电'}")
        if timestamp > 0:
            dt = datetime.fromtimestamp(timestamp / 1000)
            print(f"时间戳: {dt.strftime('%Y-%m-%d %H:%M:%S')}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_battery_level(sock):
    """测试命令 221: 获取电池电量"""
    print("\n=== 测试电池电量 (命令 221) ===")
    try:
        sock.sendall(struct.pack('>I', 221))
        level = struct.unpack('>I', sock.recv(4))[0]
        print(f"电池电量: {level}%")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_battery_support(sock):
    """测试命令 222: 检查电池监控支持"""
    print("\n=== 测试电池监控支持 (命令 222) ===")
    try:
        sock.sendall(struct.pack('>I', 222))
        supported = struct.unpack('>I', sock.recv(4))[0]
        status = "支持" if supported == 1 else "不支持"
        print(f"电池监控支持: {status}")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("电池信息测试")
    print("=" * 50)
    
    sock = connect()
    
    results = []
    
    # 测试电池监控支持
    results.append(("电池监控支持", test_battery_support(sock)))
    time.sleep(0.5)
    
    # 测试电池电量
    results.append(("电池电量", test_battery_level(sock)))
    time.sleep(0.5)
    
    # 测试完整电池信息
    results.append(("电池信息", test_battery_info(sock)))
    
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

