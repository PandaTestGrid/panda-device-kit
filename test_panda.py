#!/usr/bin/env python3
"""
Simple smoke tester for the Panda 1.1.0 service.

Typical workflow:
    1. adb forward tcp:9999 localabstract:panda-1.1.0
    2. python3 test_panda.py --host 127.0.0.1 --port 9999 run

You can point the client directly at the Android abstract socket by passing
--unix @panda-1.1.0 when running the script inside an adb shell.
"""

from __future__ import annotations

import argparse
import contextlib
import hashlib
import socket
import struct
import sys
from dataclasses import dataclass
from datetime import datetime
from typing import Callable, Dict, Iterable, List, Optional, Tuple


class PandaProtocolError(RuntimeError):
    """Raised when the service returns an error sentinel."""

    def __init__(self, code: int, message: str) -> None:
        super().__init__(f"Panda error {code}: {message}")
        self.code = code
        self.message = message


@dataclass
class WifiNetwork:
    ssid: str
    bssid: str
    frequency: int
    standard: int
    signal_level: int


@dataclass
class WifiConnection:
    ssid: str
    bssid: str
    network_id: int
    link_speed: int
    rssi: int


@dataclass
class StorageVolume:
    kind: str
    label: str
    path: str


@dataclass
class AppRecord:
    package: str
    label: str
    version_name: str
    version_code: int
    can_launch: bool
    icon_png: bytes


@dataclass
class NotificationAction:
    title: str
    requires_input: bool


@dataclass
class NotificationEntry:
    key: str
    package: str
    title: str
    text: str
    posted_at_ms: int
    clearable: bool
    actions: List[NotificationAction]


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


