#!/usr/bin/env python3
"""Prepare the newly installed Ubuntu Redis while its start guard is closed."""

from contextlib import ExitStack
import grp
import hashlib
import os
from pathlib import Path
import secrets
import stat
import subprocess
import sys
import tempfile


CONFIG = Path("/etc/redis/redis.conf")
OVERRIDES = Path("/etc/redis/job-tracker.conf")
ACL = Path("/etc/redis/job-tracker.acl")
PRIVATE = Path("/root/job-tracker-redis-private")
READY = Path("/etc/redis/job-tracker.ready")
GUARD = Path("/etc/systemd/system/redis-server.service.d/10-job-tracker-ready.conf")
# Original redis.conf from Ubuntu package 5:8.0.5-1ubuntu0.1.
STOCK_SHA256 = "68ff69f4b44ebce67227ff4bd1aa5a38f1e9c155d13948183b0453e4354a89f4"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def acl_contents(admin_password: str, app_password: str) -> bytes:
    return (
        "user default off resetpass resetkeys resetchannels -@all\n"
        f"user job_tracker_admin on #{sha256(admin_password.encode())} ~* &* +@all\n"
        f"user job_tracker_app on #{sha256(app_password.encode())} ~vision:quota:v1:* "
        "+ping +hello +client|setinfo +select|0 +get +set +eval +evalsha +time +type +expiretime\n"
    ).encode()


def override_contents(acl_path: Path) -> bytes:
    return (
        "bind 127.0.0.1\n"
        "protected-mode yes\n"
        f"aclfile {acl_path}\n"
        "maxmemory 32mb\n"
        "maxmemory-policy noeviction\n"
        "appendonly yes\n"
        "appendfsync always\n"
    ).encode()


def write_new(path: Path, contents: bytes, cleanup: ExitStack, redis_gid=None) -> None:
    with path.open("xb") as output:
        cleanup.callback(path.unlink)
        output.write(contents)
        if redis_gid is not None:
            os.fchown(output.fileno(), 0, redis_gid)
            os.fchmod(output.fileno(), 0o640)


def check_environment() -> tuple[bytes, os.stat_result]:
    if sys.platform != "linux" or os.geteuid() != 0:
        raise ValueError("请在 Linux 服务器的 root SSH 终端运行。")
    if READY.exists() or READY.is_symlink() or GUARD.is_symlink():
        raise ValueError("启动条件与预期不符。")
    if GUARD.read_text() != f"[Unit]\nConditionPathExists={READY}\n":
        raise ValueError("启动保护文件内容与预期不符。")
    subprocess.run(["systemctl", "daemon-reload"], check=True, capture_output=True)
    state = subprocess.check_output(
        ["systemctl", "show", "redis-server.service", "-p", "LoadState", "-p", "ActiveState", "-p", "DropInPaths"],
        text=True,
    ).splitlines()
    if "LoadState=loaded" not in state or "ActiveState=inactive" not in state:
        raise ValueError("Redis 服务应处于已加载且未启动的状态。")
    drop_ins = next((line.partition("=")[2].split() for line in state if line.startswith("DropInPaths=")), [])
    if drop_ins != [str(GUARD)]:
        raise ValueError("Redis 有额外的 systemd 覆盖配置，需要先核对。")
    if subprocess.check_output(["ss", "-H", "-ltn", "sport = :6379"]).strip():
        raise ValueError("6379 已有监听，需要先核对占用。")
    attributes = CONFIG.lstat()
    if not stat.S_ISREG(attributes.st_mode):
        raise ValueError("Redis 主配置应为普通文件。")
    original = CONFIG.read_bytes()
    if sha256(original) != STOCK_SHA256:
        raise ValueError("Redis 主配置与已核对的软件包原版不同，需要先查看差异。")
    return original, attributes


def main() -> int:
    try:
        original, attributes = check_environment()
        redis_gid = grp.getgrnam("redis").gr_gid
        os.umask(0o077)
        admin_password = secrets.token_hex(32)
        app_password = secrets.token_hex(32)
        with ExitStack() as cleanup:
            PRIVATE.mkdir(mode=0o700)
            cleanup.callback(PRIVATE.rmdir)
            write_new(PRIVATE / "redis.conf.original", original, cleanup)
            write_new(PRIVATE / "admin.password", (admin_password + "\n").encode(), cleanup)
            write_new(PRIVATE / "app.password", (app_password + "\n").encode(), cleanup)
            write_new(ACL, acl_contents(admin_password, app_password), cleanup, redis_gid)
            write_new(OVERRIDES, override_contents(ACL), cleanup, redis_gid)
            with tempfile.TemporaryDirectory(prefix=".job-tracker-", dir=CONFIG.parent) as staged:
                pending = Path(staged) / CONFIG.name
                with pending.open("wb") as output:
                    output.write(original + f"\ninclude {OVERRIDES}\n".encode())
                    os.fchown(output.fileno(), attributes.st_uid, attributes.st_gid)
                    os.fchmod(output.fileno(), stat.S_IMODE(attributes.st_mode))
                os.replace(pending, CONFIG)
                cleanup.pop_all()
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    except (OSError, KeyError, subprocess.SubprocessError) as error:
        # Package state, permissions or file conflicts can be checked without
        # printing credentials or the contents of the configuration files.
        print(f"Redis 配置准备中止（{type(error).__name__}）。", file=sys.stderr)
        return 1

    print("Redis 配置已写入，启动保护仍生效。")
    print(f"备份和凭据保存在 {PRIVATE}，目录权限 700，文件权限 600。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
