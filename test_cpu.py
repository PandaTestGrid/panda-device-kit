#!/usr/bin/env python3
"""
CPU 性能监控测试脚本

当前脚本覆盖的 CPU 相关采集命令及其「采集参数 / 返回参数」说明如下：

命令 200: 获取整体 CPU 使用率
  - 采集参数(发送到设备):
      * 仅发送一个 4 字节的大端无符号整型: 200
  - 返回参数(设备返回):
      * 4 字节 大端 float:
          - 含义: 整体 CPU 使用率百分比, 范围通常为 [0.0, 100.0]

命令 201: 获取每个 CPU 核心使用率
  - 采集参数(发送到设备):
      * 仅发送一个 4 字节的大端无符号整型: 201
  - 返回参数(设备返回):
      * 4 字节 大端 uint32:
          - 含义: core_count, CPU 核心数量
      * 后续紧跟 core_count 个 4 字节 大端 float:
          - 含义: 对应每个核心的使用率百分比
          - 序号从 0 到 core_count - 1, 例如:
              核心 0 使用率, 核心 1 使用率, ...

命令 202: 获取每个 CPU 核心当前频率
  - 采集参数(发送到设备):
      * 仅发送一个 4 字节的大端无符号整型: 202
  - 返回参数(设备返回):
      * 4 字节 大端 uint32:
          - 含义: core_count, CPU 核心数量
      * 后续紧跟 core_count 个 4 字节 大端 uint32:
          - 含义: 对应每个核心的当前频率, 单位为 kHz
          - 示例: 1800000 表示 1800000 kHz, 即 1800 MHz

命令 206: 获取 CPU 温度
  - 采集参数(发送到设备):
      * 仅发送一个 4 字节的大端无符号整型: 206
  - 返回参数(设备返回):
      * 4 字节 大端 float:
          - 含义: CPU 温度, 单位为摄氏度(°C)
          - 若返回值小于等于 0, 通常表示未获取到有效温度数据

命令 207: 获取指定线程的 CPU 使用率
  - 采集参数(发送到设备):
      * 4 字节 大端 uint32: 207 (命令 ID)
      * 4 字节 大端 uint32: pid, 目标进程 ID
      * 4 字节 大端 uint32: tid, 目标线程 ID
  - 返回参数(设备返回):
      * 4 字节 大端 float:
          - 含义: 该线程的 CPU 使用率百分比

测试命令: 200, 201, 202, 206, 207
"""

import socket
import struct
import sys
import time

# 优先使用 TCP 连接（通过 adb forward）
USE_TCP = True
TCP_HOST = 'localhost'
TCP_PORT = 9999
UNIX_SOCKET = '\0panda-1.1.0'

def connect():
    """连接到 Panda 服务"""
    if USE_TCP:
        # 使用 TCP 连接（通过 adb forward）
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        try:
            sock.connect((TCP_HOST, TCP_PORT))
            return sock
        except Exception as e:
            print(f"TCP 连接失败: {e}")
            print("提示: 请确保已运行 'adb forward tcp:9999 localabstract:panda-1.1.0'")
            sys.exit(1)
    else:
        # 使用 Unix socket（需要在设备上运行）
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            sock.connect(UNIX_SOCKET)
            return sock
        except Exception as e:
            print(f"Unix socket 连接失败: {e}")
            sys.exit(1)

def test_cpu_usage(sock):
    """测试命令 200: 获取整体 CPU 使用率"""
    print("\n=== 测试 CPU 使用率 (命令 200) ===")
    try:
        sock.sendall(struct.pack('>I', 200))
        usage = struct.unpack('>f', sock.recv(4))[0]
        print(f"CPU 使用率: {usage:.2f}%")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_cpu_core_usage(sock):
    """测试命令 201: 获取 CPU 核心使用率"""
    print("\n=== 测试 CPU 核心使用率 (命令 201) ===")
    try:
        sock.sendall(struct.pack('>I', 201))
        core_count = struct.unpack('>I', sock.recv(4))[0]
        print(f"CPU 核心数: {core_count}")
        
        if core_count > 0:
            usages = []
            for i in range(core_count):
                usage = struct.unpack('>f', sock.recv(4))[0]
                usages.append(usage)
                print(f"  核心 {i}: {usage:.2f}%")
            print(f"平均使用率: {sum(usages) / len(usages):.2f}%")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_cpu_freq(sock):
    """测试命令 202: 获取 CPU 频率"""
    print("\n=== 测试 CPU 频率 (命令 202) ===")
    try:
        sock.sendall(struct.pack('>I', 202))
        core_count = struct.unpack('>I', sock.recv(4))[0]
        print(f"CPU 核心数: {core_count}")
        
        if core_count > 0:
            frequencies = []
            for i in range(core_count):
                freq = struct.unpack('>I', sock.recv(4))[0]
                frequencies.append(freq)
                freq_mhz = freq / 1000.0
                print(f"  核心 {i}: {freq} kHz ({freq_mhz:.2f} MHz)")
            if frequencies:
                avg_freq = sum(frequencies) / len(frequencies) / 1000.0
                print(f"平均频率: {avg_freq:.2f} MHz")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_cpu_temperature(sock):
    """测试命令 206: 获取 CPU 温度"""
    print("\n=== 测试 CPU 温度 (命令 206) ===")
    try:
        sock.sendall(struct.pack('>I', 206))
        temp = struct.unpack('>f', sock.recv(4))[0]
        if temp > 0:
            print(f"CPU 温度: {temp:.2f}°C")
        else:
            print("CPU 温度: 未获取到数据")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_thread_cpu_usage(sock, pid, tid):
    """测试命令 207: 获取线程 CPU 使用率"""
    print(f"\n=== 测试线程 CPU 使用率 (命令 207) ===")
    print(f"PID: {pid}, TID: {tid}")
    try:
        sock.sendall(struct.pack('>I', 207))
        sock.sendall(struct.pack('>I', pid))
        sock.sendall(struct.pack('>I', tid))
        usage = struct.unpack('>f', sock.recv(4))[0]
        print(f"线程 CPU 使用率: {usage:.2f}%")
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("CPU 性能监控测试")
    print("=" * 50)
    
    sock = connect()
    
    results = []
    
    # 测试整体 CPU 使用率
    results.append(("CPU 使用率", test_cpu_usage(sock)))
    time.sleep(0.5)
    
    # 测试 CPU 核心使用率
    results.append(("CPU 核心使用率", test_cpu_core_usage(sock)))
    time.sleep(0.5)
    
    # 测试 CPU 频率
    results.append(("CPU 频率", test_cpu_freq(sock)))
    time.sleep(0.5)
    
    # 测试 CPU 温度
    results.append(("CPU 温度", test_cpu_temperature(sock)))
    time.sleep(0.5)
    
    # 测试线程 CPU 使用率（使用当前进程）
    import os
    pid = os.getpid()
    results.append(("线程 CPU 使用率", test_thread_cpu_usage(sock, pid, pid)))
    
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

