#!/usr/bin/env python3
"""
TCP 反向代理测试脚本
测试通过 TCP 连接访问 Panda 服务
"""

import socket
import struct
import sys

TCP_HOST = 'localhost'  # 或设备 IP 地址
TCP_PORT = 43305  # 默认 TCP 端口

def connect_tcp():
    """通过 TCP 连接到 Panda 服务"""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((TCP_HOST, TCP_PORT))
        print(f"✅ TCP 连接成功: {TCP_HOST}:{TCP_PORT}")
        return sock
    except Exception as e:
        print(f"❌ TCP 连接失败: {e}")
        print(f"提示: 请确保 Panda 服务已启动并启用了 TCP 反向代理")
        print(f"      默认端口: {TCP_PORT}")
        sys.exit(1)

def test_simple_command(sock):
    """测试简单命令: 获取 WiFi 状态 (命令 50)"""
    print("\n=== 测试命令 50: 获取 WiFi 状态 ===")
    try:
        # 发送命令
        sock.sendall(struct.pack('>I', 50))
        
        # 接收响应 (4 bytes int)
        response = sock.recv(4)
        if len(response) == 4:
            state = struct.unpack('>I', response)[0]
            state_str = {0: "禁用", 1: "启用", 2: "启用中", 3: "禁用中"}.get(state, f"未知({state})")
            print(f"✅ WiFi 状态: {state_str}")
            return True
        else:
            print(f"❌ 响应数据不完整: {len(response)} bytes")
            return False
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_cpu_usage(sock):
    """测试命令: 获取 CPU 使用率 (命令 200)"""
    print("\n=== 测试命令 200: 获取 CPU 使用率 ===")
    try:
        sock.sendall(struct.pack('>I', 200))
        response = sock.recv(4)
        if len(response) == 4:
            usage = struct.unpack('>f', response)[0]
            print(f"✅ CPU 使用率: {usage:.2f}%")
            return True
        else:
            print(f"❌ 响应数据不完整: {len(response)} bytes")
            return False
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def test_battery_level(sock):
    """测试命令: 获取电池电量 (命令 221)"""
    print("\n=== 测试命令 221: 获取电池电量 ===")
    try:
        sock.sendall(struct.pack('>I', 221))
        response = sock.recv(4)
        if len(response) == 4:
            level = struct.unpack('>I', response)[0]
            print(f"✅ 电池电量: {level}%")
            return True
        else:
            print(f"❌ 响应数据不完整: {len(response)} bytes")
            return False
    except Exception as e:
        print(f"❌ 测试失败: {e}")
        return False

def main():
    print("=" * 50)
    print("TCP 反向代理功能测试")
    print("=" * 50)
    print(f"目标: {TCP_HOST}:{TCP_PORT}")
    print()
    
    sock = connect_tcp()
    
    results = []
    
    # 运行测试
    results.append(("WiFi 状态", test_simple_command(sock)))
    results.append(("CPU 使用率", test_cpu_usage(sock)))
    results.append(("电池电量", test_battery_level(sock)))
    
    # 打印结果汇总
    print("\n" + "=" * 50)
    print("测试结果汇总")
    print("=" * 50)
    for name, result in results:
        status = "✅ 通过" if result else "❌ 失败"
        print(f"{name}: {status}")
    
    sock.close()
    
    # 返回退出码
    all_passed = all(result for _, result in results)
    sys.exit(0 if all_passed else 1)

if __name__ == '__main__':
    # 支持命令行参数指定主机和端口
    if len(sys.argv) > 1:
        TCP_HOST = sys.argv[1]
    if len(sys.argv) > 2:
        TCP_PORT = int(sys.argv[2])
    
    main()


