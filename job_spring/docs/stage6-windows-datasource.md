# 阶段六 Windows 后端配置

Windows 的 Java 初次部署监听 `127.0.0.1:8082`。Windows 上的 SSH 客户端把本地 `13306` 转到 Linux 回环 `3306`，把本地 `16379` 转到 Linux 回环 `6379`。Windows 与 Mac 各自建立 SSH 连接，共用 Linux 的正式 `job_tracker` 库和 Redis DB 0。

## 配置加载

1. Maven 的 `windows` profile 在处理资源时把 `@profile.name@` 替换为 `windows`。在 IDEA 勾选 `windows` 后重新构建，或在命令行用 `-Pwindows` 构建。
2. Spring 从运行目录导入 `config/application-windows.yaml`，再读取同一运行目录下的 `config/credentials-windows-local.yaml`。两个导入都必需，漏传文件时启动报错。
3. Windows 使用 MySQL 专用账号 `job_tracker_windows`。JDBC 要求 TLS，连接会话使用 `+08:00`，并且 `spring.sql.init.mode=never`。Redis 使用现有受限应用账号 `job_tracker_app`，模型初次部署保持关闭。

Windows 的私有文件结构如下。实际值通过私有渠道在设备端配置，此文件已被 Git 忽略。

```yaml
job-tracker:
  windows:
    database-password: '填入 Windows 专用 MySQL 密码'
    redis-password: '填入 Linux Redis 应用账号密码'
```

## Windows 实机验收顺序

1. 在 Windows PowerShell 用 `Get-Command ssh.exe` 确认 OpenSSH Client 可用。用一条 SSH 会话转发两个回环端口，并保持窗口打开。

   ```powershell
   ssh.exe -N -o ExitOnForwardFailure=yes -L 127.0.0.1:13306:127.0.0.1:3306 -L 127.0.0.1:16379:127.0.0.1:6379 root@8.136.39.58
   ```

   新开 PowerShell，分别用 `Test-NetConnection 127.0.0.1 -Port 13306 -InformationLevel Quiet` 和 `Test-NetConnection 127.0.0.1 -Port 16379 -InformationLevel Quiet` 核对本地监听。这一步只证明 Windows 的监听可达，远端服务认证单独验收。 [Windows OpenSSH Client](https://learn.microsoft.com/en-us/windows-server/administration/openssh/openssh_install_firstuse) · [SSH 本地转发参数](https://man.openbsd.org/ssh.1)
2. 在 Linux 为 `job_tracker_windows` 准备回环账号，核对账号名是否已存在，按正式库四张业务表授予读写权限。配置 Windows 私有文件后，分别验证 JDBC TLS、会话时区及 Redis ACL `PING`。本步先做只读验证。
3. 用 `windows` profile 启动 Java，核对 `/api/health` 返回 `UP/UP`，匿名岗位接口返回 `401`。Windows 前端、模型启用和域名入口分别安排。

回退时停止 Windows Java，并按 `Ctrl+C` 关闭这条 Windows SSH 会话。Linux 正式库继续作为业务数据来源。

## 本次开发验证

2026-11-04 在 Mac 的临时目录用 Java 17 和 Maven `-Pwindows` 运行 715 项后端测试，失败、错误和跳过均为 0，JAR 构建成功。使用内存 H2、独立 Redis 和虚构凭据。Spring 配置探针确认外置端口和私有值均生效，缺任一必需 YAML 时配置加载失败。已核对 JAR 的激活 profile 为 `windows`，两份外置 YAML 均未打包。Windows 实机 SSH、MySQL 账号及服务认证待验收。
