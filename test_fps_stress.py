#!/usr/bin/env python3
"""
Stress test for FPS reflection path.
Sends command 208 to start profiling, then pulls FPS (command 204)
the specified number of times, and prints basic statistics.
"""

import argparse
import socket
import statistics
import struct
import sys
import time

TCP_HOST = "localhost"
TCP_PORT = 9999
UNIX_SOCKET = "\0panda-1.1.0"


def parse_args():
    parser = argparse.ArgumentParser(description="FPS stress test")
    parser.add_argument(
        "--count",
        type=int,
        default=1000,
        help="number of FPS samples to collect (default: 1000)",
    )
    parser.add_argument(
        "--interval",
        type=int,
        default=1000,
        help="profiling interval for command 208 in ms (default: 1000)",
    )
    parser.add_argument(
        "--sleep",
        type=float,
        default=0.01,
        help="sleep duration between pulls in seconds (default: 0.01)",
    )
    parser.add_argument(
        "--unix",
        action="store_true",
        help="use abstract UNIX domain socket instead of TCP",
    )
    return parser.parse_args()


def connect(use_unix: bool):
    if use_unix:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        sock.connect(UNIX_SOCKET)
    else:
        sock = socket.create_connection((TCP_HOST, TCP_PORT))
    return sock


def send_command(sock, cmd, *payload):
    sock.sendall(struct.pack(">I", cmd))
    for value in payload:
        sock.sendall(struct.pack(">I", value))


def main():
    args = parse_args()
    sock = connect(args.unix)

    try:
        # start profiling
        send_command(sock, 208, args.interval)
        if struct.unpack(">I", sock.recv(4))[0] != 1:
            raise RuntimeError("Failed to start profiling (command 208)")

        fps_values = []
        for idx in range(args.count):
            send_command(sock, 204)
            fps = struct.unpack(">I", sock.recv(4))[0]
            print(f"FPS: {fps}")
            fps_values.append(fps)
            time.sleep(args.sleep)

        # stop profiling
        send_command(sock, 209)
        struct.unpack(">I", sock.recv(4))[0]
    finally:
        sock.close()

    if not fps_values:
        print("No FPS samples collected")
        sys.exit(1)

    print("=== FPS Stress Test ===")
    print(f"Samples        : {len(fps_values)}")
    print(f"Min / Max      : {min(fps_values)} / {max(fps_values)}")
    print(f"Mean / Median  : {statistics.mean(fps_values):.2f} / "
          f"{statistics.median(fps_values):.2f}")
    uniques = sorted(set(fps_values))
    print(f"Unique values  : {len(uniques)} -> {uniques[:10]}" +
          (" ..." if len(uniques) > 10 else ""))


if __name__ == "__main__":
    main()


