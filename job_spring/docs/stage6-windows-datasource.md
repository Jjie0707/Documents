# 阶段六 Windows 后端配置

Windows 的 Java 初次部署监听 `127.0.0.1:8082`。Windows 上的 SSH 客户端把本地 `13306` 转到 Linux 回环 `3306`，把本地 `16379` 转到 Linux 回环 `6379`。Windows 与 Mac 各自建立 SSH 连接，共用 Linux 的正式 `job_tracker` 库和 Redis DB 0。

## 配置加载

1. Maven 的 `windows` profile 在处理资源时把 `@profile.name@` 替换为 `windows`。在 IDEA 勾选 `windows` 后重新构建，或在命令行用 `-Pwindows` 构建。
2. Spring 从运行目录导入 `config/application-windows.yaml`，再读取同一运行目录下的 `config/credentials-windows-local.yaml`。两个导入都必需，漏传文件时启动报错。
3. Windows 使用 MySQL 专用账号 `job_tracker_windows`。JDBC 要求 TLS，连接会话使用 `+08:00`，并且 `spring.sql.init.mode=never`。Redis 使用现有受限应用账号 `job_tracker_app`。模型默认关闭，可通过私有配置启用。

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
2. 在 Linux 为 `job_tracker_windows` 准备回环账号。上传 [账号准备脚本](../scripts/configure-linux-db-account.py)，在 Linux 的交互式 root SSH 终端运行 `python3 /root/configure-windows-db-account.py --target windows`。脚本先创建暂时锁定的 TLS 账号，由 MySQL 生成随机密码并保存私有 YAML，再授予正式库四张业务表的读写权限并解锁。结果存于 `/root/job-tracker-windows-private/credentials-windows-local.yaml`。若脚本中途失败，先核对账号、授权和私有文件状态。配置 Windows 私有文件后，分别验证 JDBC TLS、会话时区及 Redis ACL `PING`。本步先做只读连接验证。
3. 用 `windows` profile 启动 Java，核对 `/api/health` 返回 `UP/UP`，匿名岗位接口返回 `401`。Windows 前端、模型启用和域名入口分别安排。

回退时停止 Windows Java，并按 `Ctrl+C` 关闭这条 Windows SSH 会话。Linux 正式库继续作为业务数据来源。

## 本次开发验证

2026-11-04 在 Mac 的临时目录用 Java 17 和 Maven `-Pwindows` 运行 715 项后端测试，失败、错误和跳过均为 0，JAR 构建成功。使用内存 H2、独立 Redis 和虚构凭据。Spring 配置探针确认外置端口和私有值均生效，缺任一必需 YAML 时配置加载失败。已核对 JAR 的激活 profile 为 `windows`，两份外置 YAML 均未打包。

用户确认 Windows 两个 SSH 本地端口可达，后端启动并收到 MySQL 的账号拒绝。Linux 只读查询确认 Windows 专用账号尚未创建。已为现有账号准备脚本增加 `--target windows`，用虚构输入核对两种目标的目录权限、授权范围、凭据写入、输出和防止重复执行。真实账号创建和服务认证待验收。

用户确认脚本已在 Linux 创建 Windows 应用账号，私有 YAML 经 SCP 到达 Windows 并生效。Windows 后端健康检查 UP/UP，网页可登录并显示已有岗位记录。只读额度接口最初返回 `NOT_CONFIGURED`。Windows 凭据文件包含数据库和 Redis 密码，额度查询还需要与 Mac 相同的 HMAC 密钥。已核对 Mac 的 Git 忽略文件 `config/vision-quota-local.yaml` 仅包含该密钥。

用户通过 SSH 传递额度配置并重启 Windows 后端后，只读额度接口返回 `AVAILABLE` 并读到既有计数。Windows 的 Redis 认证和共享额度读取通过。保留两端项目目录中生效的私有配置，可以清理 Linux 及 Windows 私有下载目录中的临时额度文件副本。模型调用、域名入口另行验收。

## Windows 图片识别

Windows 外置配置移除了初次部署时的模型关闭覆盖。Spring 继续使用基础配置对 `config/vision-model-local.yaml` 的可选导入。Windows 通过 SSH 接收 Mac 已配置的私有模型文件，放到本机项目的 `config/` 后重启 Java。文件已被 Git 忽略，默认模型仍为 `glm-4.1v-thinking-flash`，共享 Redis、HMAC 和每日额度策略沿用已验收的配置。

Java 17 隔离验证覆盖 Windows 无模型私有文件时关闭、带虚构 Key 的私有文件时启用，以及 Linux profile 仍关闭。715 项后端测试和 `-Pwindows` 打包通过。Windows 实机用一张虚构招聘截图验收识别草稿和额度变化。回退时将 Windows 私有模型文件移出 `config/` 再重启。传输副本待验收后清理。

## Windows 域名入口

2026-11-04 用户确认 Mac 浏览器可访问 `job.jwilu.com`。独立 HTTPS 只读检查确认请求经过 Cloudflare，首页 HTTP 200，`/api/health` HTTP 200 且为 UP/UP，匿名 `/api/jobs` HTTP 401。首页包含 Vite 开发客户端标记，当前域名入口使用开发前端。手机外网登录和岗位列表待用户验收。

2026-11-05 用户确认手机移动网络可访问域名并完成图片识别，Windows 图片识别也通过。手机经域名识别偏慢，尚未采集分段耗时。 [阶段六验收记录](stage6-acceptance.md)
