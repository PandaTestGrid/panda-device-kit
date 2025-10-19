#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
停止 Panda 自动点击监控
"""

import socket
import json
import sys

HOST = '127.0.0.1'
PORT = 9999

def send_request(action, params=None):
    """发送请求到 Panda 服务"""
    try:
        client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        client.settimeout(5)
        client.connect((HOST, PORT))
        
        request = {
            "action": action,
            "params": params or {}
        }
        
        request_str = json.dumps(request) + "\n"
        client.sendall(request_str.encode('utf-8'))
        
        response = b""
        while True:
            chunk = client.recv(4096)
            if not chunk:
                break
            response += chunk
            if b'\n' in chunk:
                break
        
        client.close()
        
        if response:
            return json.loads(response.decode('utf-8'))
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
        return None

def stop_autoclick():
    """停止自动点击监控"""
    print("🛑 正在停止自动点击监控...")
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    
    response = send_request("stop_autoclick")
    
    if response:
        if response.get("success"):
            data = response.get("data", {})
            
            print("\n✅ 监控已停止")
            
            total_clicks = data.get("total_clicks", 0)
            if total_clicks > 0:
                print(f"   总点击次数: {total_clicks}")
            
            duration = data.get("duration")
            if duration:
                print(f"   运行时长: {duration}")
            
            print("\n💡 使用 'make monitor' 重新启动监控")
        else:
            error_msg = response.get("error", "未知错误")
            if "not running" in error_msg.lower() or "未运行" in error_msg:
                print("\n⚠️  监控未在运行")
            else:
                print(f"\n❌ 停止失败: {error_msg}")
    else:
        print("\n❌ 无法与 Panda 服务通信")
        print("   请检查:")
        print("   1. 服务是否运行: make status")
        print("   2. 端口转发是否设置: make forward")
    
    print("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

if __name__ == "__main__":
    try:
        stop_autoclick()
    except KeyboardInterrupt:
        print("\n\n⚠️  操作已取消")
        sys.exit(0)

