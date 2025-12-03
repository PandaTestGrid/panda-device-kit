#!/usr/bin/env python3
"""
综合测试脚本 - 测试所有功能模块
"""

import subprocess
import sys
import time

def run_test(script_name, description):
    """运行测试脚本"""
    print(f"\n{'=' * 60}")
    print(f"运行测试: {description}")
    print(f"脚本: {script_name}")
    print('=' * 60)
    
    try:
        result = subprocess.run(
            [sys.executable, script_name],
            capture_output=True,
            text=True,
            timeout=60
        )
        
        print(result.stdout)
        if result.stderr:
            print("错误输出:", result.stderr)
        
        return result.returncode == 0
    except subprocess.TimeoutExpired:
        print(f"测试超时: {script_name}")
        return False
    except Exception as e:
        print(f"运行测试失败: {e}")
        return False

def main():
    print("=" * 60)
    print("Panda 设备工具包 - 综合测试")
    print("=" * 60)
    
    # 定义所有测试
    tests = [
        ("test_cpu.py", "CPU 性能监控"),
        ("test_gpu.py", "GPU 性能监控"),
        ("test_fps.py", "FPS 性能监控"),
        ("test_memory.py", "内存监控"),
        ("test_battery.py", "电池信息"),
        ("test_network_stats.py", "网络流量统计"),
        ("test_wifi.py", "WiFi 管理"),
    ]
    
    results = []
    
    for script, description in tests:
        success = run_test(script, description)
        results.append((description, success))
        time.sleep(1)  # 测试间隔
    
    # 打印汇总
    print("\n" + "=" * 60)
    print("测试结果汇总")
    print("=" * 60)
    
    passed = 0
    failed = 0
    
    for description, success in results:
        status = "✓ 通过" if success else "✗ 失败"
        print(f"{description}: {status}")
        if success:
            passed += 1
        else:
            failed += 1
    
    print("\n" + "=" * 60)
    print(f"总计: {len(results)} 个测试")
    print(f"通过: {passed}")
    print(f"失败: {failed}")
    print("=" * 60)
    
    sys.exit(0 if failed == 0 else 1)

if __name__ == '__main__':
    main()

