#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
启动 Panda 自动点击监控
"""

import socket
import json
import sys

HOST = "127.0.0.1"
PORT = 9999


def send_request(action, params=None):
    """发送请求到 Panda 服务"""
    try:
        client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client.settimeout(5)
        client.connect((HOST, PORT))

        request = {"action": action, "params": params or {}}

        request_str = json.dumps(request) + "\n"
        client.sendall(request_str.encode("utf-8"))

        response = b""
        while True:
            chunk = client.recv(4096)
            if not chunk:
                break
            response += chunk
            if b"\n" in chunk:
                break

        client.close()

        if response:
            # 尝试解码响应
            try:
                # 去除末尾的换行符和空白
                response_str = response.strip().decode("utf-8")
                return json.loads(response_str)
            except UnicodeDecodeError:
                # 如果UTF-8解码失败，尝试其他编码或显示原始数据
                print(f"⚠️  响应解码失败，原始数据: {response[:100]}")
                # 尝试 latin-1 编码（它能解码任何字节）
                try:
                    response_str = response.strip().decode("latin-1")
                    return json.loads(response_str)
                except:
                    return None
            except json.JSONDecodeError as e:
                print(f"⚠️  JSON解析失败: {e}")
                print(f"   响应内容: {response[:200]}")
                return None
        return None

    except ConnectionRefusedError:
        print("❌ 无法连接到 Panda 服务")
        print("   请确保服务已启动并设置了端口转发")
        return None
    except socket.timeout:
        print("⚠️  请求超时")
        return None
    except Exception as e:
        print(f"❌ 错误: {e}")
        import traceback

        traceback.print_exc()
        return None


def start_autoclick():
    """启动自动点击监控"""
    print("🤖 正在启动自动点击监控...")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

    # 获取配置参数
    print("\n请输入监控配置（直接回车使用默认值）：")

    try:
        interval_input = input("  点击间隔（秒，默认5）: ").strip()
        interval = int(interval_input) if interval_input else 5

        max_clicks_input = input("  最大点击次数（0=无限，默认0）: ").strip()
        max_clicks = int(max_clicks_input) if max_clicks_input else 0

        target_app = input("  目标应用包名（留空=所有应用）: ").strip()

    except KeyboardInterrupt:
        print("\n\n⚠️  操作已取消")
        sys.exit(0)
    except ValueError:
        print("❌ 输入格式错误，使用默认值")
        interval = 5
        max_clicks = 0
        target_app = ""

    # 构建参数
    params = {"interval": interval, "max_clicks": max_clicks}

    if target_app:
        params["target_app"] = target_app

    # 发送启动请求
    response = send_request("start_autoclick", params)

    if response:
        if response.get("success"):
            print("\n✅ 自动点击监控已启动")
            print(f"   间隔: {interval} 秒")
            print(f"   最大点击: {'无限' if max_clicks == 0 else max_clicks}")
            if target_app:
                print(f"   目标应用: {target_app}")
            print("\n💡 使用 'make check-monitor' 查看状态")
            print("💡 使用 'make stop-monitor' 停止监控")
        else:
            error_msg = response.get("error", "未知错误")
            print(f"\n❌ 启动失败: {error_msg}")
    else:
        print("\n❌ 无法与 Panda 服务通信")
        print("   请检查:")
        print("   1. 服务是否运行: make status")
        print("   2. 端口转发是否设置: make forward")

    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")


if __name__ == "__main__":
    try:
        start_autoclick()
    except KeyboardInterrupt:
        print("\n\n⚠️  操作已取消")
        sys.exit(0)
