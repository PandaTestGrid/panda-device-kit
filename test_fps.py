# #!/usr/bin/env python3
# """
# FPS 性能监控测试脚本
# 测试命令: 204, 208, 209
# """

# import socket
# import struct
# import sys
# import time

# USE_TCP = True
# TCP_HOST = 'localhost'
# TCP_PORT = 9999
# UNIX_SOCKET = '\0panda-1.1.0'

# def connect():
#     """连接到 Panda 服务"""
#     if USE_TCP:
#         sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
#         try:
#             sock.connect((TCP_HOST, TCP_PORT))
#             return sock
#         except Exception as e:
#             print(f"TCP 连接失败: {e}")
#             print("提示: 请确保已运行 'adb forward tcp:9999 localabstract:panda-1.1.0'")
#             sys.exit(1)
#     else:
#         sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
#         try:
#             sock.connect(UNIX_SOCKET)
#             return sock
#         except Exception as e:
#             print(f"Unix socket 连接失败: {e}")
#             sys.exit(1)

# def test_fps(sock):
#     """测试命令 204: 获取 FPS"""
#     print("\n=== 测试 FPS (命令 204) ===")
#     try:
#         sock.sendall(struct.pack('>I', 204))
#         fps = struct.unpack('>I', sock.recv(4))[0]
#         print(f"当前 FPS: {fps}")
#         return True
#     except Exception as e:
#         print(f"错误: {e}")
#         return False

# def test_start_profiling(sock, interval=1000):
#     """测试命令 208: 开始性能分析"""
#     print(f"\n=== 测试开始性能分析 (命令 208) ===")
#     print(f"监控间隔: {interval}ms")
#     try:
#         sock.sendall(struct.pack('>I', 208))
#         sock.sendall(struct.pack('>I', interval))
#         result = struct.unpack('>I', sock.recv(4))[0]
#         if result == 1:
#             print("性能分析已启动")
#             return True
#         else:
#             print("性能分析启动失败")
#             return False
#     except Exception as e:
#         print(f"错误: {e}")
#         return False

# def test_stop_profiling(sock):
#     """测试命令 209: 停止性能分析"""
#     print("\n=== 测试停止性能分析 (命令 209) ===")
#     try:
#         sock.sendall(struct.pack('>I', 209))
#         result = struct.unpack('>I', sock.recv(4))[0]
#         if result == 1:
#             print("性能分析已停止")
#             return True
#         else:
#             print("性能分析停止失败")
#             return False
#     except Exception as e:
#         print(f"错误: {e}")
#         return False

# def main():
#     print("=" * 50)
#     print("FPS 性能监控测试")
#     print("=" * 50)
    
#     sock = connect()
    
#     results = []
    
#     # 测试获取 FPS（未启动监控时）
#     results.append(("获取 FPS (未启动)", test_fps(sock)))
#     time.sleep(0.5)
    
#     # 测试启动性能分析
#     results.append(("启动性能分析", test_start_profiling(sock, 1000)))
#     time.sleep(2)  # 等待监控启动
    
#     # 测试获取 FPS（启动监控后）
#     print("\n--- 连续获取 FPS 5 次 ---")
#     fps_values = []
#     for i in range(5):
#         sock.sendall(struct.pack('>I', 204))
#         fps = struct.unpack('>I', sock.recv(4))[0]
#         fps_values.append(fps)
#         print(f"  第 {i+1} 次: {fps} FPS")
#         time.sleep(0.5)
    
#     if fps_values:
#         avg_fps = sum(fps_values) / len(fps_values)
#         print(f"平均 FPS: {avg_fps:.2f}")
#         results.append(("获取 FPS (启动后)", True))
#     else:
#         results.append(("获取 FPS (启动后)", False))
    
#     # 测试停止性能分析
#     results.append(("停止性能分析", test_stop_profiling(sock)))
    
#     # 打印测试结果
#     print("\n" + "=" * 50)
#     print("测试结果汇总")
#     print("=" * 50)
#     for name, result in results:
#         status = "✓ 通过" if result else "✗ 失败"
#         print(f"{name}: {status}")
    
#     sock.close()
    
#     # 返回退出码
#     all_passed = all(result for _, result in results)
#     sys.exit(0 if all_passed else 1)

# if __name__ == '__main__':
#     main()


import socket, struct, statistics, time
HOST, PORT = 'localhost', 9999
sock = socket.create_connection((HOST, PORT))

def send_cmd(cmd, *payload):
    sock.sendall(struct.pack('>I', cmd))
    for p in payload:
        sock.sendall(struct.pack('>I', p))

# start profiling to ensure continuous updates
send_cmd(208, 1000)
ack = struct.unpack('>I', sock.recv(4))[0]
if ack != 1:
    raise SystemExit('start profiling failed')

fps_values = []
for i in range(1000):
    send_cmd(204)
    fps = struct.unpack('>I', sock.recv(4))[0]
    fps_values.append(fps)
    time.sleep(0.01)

send_cmd(209)
struct.unpack('>I', sock.recv(4))[0]

sock.close()
print(f"Samples: {len(fps_values)}")
print(f"Min: {min(fps_values)}  Max: {max(fps_values)}")
print(f"Mean: {statistics.mean(fps_values):.2f}  Median: {statistics.median(fps_values):.2f}")
print(f"Unique values: {sorted(set(fps_values))[:10]}" + ("..." if len(set(fps_values))>10 else ""))