#!/usr/bin/env python3
"""Create a deployment's MySQL application account on Linux and stage its private YAML."""

import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys


TABLES = ("users", "job_applications", "interview_rounds", "interview_questions")
PRIVATE_ROOT = Path("/root")
REDIS_PASSWORD = Path("/root/job-tracker-redis-private/app.password")


def mysql_admin(sql: str) -> str:
    # mysql prompts on the controlling terminal. Its query output stays in memory.
    env = os.environ.copy()
    env.pop("MYSQL_PWD", None)
    env["MYSQL_HISTFILE"] = "/dev/null"
    return subprocess.run(
        [
            "mysql", "--no-defaults", "--no-login-paths", "--protocol=TCP",
            "--host=127.0.0.1", "--port=3306", "--ssl-mode=REQUIRED",
            "--user=root", "--password", "--batch", "--raw", "--skip-column-names",
        ],
        input=sql,
        stdout=subprocess.PIPE,
        text=True,
        check=True,
        env=env,
    ).stdout


def credentials_yaml(target: str, database_password: str, redis_password: str) -> str:
    return (
        "job-tracker:\n"
        f"  {target}:\n"
        f"    database-password: {json.dumps(database_password)}\n"
        f"    redis-password: {json.dumps(redis_password)}\n"
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--target", choices=("linux", "windows"), default="linux")
    target = parser.parse_args().target
    if sys.platform != "linux" or os.geteuid() != 0 or not sys.stdin.isatty():
        print("请在 Linux 的交互式 root SSH 终端运行。", file=sys.stderr)
        return 1

    username = f"job_tracker_{target}"
    account = f"'{username}'@'127.0.0.1'"
    private_dir = PRIVATE_ROOT / f"job-tracker-{target}-private"
    credentials = private_dir / f"credentials-{target}-local.yaml"

    try:
        redis_file = REDIS_PASSWORD.lstat()
        if not stat.S_ISREG(redis_file.st_mode) or redis_file.st_uid != 0 or redis_file.st_mode & 0o077:
            raise ValueError("Redis 凭据文件应为 root 私有文件。")
        redis_password = REDIS_PASSWORD.read_text().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", redis_password):
            raise ValueError("Redis 应用凭据格式与已部署的账号不符。")
        os.umask(0o077)
        private_dir.mkdir(mode=0o700)
    except (ValueError, OSError):
        print("私有目录或 Redis 凭据检查未通过，MySQL 账号步骤尚未执行。", file=sys.stderr)
        return 1

    try:
        print("第一次输入 MySQL root 密码，创建暂时锁定的应用账号。", flush=True)
        generated = mysql_admin(
            "SET SESSION generated_random_password_length = 40;\n"
            f"CREATE USER {account} IDENTIFIED WITH caching_sha2_password BY RANDOM PASSWORD "
            "REQUIRE SSL ACCOUNT LOCK;\n"
        ).rstrip("\n").split("\t")
        if len(generated) != 4 or generated[:2] != [username, "127.0.0.1"] or len(generated[2]) != 40:
            raise ValueError("MySQL 随机密码结果格式异常。")
        with credentials.open("x", encoding="utf-8") as output:
            output.write(credentials_yaml(target, generated[2], redis_password))
            output.flush()
            os.fsync(output.fileno())
        del generated, redis_password

        print("第二次输入 MySQL root 密码，授予正式四表权限并解锁。", flush=True)
        grants = "\n".join(
            f"GRANT SELECT, INSERT, UPDATE, DELETE ON job_tracker.{table} TO {account};"
            for table in TABLES
        )
        results = mysql_admin(f"{grants}\nALTER USER {account} ACCOUNT UNLOCK;\nSHOW GRANTS FOR {account};\n")
        for line in results.splitlines():
            if line.startswith("GRANT "):
                print(line)
    except (ValueError, OSError, subprocess.SubprocessError, KeyboardInterrupt):
        print("账号准备未完成。私有目录已保留，请先核对账号和授权状态再续跑。", file=sys.stderr)
        return 1

    print(f"{target} 应用账号已创建，私有 YAML 已保存到 {credentials}。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