class PandaClient:
    """Binary protocol helper for Panda service commands."""

    WIFI_STATE_LABELS = {
        0: "DISABLING",
        1: "DISABLED",
        2: "ENABLING",
        3: "ENABLED",
        4: "UNKNOWN",
    }

    VOLUME_TYPES = {
        0: "internal",
        1: "sdcard",
        2: "usb",
    }

    def __init__(
        self,
        *,
        host: Optional[str] = None,
        port: Optional[int] = None,
        unix_socket: Optional[str] = None,
        timeout: float = 10.0,
    ) -> None:
        if unix_socket:
            self._socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            path = unix_socket
            if path.startswith("@"):
                path = "\0" + path[1:]
            self._socket.connect(path)
        else:
            if not host or not port:
                raise ValueError("host and port must be provided when not using --unix")
            self._socket = socket.create_connection((host, port), timeout=timeout)
        self._socket.settimeout(timeout)
        self._reader = self._socket.makefile("rb")

    # ------------------------- public API helpers ------------------------- #
    def get_wifi_state(self) -> Tuple[int, str]:
        self._send_command(50)
        state = self._read_int()
        return state, self.WIFI_STATE_LABELS.get(state, f"UNKNOWN({state})")

    def get_wifi_connection(self) -> WifiConnection:
        self._send_command(53)
        return WifiConnection(
            ssid=self._read_string(),
            bssid=self._read_string(),
            network_id=self._read_int(),
            link_speed=self._read_int(),
            rssi=self._read_int(),
        )

    def scan_wifi(self, limit: Optional[int] = None) -> List[WifiNetwork]:
        self._send_command(52)
        count = self._read_int()
        networks: List[WifiNetwork] = []
        for _ in range(count):
            network = WifiNetwork(
                ssid=self._read_string(),
                bssid=self._read_string(),
                frequency=self._read_int(),
                standard=self._read_int(),
                signal_level=self._read_int(),
            )
            networks.append(network)
            if limit and len(networks) >= limit:
                break
        return networks

    def get_configured_networks(self) -> List[Tuple[int, str]]:
        self._send_command(54)
        total = self._read_int()
        return [(self._read_int(), self._read_string()) for _ in range(total)]

    def get_clipboard(self) -> Optional[Tuple[str, bytes]]:
        self._send_command(70)
        length_or_flag = self._read_int()
        if length_or_flag == -1:
            return None
        if length_or_flag < -1:
            message = self._read_string()
            raise PandaProtocolError(length_or_flag, message)
        mime = self._reader.read(length_or_flag).decode("utf-8") if length_or_flag else ""
        data = self._read_bytes()
        return mime, data

    def set_clipboard(self, mime_type: str, data: bytes) -> None:
        self._send_command(71)
        self._write_string(mime_type)
        self._write_bytes(data)
        status = self._read_int()
        if status != 0:
            raise PandaProtocolError(status, self._read_string())

    def list_storage_volumes(self) -> List[StorageVolume]:
        self._send_command(20)
        volumes = []
        count = self._read_int()
        for _ in range(count):
            type_id = self._read_int()
            volumes.append(
                StorageVolume(
                    kind=self.VOLUME_TYPES.get(type_id, f"unknown({type_id})"),
                    label=self._read_string(),
                    path=self._read_string(),
                )
            )
        return volumes

    def list_notifications(self) -> List[NotificationEntry]:
        self._send_command(80)
        total = self._read_int()
        notifications: List[NotificationEntry] = []
        for _ in range(total):
            key = self._read_string()
            package = self._read_string()
            title = self._read_string()
            text = self._read_string()
            posted_at_ms = self._read_long()
            clearable = self._read_bool()
            action_count = self._read_int()
            actions = [
                NotificationAction(
                    title=self._read_string(),
                    requires_input=self._read_bool(),
                )
                for _ in range(action_count)
            ]
            notifications.append(
                NotificationEntry(
                    key=key,
                    package=package,
                    title=title,
                    text=text,
                    posted_at_ms=posted_at_ms,
                    clearable=clearable,
                    actions=actions,
                )
            )
        # server waits for an extra byte before unregistering listener
        self._socket.sendall(b"\x00")
        return notifications

    def open_notification(self, key: str) -> None:
        self._send_command(82)
        self._write_string(key)
        self._write_int(0)

    def trigger_notification_action(self, key: str, action_index: int, input_text: str = "") -> None:
        self._send_command(82)
        self._write_string(key)
        self._write_int(1)
        self._write_int(action_index)
        self._write_string(input_text)

    def cancel_notification(self, key: str) -> None:
        self._send_command(81)
        self._write_string(key)

    def clear_all_notifications(self) -> None:
        self._send_command(83)

    # --------------------------- socket helpers --------------------------- #
    def close(self) -> None:
        with contextlib.suppress(Exception):
            self._reader.close()
        with contextlib.suppress(Exception):
            self._socket.close()

    def __enter__(self) -> "PandaClient":
        return self

    def __exit__(self, exc_type, exc, tb) -> None:
        self.close()

    def _send_command(self, code: int) -> None:
        self._socket.sendall(struct.pack(">I", code))

    def _write_string(self, value: str) -> None:
        encoded = value.encode("utf-8")
        self._socket.sendall(struct.pack(">I", len(encoded)))
        if encoded:
            self._socket.sendall(encoded)

    def _write_int(self, value: int) -> None:
        self._socket.sendall(struct.pack(">I", value))

    def _write_bytes(self, payload: bytes) -> None:
        self._socket.sendall(struct.pack(">I", len(payload)))
        if payload:
            self._socket.sendall(payload)

    def _read_exact(self, size: int) -> bytes:
        data = b""
        while len(data) < size:
            chunk = self._reader.read(size - len(data))
            if not chunk:
                raise EOFError("Socket closed while reading data")
            data += chunk
        return data

    def _read_int(self) -> int:
        return struct.unpack(">I", self._read_exact(4))[0]

    def _read_string(self) -> str:
        length = self._read_int()
        if length == 0:
            return ""
        return self._read_exact(length).decode("utf-8")

    def _read_bytes(self) -> bytes:
        length = self._read_int()
        if length == 0:
            return b""
        return self._read_exact(length)

    def _read_long(self) -> int:
        return struct.unpack(">Q", self._read_exact(8))[0]

    def _read_bool(self) -> bool:
        return self._read_int() != 0

    def get_app_list(
        self,
        *,
        include_system: bool = True,
        include_third_party: bool = True,
        launchable_only: bool = True,
        icon_size: int = 128,
        sample_limit: Optional[int] = None,
    ) -> Tuple[bytes, List[AppRecord], int]:
        flags = 0
        if include_system:
            flags |= 1
        if include_third_party:
            flags |= 2
        if not launchable_only:
            flags |= 4

        self._send_command(10)
        self._write_int(flags)
        self._write_int(icon_size)

        default_icon = self._read_bytes()
        total = self._read_int()
        records: List[AppRecord] = []

        for _ in range(total):
            package = self._read_string()
            version_name = self._read_string()
            version_code = self._read_long()
            label = self._read_string()
            _install_ts = self._read_int()
            _update_ts = self._read_int()
            _last_used_ts = self._read_int()
            _installer = self._read_string()
            _cpu_arch = self._read_string()
            _target_sdk = self._read_int()
            _min_sdk = self._read_int()
            _app_flags = self._read_int()
            _has_splits = self._read_bool()
            can_launch = self._read_bool()
            _apk_size = self._read_long()
            _data_size = self._read_long()
            _cache_size = self._read_long()
            icon_png = self._read_bytes()

            if sample_limit is None or len(records) < sample_limit:
                records.append(
                    AppRecord(
                        package=package,
                        label=label,
                        version_name=version_name,
                        version_code=version_code,
                        can_launch=can_launch,
                        icon_png=icon_png,
                    )
                )

        return default_icon, records, total


