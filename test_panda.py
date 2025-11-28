#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Panda 功能测试套件
测试所有 Panda Android 系统服务工具的功能
"""

import socket
import struct
import sys
import argparse
import time
import re
from pathlib import Path
from typing import Optional, List, Dict, Any


class PandaClient:
    """Panda 客户端，用于与 Android 设备上的 Panda 服务通信"""
    
    def __init__(
        self,
        socket_name: str = '\0panda-1.1.0',
        tcp_host: Optional[str] = None,
        tcp_port: Optional[int] = None,
    ):
        """
        初始化客户端
        
        Args:
            socket_name: LocalSocket 名称
            tcp_host: 当使用 TCP 连接时的主机
            tcp_port: 当使用 TCP 连接时的端口
        """
        self.socket_name = socket_name
        self.tcp_host = tcp_host
        self.tcp_port = tcp_port
        self.sock: Optional[socket.socket] = None
    
    def connect(self) -> bool:
        """连接到 Panda 服务"""
        try:
            if self.tcp_host is not None and self.tcp_port is not None:
                self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                self.sock.connect((self.tcp_host, self.tcp_port))
            else:
                self.sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                self.sock.connect(self.socket_name)
            return True
        except Exception as e:
            print(f"❌ 连接失败: {e}")
            return False
    
    def disconnect(self):
        """断开连接"""
        if self.sock:
            try:
                self.sock.close()
            except:
                pass
            self.sock = None
    
    def send_command(self, command: int) -> bool:
        """发送命令码"""
        try:
            self.sock.sendall(struct.pack('>I', command))
            return True
        except Exception as e:
            print(f"❌ 发送命令失败: {e}")
            return False
    
    def read_int(self, signed: bool = False) -> int:
        """读取 32 位整数（大端序）"""
        data = self.sock.recv(4)
        if len(data) != 4:
            raise IOError("读取整数失败")
        fmt = '>i' if signed else '>I'
        return struct.unpack(fmt, data)[0]
    
    def read_long(self) -> int:
        """读取 64 位长整数（大端序）"""
        data = self.sock.recv(8)
        if len(data) != 8:
            raise IOError("读取长整数失败")
        return struct.unpack('>Q', data)[0]
    
    def read_string(self) -> str:
        """读取字符串（长度 + UTF-8 数据）"""
        length = self.read_int(signed=True)
        if length < 0:
            error_message = self.read_string()
            raise RuntimeError(f"服务端返回错误码 {length}: {error_message}")
        if length == 0:
            return ""
        return self.read_exact(length).decode('utf-8')
    
    def read_bytes(self) -> bytes:
        """读取字节数组（长度 + 数据）"""
        length = self.read_int()
        if length == 0:
            return b''
        return self.read_exact(length)
    
    def read_exact(self, length: int) -> bytes:
        """读取指定长度的字节"""
        if length < 0:
            raise ValueError("length must be non-negative")
        data = b''
        while len(data) < length:
            chunk = self.sock.recv(length - len(data))
            if not chunk:
                raise IOError("读取数据失败")
            data += chunk
        return data
    
    def read_png_stream(self) -> bytes:
        """按 PNG 结构读取数据，直到 IEND chunk"""
        signature = self.read_exact(8)
        if signature != b'\x89PNG\r\n\x1a\n':
            raise ValueError("PNG 签名不正确")
        png_data = bytearray(signature)
        while True:
            length_bytes = self.read_exact(4)
            length = struct.unpack('>I', length_bytes)[0]
            chunk_type = self.read_exact(4)
            chunk_data = self.read_exact(length)
            crc = self.read_exact(4)
            png_data.extend(length_bytes)
            png_data.extend(chunk_type)
            png_data.extend(chunk_data)
            png_data.extend(crc)
            if chunk_type == b'IEND':
                break
        return bytes(png_data)
    
    def write_int(self, value: int):
        """写入 32 位整数"""
        self.sock.sendall(struct.pack('>I', value & 0xFFFFFFFF))
    
    def write_string(self, value: str):
        """写入字符串"""
        data = value.encode('utf-8')
        self.write_int(len(data))
        self.sock.sendall(data)
    
    def write_bytes(self, data: bytes):
        """写入字节数组"""
        self.write_int(len(data))
        self.sock.sendall(data)
    
    def __enter__(self):
        if not self.connect():
            raise ConnectionError("无法连接到 Panda 服务")
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.disconnect()


class PandaTester:
    """Panda 功能测试器"""
    
    def __init__(self, client: PandaClient, icon_output_dir: Optional[Path] = None):
        self.client = client
        self.test_results: Dict[str, bool] = {}
        self.icon_output_dir = icon_output_dir
        if self.icon_output_dir:
            self.icon_output_dir.mkdir(parents=True, exist_ok=True)

    def _save_icon(self, package_name: str, icon_data: bytes, index: int):
        """将单个应用图标写入磁盘"""
        if not self.icon_output_dir or not icon_data:
            return
        safe_name = re.sub(r'[^A-Za-z0-9._-]+', '_', package_name).strip('_')
        if not safe_name:
            safe_name = f"app_{index:04d}"
        filename = f"{index:04d}_{safe_name}.png"
        output_path = self.icon_output_dir / filename
        with open(output_path, 'wb') as icon_file:
            icon_file.write(icon_data)
    
    def test_basic(self):
        """测试基础功能 (命令 0-1)"""
        print("\n" + "="*60)
        print("📋 测试基础功能")
        print("="*60)
        
        # 命令 0: 创建虚拟显示器
        print("\n[测试] 命令 0: 创建虚拟显示器")
        try:
            self.client.send_command(0)
            result = self.client.read_int()
            if result == 0:
                print("✅ 虚拟显示器创建成功")
                self.test_results["基础-虚拟显示器"] = True
            else:
                print(f"⚠️  返回码: {result}")
                self.test_results["基础-虚拟显示器"] = False
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["基础-虚拟显示器"] = False
        
        # 命令 1: 初始化
        print("\n[测试] 命令 1: 系统初始化")
        try:
            self.client.send_command(1)
            print("✅ 初始化命令已发送（无返回值）")
            self.test_results["基础-初始化"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["基础-初始化"] = False
    
    def test_apps(self):
        """测试应用管理功能 (命令 10-14, 21)"""
        print("\n" + "="*60)
        print("📱 测试应用管理功能")
        print("="*60)
        
        # 命令 10: 获取应用列表
        print("\n[测试] 命令 10: 获取应用列表")
        try:
            self.client.send_command(10)
            # 发送标志位和图标大小
            self.client.write_int(3)  # 标志位：包含系统和第三方应用
            self.client.write_int(64)  # 图标大小：64x64
            
            # 读取默认图标
            icon_size = self.client.read_int()
            if icon_size > 0:
                icon_data = self.client.read_exact(icon_size)
                print(f"✅ 默认图标: {icon_size} 字节")
            
            # 读取应用数量
            app_count = self.client.read_int()
            print(f"✅ 应用总数: {app_count}")
            
            sample_count = min(3, app_count)
            saved_icons = 0
            for i in range(app_count):
                package_name = self.client.read_string()
                version_name = self.client.read_string()
                version_code = self.client.read_long()
                app_name = self.client.read_string()
                app_size = self.client.read_long()
                # 读取图标
                icon_size = self.client.read_int()
                icon_data = self.client.read_exact(icon_size) if icon_size > 0 else None
                
                if i < sample_count:
                    print(f"  [{i+1}] {app_name} ({package_name})")
                    print(f"      版本: {version_name} ({version_code})")
                    print(f"      大小: {app_size / 1024 / 1024:.2f} MB")
                elif i == sample_count:
                    remaining = app_count - sample_count
                    if remaining > 0:
                        print(f"  ... 还有 {remaining} 个应用（已读取并保存）")
                
                if icon_data and self.icon_output_dir:
                    self._save_icon(package_name or app_name or f"app_{i:04d}", icon_data, i)
                    saved_icons += 1
            
            if self.icon_output_dir:
                print(f"💾 已保存 {saved_icons} 个图标到 {self.icon_output_dir}")
            
            self.test_results["应用-应用列表"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            import traceback
            traceback.print_exc()
            self.test_results["应用-应用列表"] = False
        
        # 命令 11: 获取 APK 路径（需要包名）
        print("\n[测试] 命令 11: 获取 APK 路径")
        try:
            self.client.send_command(11)
            # 使用系统包名测试
            self.client.write_string("com.android.settings")
            apk_path = self.client.read_string()
            apk_size = self.client.read_long()
            split_count = self.client.read_int()
            print(f"✅ APK 路径: {apk_path}")
            print(f"✅ APK 大小: {apk_size / 1024 / 1024:.2f} MB")
            print(f"✅ 分包数量: {split_count}")
            self.test_results["应用-APK路径"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["应用-APK路径"] = False
        
        # 命令 12: 获取相机状态
        print("\n[测试] 命令 12: 获取相机状态")
        try:
            self.client.send_command(12)
            self.client.write_int(0)  # 相机 ID
            status = self.client.read_int()
            print(f"✅ 相机状态: {status}")
            self.test_results["应用-相机状态"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["应用-相机状态"] = False
        
        # 命令 13: 获取相机列表
        print("\n[测试] 命令 13: 获取相机列表")
        try:
            self.client.send_command(13)
            camera_count = self.client.read_int()
            print(f"✅ 相机数量: {camera_count}")
            for i in range(camera_count):
                camera_id = self.client.read_string()
                lens_facing = self.client.read_int()
                sensor_width = self.client.read_int()
                sensor_height = self.client.read_int()
                print(f"  相机 {i+1}: {camera_id}, 方向={lens_facing}, 分辨率={sensor_width}x{sensor_height}")
            self.test_results["应用-相机列表"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["应用-相机列表"] = False
        
        # 命令 14: 启动应用（测试启动设置应用）
        print("\n[测试] 命令 14: 启动应用")
        try:
            self.client.send_command(14)
            self.client.write_string("com.android.settings")
            self.client.write_int(0)  # 显示器 ID
            result = self.client.read_int()
            if result == 0:
                print("✅ 应用启动成功")
            else:
                error_msg = self.client.read_string()
                print(f"⚠️  启动失败: {error_msg}")
            self.test_results["应用-启动应用"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["应用-启动应用"] = False
        
        # 命令 21: 获取相机服务信息
        print("\n[测试] 命令 21: 获取相机服务信息")
        try:
            self.client.send_command(21)
            print("✅ 相机服务信息命令已发送（无返回值）")
            self.test_results["应用-相机服务信息"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["应用-相机服务信息"] = False
    
    def test_storage(self):
        """测试存储功能 (命令 20)"""
        print("\n" + "="*60)
        print("💾 测试存储功能")
        print("="*60)
        
        # 命令 20: 获取存储设备列表
        print("\n[测试] 命令 20: 获取存储设备列表")
        try:
            self.client.send_command(20)
            raw_count = self.client.read_int()
            if raw_count & 0x80000000:
                error_message = self.client.read_string()
                print(f"⚠️  存储接口返回错误: {error_message}")
                self.test_results["存储-设备列表"] = False
                return
            volume_count = raw_count
            print(f"✅ 存储设备数量: {volume_count}")
            for i in range(volume_count):
                volume_type = self.client.read_int()
                label = self.client.read_string()
                path = self.client.read_string()
                type_name = ["内部存储", "SD卡", "USB设备"][volume_type] if volume_type < 3 else "未知"
                print(f"  设备 {i+1}: {label} ({type_name}) - {path}")
            self.test_results["存储-设备列表"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["存储-设备列表"] = False
    
    def test_wifi(self):
        """测试 WiFi 功能 (命令 50-58)"""
        print("\n" + "="*60)
        print("🌐 测试 WiFi 功能")
        print("="*60)
        
        # 命令 50: 获取 WiFi 状态
        print("\n[测试] 命令 50: 获取 WiFi 状态")
        try:
            self.client.send_command(50)
            state = self.client.read_int()
            state_names = {1: "禁用", 2: "启用中", 3: "已启用", 4: "禁用中"}
            print(f"✅ WiFi 状态: {state} ({state_names.get(state, '未知')})")
            self.test_results["WiFi-状态"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["WiFi-状态"] = False
        
        # 命令 52: 扫描 WiFi
        print("\n[测试] 命令 52: 扫描 WiFi 网络")
        try:
            self.client.send_command(52)
            # 等待扫描完成
            time.sleep(3)
            network_count = self.client.read_int()
            print(f"✅ 扫描到 {network_count} 个网络")
            for i in range(min(5, network_count)):  # 只显示前 5 个
                ssid = self.client.read_string()
                bssid = self.client.read_string()
                frequency = self.client.read_int()
                standard = self.client.read_int()
                level = self.client.read_int()
                print(f"  网络 {i+1}: {ssid} ({bssid})")
                print(f"    频率: {frequency} MHz, 信号: {level}/4")
            if network_count > 5:
                # 跳过剩余网络
                for i in range(5, network_count):
                    self.client.read_string()  # ssid
                    self.client.read_string()  # bssid
                    self.client.read_int()  # frequency
                    self.client.read_int()  # standard
                    self.client.read_int()  # level
            self.test_results["WiFi-扫描"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["WiFi-扫描"] = False
        
        # 命令 53: 获取当前 WiFi 信息
        print("\n[测试] 命令 53: 获取当前 WiFi 信息")
        try:
            self.client.send_command(53)
            ssid = self.client.read_string()
            bssid = self.client.read_string()
            network_id = self.client.read_int()
            link_speed = self.client.read_int()
            rssi = self.client.read_int()
            print(f"✅ 当前网络: {ssid}")
            print(f"  BSSID: {bssid}, 网络ID: {network_id}")
            print(f"  连接速度: {link_speed} Mbps, 信号强度: {rssi} dBm")
            self.test_results["WiFi-当前信息"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["WiFi-当前信息"] = False
        
        # 命令 54: 获取已配置的网络
        print("\n[测试] 命令 54: 获取已配置的网络")
        try:
            self.client.send_command(54)
            network_count = self.client.read_int()
            print(f"✅ 已配置网络数量: {network_count}")
            for i in range(network_count):
                net_id = self.client.read_int()
                net_ssid = self.client.read_string()
                print(f"  网络 {i+1}: {net_ssid} (ID: {net_id})")
            self.test_results["WiFi-已配置网络"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["WiFi-已配置网络"] = False
    
        
    
    def test_notifications(self):
        """测试通知功能 (命令 80-83)"""
        print("\n" + "="*60)
        print("🔔 测试通知功能")
        print("="*60)
        
        # 命令 80: 获取通知列表
        print("\n[测试] 命令 80: 获取通知列表")
        try:
            self.client.send_command(80)
            notification_count = self.client.read_int()
            print(f"✅ 活动通知数量: {notification_count}")
            for i in range(notification_count):
                key = self.client.read_string()
                package_name = self.client.read_string()
                title = self.client.read_string()
                text = self.client.read_string()
                timestamp = self.client.read_long()
                is_clearable = self.client.read_int() != 0
                action_count = self.client.read_int()
                print(f"  通知 {i+1}: {title}")
                print(f"    包名: {package_name}")
                print(f"    内容: {text[:50]}...")
                print(f"    动作数: {action_count}")
                # 跳过动作信息
                for j in range(action_count):
                    self.client.read_string()  # action title
                    self.client.read_int()  # has_input
            # 发送确认（通知服务会等待）
            if notification_count > 0:
                self.client.sock.sendall(b'\x00')
            self.test_results["通知-获取列表"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["通知-获取列表"] = False
    
    def test_system(self):
        """测试系统功能 (命令 60-65, 90, 100)"""
        print("\n" + "="*60)
        print("⚙️  测试系统功能")
        print("="*60)
        
        # 命令 60: 获取系统属性
        print("\n[测试] 命令 60: 获取系统属性")
        try:
            self.client.send_command(60)
            # 读取属性数量（假设返回格式）
            print("✅ 系统属性命令已发送")
            self.test_results["系统-系统属性"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["系统-系统属性"] = False
        
        # # 命令 90: 截图（壁纸）
        # print("\n[测试] 命令 90: 截图（壁纸）")
        # try:
        #     self.client.send_command(90)
        #     # 读取 PNG 数据（服务端直接输出 PNG 流）
        #     image_data = self.client.read_png_stream()
        #     print(f"✅ 截图成功: {len(image_data)} 字节")
        #     # 可选：保存图片
        #     with open('wallpaper.png', 'wb') as f:
        #         f.write(image_data)
        #     self.test_results["系统-截图"] = True
        # except Exception as e:
        #     print(f"❌ 测试失败: {e}")
        #     self.test_results["系统-截图"] = False
        
        # 命令 100: 执行 Shell 命令
        print("\n[测试] 命令 100: 执行 Shell 命令")
        try:
            self.client.send_command(100)
            # 发送命令
            self.client.write_string("echo 'Panda Test'")
            # 读取输出（格式可能因实现而异）
            print("✅ Shell 命令已发送")
            self.test_results["系统-Shell命令"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["系统-Shell命令"] = False
    
    def test_autoclick(self):
        """测试自动点击功能 (命令 110-119)"""
        print("\n" + "="*60)
        print("🖱️  测试自动点击功能")
        print("="*60)
        
        # 命令 113: 获取可点击文本
        print("\n[测试] 命令 113: 获取可点击文本")
        try:
            self.client.send_command(113)
            text_count = self.client.read_int()
            print(f"✅ 可点击文本数量: {text_count}")
            for i in range(min(5, text_count)):
                text = self.client.read_string()
                x = self.client.read_int()
                y = self.client.read_int()
                print(f"  文本 {i+1}: '{text}' 位置: ({x}, {y})")
            if text_count > 5:
                for i in range(5, text_count):
                    self.client.read_string()
                    self.client.read_int()
                    self.client.read_int()
            self.test_results["自动点击-可点击文本"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["自动点击-可点击文本"] = False
        
        # 命令 116: 获取监控状态
        print("\n[测试] 命令 116: 获取监控状态")
        try:
            self.client.send_command(116)
            is_monitoring = self.client.read_int() != 0
            print(f"✅ 监控状态: {'运行中' if is_monitoring else '已停止'}")
            self.test_results["自动点击-监控状态"] = True
        except Exception as e:
            print(f"❌ 测试失败: {e}")
            self.test_results["自动点击-监控状态"] = False
    
    def print_summary(self):
        """打印测试总结"""
        print("\n" + "="*60)
        print("📊 测试总结")
        print("="*60)
        
        total = len(self.test_results)
        passed = sum(1 for v in self.test_results.values() if v)
        failed = total - passed
        
        print(f"\n总计: {total} 项测试")
        print(f"✅ 通过: {passed} 项")
        print(f"❌ 失败: {failed} 项")
        print(f"📈 通过率: {passed/total*100:.1f}%")
        
        if failed > 0:
            print("\n失败的测试:")
            for test_name, result in self.test_results.items():
                if not result:
                    print(f"  ❌ {test_name}")


def main():
    parser = argparse.ArgumentParser(description='Panda 功能测试套件')
    parser.add_argument('--tests', nargs='+', 
                       choices=['basic', 'apps', 'storage', 'wifi', 
                               'notifications', 'system', 'autoclick', 'all'],
                       default=['all'],
                       help='要运行的测试套件')
    parser.add_argument('--socket', default='\0panda-1.1.0',
                       help='LocalSocket 名称（默认: \\0panda-1.1.0）')
    parser.add_argument('--tcp-host',
                       help='通过 TCP 连接时的主机名（例如 127.0.0.1）')
    parser.add_argument('--tcp-port', type=int,
                       help='通过 TCP 连接时的端口号（需先 adb forward）')
    parser.add_argument('--save-app-icons',
                       metavar='DIR',
                       help='将所有应用图标保存到指定目录')
    
    args = parser.parse_args()
    
    # 如果指定了 all，运行所有测试
    if 'all' in args.tests:
        test_suites = ['basic', 'apps', 'storage', 'wifi', 'clipboard', 
                      'notifications', 'system', 'autoclick']
    else:
        test_suites = args.tests
    
    if (args.tcp_host is None) != (args.tcp_port is None):
        parser.error('--tcp-host 和 --tcp-port 需要同时提供')
    
    if args.tcp_host and args.tcp_port:
        connection_desc = f"TCP {args.tcp_host}:{args.tcp_port}"
    else:
        connection_desc = repr(args.socket)
    
    print("🐼 Panda 功能测试套件")
    print("="*60)
    print(f"连接方式: {connection_desc}")
    print(f"测试套件: {', '.join(test_suites)}")
    
    try:
        with PandaClient(
            socket_name=args.socket,
            tcp_host=args.tcp_host,
            tcp_port=args.tcp_port,
        ) as client:
            icon_output_dir = Path(args.save_app_icons).expanduser() if args.save_app_icons else None
            tester = PandaTester(client, icon_output_dir=icon_output_dir)
            
            if 'basic' in test_suites:
                tester.test_basic()
            if 'apps' in test_suites:
                tester.test_apps()
            if 'storage' in test_suites:
                tester.test_storage()
            if 'wifi' in test_suites:
                tester.test_wifi()
            if 'notifications' in test_suites:
                tester.test_notifications()
            if 'system' in test_suites:
                tester.test_system()
            # if 'autoclick' in test_suites:
            #     tester.test_autoclick()
            
            tester.print_summary()
            
    except KeyboardInterrupt:
        print("\n\n⚠️  测试被用户中断")
        sys.exit(1)
    except Exception as e:
        print(f"\n\n❌ 测试过程中发生错误: {e}")
        import traceback
        traceback.print_exc()
        sys.exit(1)


if __name__ == '__main__':
    main()

