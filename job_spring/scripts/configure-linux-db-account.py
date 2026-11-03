#!/usr/bin/env python3
"""Create the Linux application's MySQL account and stage its private YAML."""

import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys


ACCOUNT = "'job_tracker_linux'@'127.0.0.1'"
TABLES = ("users", "job_applications", "interview_rounds", "interview_questions")
REDIS_PASSWORD = Path("/root/job-tracker-redis-private/app.password")
PRIVATE = Path("/root/job-tracker-linux-private")
CREDENTIALS = PRIVATE / "credentials-linux-local.yaml"


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


def credentials_yaml(database_password: str, redis_password: str) -> str:
    return (
        "job-tracker:\n"
        "  linux:\n"
        f"    database-password: {json.dumps(database_password)}\n"
        f"    redis-password: {json.dumps(redis_password)}\n"
    )


def main() -> int:
    if sys.platform != "linux" or os.geteuid() != 0 or not sys.stdin.isatty():
        print("请在 Linux 的交互式 root SSH 终端运行。", file=sys.stderr)
        return 1

    try:
        redis_file = REDIS_PASSWORD.lstat()
        if not stat.S_ISREG(redis_file.st_mode) or redis_file.st_uid != 0 or redis_file.st_mode & 0o077:
            raise ValueError("Redis 凭据文件应为 root 私有文件。")
        redis_password = REDIS_PASSWORD.read_text().strip()
        if not re.fullmatch(r"[0-9a-f]{64}", redis_password):
            raise ValueError("Redis 应用凭据格式与已部署的账号不符。")
        os.umask(0o077)
        PRIVATE.mkdir(mode=0o700)
    except (ValueError, OSError):
        print("私有目录或 Redis 凭据检查未通过，MySQL 账号步骤尚未执行。", file=sys.stderr)
        return 1

    try:
        print("第一次输入 MySQL root 密码，创建暂时锁定的应用账号。", flush=True)
        generated = mysql_admin(
            "SET SESSION generated_random_password_length = 40;\n"
            f"CREATE USER {ACCOUNT} IDENTIFIED WITH caching_sha2_password BY RANDOM PASSWORD "
            "REQUIRE SSL ACCOUNT LOCK;\n"
        ).rstrip("\n").split("\t")
        if len(generated) != 4 or generated[:2] != ["job_tracker_linux", "127.0.0.1"] or len(generated[2]) != 40:
            raise ValueError("MySQL 随机密码结果格式异常。")
        with CREDENTIALS.open("x", encoding="utf-8") as output:
            output.write(credentials_yaml(generated[2], redis_password))
            output.flush()
            os.fsync(output.fileno())
        del generated, redis_password

        print("第二次输入 MySQL root 密码，授予正式四表权限并解锁。", flush=True)
        grants = "\n".join(
            f"GRANT SELECT, INSERT, UPDATE, DELETE ON job_tracker.{table} TO {ACCOUNT};"
            for table in TABLES
        )
        results = mysql_admin(f"{grants}\nALTER USER {ACCOUNT} ACCOUNT UNLOCK;\nSHOW GRANTS FOR {ACCOUNT};\n")
        for line in results.splitlines():
            if line.startswith("GRANT "):
                print(line)
    except (ValueError, OSError, subprocess.SubprocessError, KeyboardInterrupt):
        print("账号准备未完成。私有目录已保留，请先核对账号和授权状态再续跑。", file=sys.stderr)
        return 1

    print(f"Linux 应用账号已创建，私有 YAML 已保存到 {CREDENTIALS}。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