def run_wifi_suite(client: PandaClient, scan_limit: int) -> None:
    state_code, state_label = client.get_wifi_state()
    print(f"[wifi] state: {state_label} ({state_code})")

    try:
        connection = client.get_wifi_connection()
        print(
            f"[wifi] connected to {connection.ssid} ({connection.bssid}) "
            f"id={connection.network_id} speed={connection.link_speed}Mbps rssi={connection.rssi}dBm"
        )
    except PandaProtocolError as err:
        print(f"[wifi] failed to read connection: {err}")

    networks = client.scan_wifi(limit=scan_limit)
    if networks:
        print(f"[wifi] visible networks (showing {len(networks)}):")
        for net in networks:
            print(
                f"    - {net.ssid or '<hidden>'} ({net.bssid}) "
                f"{net.frequency}MHz std={net.standard} level={net.signal_level}"
            )
    else:
        print("[wifi] no networks reported (requires location + Wi-Fi permissions)")

    configurations = client.get_configured_networks()
    print(f"[wifi] configured networks: {len(configurations)} entries")
    for net_id, ssid in configurations[:10]:
        print(f"    - #{net_id}: {ssid}")


def run_clipboard_suite(client: PandaClient, text: str) -> None:
    snapshot = client.get_clipboard()
    if snapshot:
        mime, data = snapshot
        preview = data[:64].decode("utf-8", errors="replace")
        print(f"[clipboard] current ({mime}): {preview}")
    else:
        print("[clipboard] clipboard currently empty")

    print("[clipboard] pushing sample text...")
    client.set_clipboard("text/plain", text.encode("utf-8"))
    updated = client.get_clipboard()
    if updated:
        mime, data = updated
        print(f"[clipboard] new contents ({mime}): {data.decode('utf-8', errors='replace')}")
    else:
        print("[clipboard] failed to read data back")


def run_storage_suite(client: PandaClient) -> None:
    volumes = client.list_storage_volumes()
    if not volumes:
        print("[storage] no mounted volumes reported (API < 24 or permissions missing)")
        return
    print(f"[storage] mounted volumes: {len(volumes)}")
    for volume in volumes:
        print(f"    - {volume.kind}: {volume.label} -> {volume.path}")


