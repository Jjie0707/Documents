#!/usr/bin/env python3
"""Save the Mac application's password locally, without printing it or starting Java."""

import getpass
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import warnings


def save_password(destination: Path, password: str) -> None:
    # Match the character policy used when this account was created. It also
    # excludes Spring placeholder syntax, quotes and line breaks.
    if not re.fullmatch(r"[A-Za-z0-9@#%_+=.,:-]{16,128}", password):
        raise ValueError("password format")
    body = (
        "# 私有数据库密码，不提交、不打印；只由 mac profile 引用。\n"
        "job-tracker:\n"
        "  mac:\n"
        "    database-password: " + json.dumps(password) + "\n"
    )
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(destination, flags, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        os.fchmod(output.fileno(), 0o600)
        output.write(body)


def main() -> int:
    if len(sys.argv) != 1 or not sys.stdin.isatty():
        print("请直接在 Mac 交互式终端运行，不通过参数或管道传入密码。")
        return 1
    project = Path(__file__).resolve().parent.parent
    destination = project / "config" / "database-mac-local.yaml"
    if destination.exists() or destination.is_symlink():
        print("私有配置已存在，本次不覆盖。请在本机检查，不发送文件内容。")
        return 1
    try:
        ignored = subprocess.run(
            ["git", "check-ignore", "-q", "--", str(destination)],
            cwd=project,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            check=False,
        )
        if ignored.returncode != 0:
            print("私有配置未被 Git 忽略，未读取或保存密码。")
            return 1
        with warnings.catch_warnings():
            warnings.simplefilter("error", getpass.GetPassWarning)
            password = getpass.getpass("请输入 Linux job_tracker_mac 的密码：")
            confirmed = getpass.getpass("再次输入同一密码：")
        if password != confirmed:
            print("两次密码不一致，未保存。")
            return 1
        save_password(destination, password)
        del password, confirmed
    except ValueError:
        print("密码格式与创建账号时的要求不符，未保存。")
        return 1
    except FileExistsError:
        print("私有配置已存在，本次不覆盖。")
        return 1
    except (OSError, EOFError, KeyboardInterrupt, getpass.GetPassWarning):
        print("未完成保存，请在本机检查；不要发送密码或私有文件内容。")
        return 1
    print("Mac 数据库密码已保存到 Git 忽略的私有配置，权限为 600。")
    print("尚未连接数据库、启动后端或切换数据源。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
