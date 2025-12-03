#!/usr/bin/env python3
"""
WiFi 管理测试脚本
测试命令: 50, 52, 53, 54
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

def test_wifi_state(sock):
    """测试命令 50: 获取 WiFi 状态"""
    print("\n=== 测试 WiFi 状态 (命令 50) ===")
    try:
        sock.sendall(struct.pack('>I', 50))
        state = struct.unpack('>I', sock.recv(4))[0]
        
        state_names = {
            0: "WIFI_STATE_DISABLING",
            1: "WIFI_STATE_DISABLED",
            2: "WIFI_STATE_ENABLING",
            3: "WIFI_STATE_ENABLED",
            4: "WIFI_STATE_UNKNOWN"
        }
        state_name = state_names.get(state, f"未知状态 ({state})")
        print(f"WiFi 状态: {state_name}")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_scan_wifi(sock):
    """测试命令 52: 扫描 WiFi 网络"""
    print("\n=== 测试扫描 WiFi 网络 (命令 52) ===")
    print("正在扫描，请稍候...")
    try:
        sock.sendall(struct.pack('>I', 52))
        
        # 等待扫描结果
        count = struct.unpack('>I', sock.recv(4))[0]
        print(f"发现 {count} 个 WiFi 网络:")
        
        networks = []
        for i in range(count):
            # 读取 SSID
            ssid_len = struct.unpack('>I', sock.recv(4))[0]
            ssid = sock.recv(ssid_len).decode('utf-8')
            
            # 读取 BSSID
            bssid_len = struct.unpack('>I', sock.recv(4))[0]
            bssid = sock.recv(bssid_len).decode('utf-8')
            
            # 读取频率
            frequency = struct.unpack('>I', sock.recv(4))[0]
            
            # 读取标准
            standard = struct.unpack('>I', sock.recv(4))[0]
            
            # 读取信号等级
            level = struct.unpack('>I', sock.recv(4))[0]
            
            networks.append({
                'ssid': ssid,
                'bssid': bssid,
                'frequency': frequency,
                'standard': standard,
                'level': level
            })
            
            standard_name = {
                0: "未知",
                1: "802.11a",
                2: "802.11b",
                3: "802.11g",
                4: "802.11n",
                5: "802.11ac",
                6: "802.11ax"
            }.get(standard, f"未知({standard})")
            
            print(f"  [{i+1}] {ssid}")
            print(f"      BSSID: {bssid}")
            print(f"      频率: {frequency} MHz")
            print(f"      标准: {standard_name}")
            print(f"      信号: {'█' * level}{'░' * (4 - level)} ({level}/4)")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_wifi_info(sock):
    """测试命令 53: 获取当前连接的 WiFi 信息"""
    print("\n=== 测试当前 WiFi 信息 (命令 53) ===")
    try:
        sock.sendall(struct.pack('>I', 53))
        
        # 读取 SSID
        ssid_len = struct.unpack('>I', sock.recv(4))[0]
        ssid = sock.recv(ssid_len).decode('utf-8')
        
        # 读取 BSSID
        bssid_len = struct.unpack('>I', sock.recv(4))[0]
        bssid = sock.recv(bssid_len).decode('utf-8')
        
        # 读取 networkId
        network_id = struct.unpack('>I', sock.recv(4))[0]
        
        # 读取 linkSpeed
        link_speed = struct.unpack('>I', sock.recv(4))[0]
        
        # 读取 rssi
        rssi = struct.unpack('>i', sock.recv(4))[0]
        
        print(f"SSID: {ssid}")
        print(f"BSSID: {bssid}")
        print(f"Network ID: {network_id}")
        print(f"连接速度: {link_speed} Mbps")
        print(f"信号强度: {rssi} dBm")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def test_configured_networks(sock):
    """测试命令 54: 获取已配置的网络列表"""
    print("\n=== 测试已配置网络列表 (命令 54) ===")
    try:
        sock.sendall(struct.pack('>I', 54))
        
        count = struct.unpack('>I', sock.recv(4))[0]
        print(f"已配置 {count} 个网络:")
        
        for i in range(count):
            network_id = struct.unpack('>I', sock.recv(4))[0]
            
            ssid_len = struct.unpack('>I', sock.recv(4))[0]
            ssid = sock.recv(ssid_len).decode('utf-8')
            
            print(f"  [{i+1}] {ssid} (ID: {network_id})")
        
        return True
    except Exception as e:
        print(f"错误: {e}")
        return False

def main():
    print("=" * 50)
    print("WiFi 管理测试")
    print("=" * 50)
    
    sock = connect()
    
    results = []
    
    # 测试 WiFi 状态
    results.append(("WiFi 状态", test_wifi_state(sock)))
    time.sleep(0.5)
    
    # 测试扫描 WiFi
    results.append(("扫描 WiFi", test_scan_wifi(sock)))
    time.sleep(1)
    
    # 测试当前 WiFi 信息
    results.append(("当前 WiFi 信息", test_wifi_info(sock)))
    time.sleep(0.5)
    
    # 测试已配置网络
    results.append(("已配置网络", test_configured_networks(sock)))
    
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