def run_app_suite(client: PandaClient, icon_size: int = 128, sample_limit: int = 5) -> None:
    default_icon, apps, total = client.get_app_list(
        launchable_only=True, icon_size=icon_size, sample_limit=sample_limit
    )

    def describe_icon(prefix: str, payload: bytes) -> None:
        if not payload:
            print(f"{prefix}icon missing (bytes=0)")
            return
        digest = hashlib.sha256(payload).hexdigest()[:16]
        if payload.startswith(PNG_SIGNATURE):
            print(f"{prefix}icon PNG {len(payload)} bytes sha256={digest}")
        else:
            print(f"{prefix}icon invalid header (bytes={len(payload)} sha256={digest})")

    print(f"[apps] reported {total} launchable applications")
    describe_icon("[apps] default ", default_icon)

    if not apps:
        print("[apps] no app metadata received")
        return

    launchable_with_icons = 0
    for app in apps:
        prefix = f"[apps] {app.label} ({app.package}) "
        describe_icon(prefix, app.icon_png)
        if app.icon_png:
            launchable_with_icons += 1

    if launchable_with_icons == 0:
        print("[apps] WARNING: no launchable apps returned a non-empty icon payload")
    else:
        print(f"[apps] icons received for {launchable_with_icons}/{len(apps)} sampled apps")


def run_notification_suite(client: PandaClient, sample_limit: int = 5) -> None:
    notifications = client.list_notifications()
    if not notifications:
        print("[notifications] no active notifications reported (requires notification access)")
        return

    print(f"[notifications] active notifications: {len(notifications)}")
    for index, notification in enumerate(notifications[:sample_limit]):
        posted = datetime.fromtimestamp(notification.posted_at_ms / 1000.0)
        preview_title = notification.title or "<no title>"
        preview_text = notification.text or "<no text>"
        print(
            f"    #{index}: {notification.package} "
            f"key={notification.key} clearable={notification.clearable}"
        )
        print(f"        title: {preview_title}")
        print(f"        text : {preview_text}")
        print(f"        posted: {posted.isoformat(timespec='seconds')}")
        if notification.actions:
            print(f"        actions ({len(notification.actions)}):")
            for action_index, action in enumerate(notification.actions):
                suffix = " (input)" if action.requires_input else ""
                label = action.title or f"<action #{action_index}>"
                print(f"            - [{action_index}] {label}{suffix}")
        else:
            print("        actions: none")


AVAILABLE_SUITES: Dict[str, Callable[[PandaClient], None]] = {
    "wifi": lambda client: run_wifi_suite(client, scan_limit=5),
    # "clipboard": lambda client: run_clipboard_suite(client, text="Hello from Panda test!"),
    "storage": run_storage_suite,
    "apps": run_app_suite,
    "notifications": run_notification_suite,
}

DEFAULT_SUITES: List[str] = [name for name in AVAILABLE_SUITES if name not in {"clipboard", "audio"}]


def parse_args(argv: Optional[Iterable[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Panda service smoke tester")
    connection = parser.add_mutually_exclusive_group()
    connection.add_argument("--unix", dest="unix_socket", help="Unix domain socket path (e.g. @panda-1.1.0)")
    connection.add_argument("--host", default="127.0.0.1", help="TCP host (default: 127.0.0.1)")
    parser.add_argument("--port", type=int, default=9999, help="TCP port forwarded to Panda (default: 9999)")
    parser.add_argument(
        "--tests",
        nargs="*",
        choices=sorted(AVAILABLE_SUITES),
        default=DEFAULT_SUITES,
        help="Suites to execute",
    )
    parser.add_argument("--timeout", type=float, default=10.0, help="Socket timeout in seconds")
    return parser.parse_args(argv)


def main(argv: Optional[Iterable[str]] = None) -> int:
    args = parse_args(argv)
    if not args.tests:
        print("No suites selected; nothing to do.")
        return 0
    host = None if args.unix_socket else args.host
    port = args.port if not args.unix_socket else None
    exit_code = 0

    for name in args.tests:
        print(f"\n=== Running {name} suite ===")
        try:
            with PandaClient(
                host=host,
                port=port,
                unix_socket=args.unix_socket,
                timeout=args.timeout,
            ) as client:
                AVAILABLE_SUITES[name](client)
        except (ConnectionError, OSError) as err:
            print(f"[{name}] Failed to connect to Panda service: {err}", file=sys.stderr)
            exit_code = 2
        except PandaProtocolError as err:
            print(f"[{name}] Service returned an error: {err}", file=sys.stderr)
            if exit_code == 0:
                exit_code = 3

    return exit_code


if __name__ == "__main__":
    sys.exit(main())

