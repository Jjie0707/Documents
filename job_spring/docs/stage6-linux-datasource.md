# 阶段六 Linux Java 运行配置

2026-11-03 Linux Java 试运行和常驻服务验收通过。`job-tracker.service` 已启用并运行，监听 `127.0.0.1:8082`。下一步准备 Nginx 入口。

## 请求链路

```text
Maven -Plinux → 资源过滤 → JAR 中 Spring 激活 linux
  → 工作目录的 config/application-linux.yaml
  → config/credentials-linux-local.yaml 提供两个密码
  → HikariCP → Linux 127.0.0.1:3306/job_tracker
  → Lettuce → Linux 127.0.0.1:6379，Redis DB 0
```

Maven 用 `pom.xml` 中的 `profile.name` 替换 `application.yaml` 的 `@profile.name@`。Spring 加载 Linux 文档后，要求两个外置 YAML 都存在。应用监听 `127.0.0.1:8082`，供后续同机 Nginx 转发。MySQL 的 `sslMode=REQUIRED` 要求连接使用 TLS，JDBC 会话时区为 UTC+8。Redis 使用已验收的 `job_tracker_app`。SQL 初始化为 `never`。 [Maven 配置](../pom.xml) · [基础配置](../src/main/resources/application.yaml) · [Linux 外置配置](../config/application-linux.yaml) · [Spring 外置配置](https://docs.spring.io/spring-boot/reference/features/external-config.html)

首次 Linux 运行使用 `vision.model.enabled=false`。正式启用识别时再统一 Redis、HMAC、额度窗口和可信代理，并核对运行参数和环境变量中的优先级覆盖。Mac 的真实识别环境继续按已有接入记录管理。

## MySQL 专用账号

用户已在 Linux 用 root 经 TCP 回环以 `--ssl-mode=REQUIRED` 登录。实际协商套件为 `TLS_AES_128_GCM_SHA256`，`job_tracker_linux` 同名账号数为 0，正式库 `users`、`job_applications`、`interview_rounds` 和 `interview_questions` 四表均为 InnoDB。此次核对只查询元数据。

核对通过后，在 Linux 的 root SSH 终端运行账号脚本。第一次输入 MySQL root 密码时，服务端生成随机密码，账号以 `ACCOUNT LOCK` 创建且要求 TLS。脚本将生成结果与 Redis 已有应用密码写入服务器私有目录 `/root/job-tracker-linux-private/credentials-linux-local.yaml`，目录权限 700，文件权限 600。第二次输入 MySQL root 密码后，脚本授予四表各自的 `SELECT, INSERT, UPDATE, DELETE`，再解锁并显示授权。应用账号与网站用户是两层身份，网站用户仍由原有登录和数据归属规则隔离。 [账号脚本](../scripts/configure-linux-db-account.py) · [MySQL 创建账号](https://dev.mysql.com/doc/refman/8.4/en/create-user.html)

用户已回传实际执行结果。MySQL 显示 `job_tracker_linux@127.0.0.1` 的一条基础 `USAGE ON *.*` 和四条正式表的 `SELECT, INSERT, UPDATE, DELETE` 授权。脚本确认私有 YAML 已保存到上述服务器目录。真实 JDBC 和 Redis 连接结果见下方试运行记录。

私有 YAML 的路径已加入 Git 忽略规则，放入实际运行目录和设置服务账号读权限属于后续部署。若创建或授权中断，先用管理账号核对账号是否存在、锁定状态和已授予权限。恢复时仅处理本次新建账号和私有 YAML。若取消部署，确认该账号未被实例使用后再按确认方案撤销其授权。业务数据保留在 Linux 正式库。

## 开发验证

- Java 17 在独立临时源码目录执行 `-Plinux` 的全量后端测试与 JAR 构建，715 项通过，0 失败、错误或跳过。测试用 H2、独立 Redis 和虚构配置。
- 从实际 JAR 读取过滤后的基础配置，以 Spring 的配置加载器验证 Linux profile、地址、TLS 参数、两个私有凭据和模型关闭。模拟旧本地文件存在时，Linux 配置仍按预期生效。分别移除 Linux 外置 YAML 和凭据文件，验证加载失败。JAR 中核对了外置文件及虚构凭据的排除。
- 账号脚本通过 Python 语法检查，并在 Mac 的独立 MySQL 9.3 临时实例用四张虚构表验证了随机密码结果解析、四表授权、私有 YAML 权限及解锁。该实例中应用账号通过 TLS 登录，跨库读取、建表和无 TLS 的 TCP 登录均被拒绝。Linux MySQL 8.4 的实际账号和 JDBC 验收见上方授权结果及下方试运行记录。

## Linux 部署试运行

部署包位于本机 `target/linux-delivery/job-tracker-linux-smoke.tar.gz`。包内含通过 715 项测试的 Linux JAR、非敏感外置 YAML、试运行脚本、Java 17 连接探针字节码和 SHA-256 清单。打包前确认 POM、源码及外置 YAML 与通过测试的副本一致。服务器私有 YAML 由脚本在服务器内部复制。 [试运行脚本](../scripts/prepare-linux-smoke.sh) · [探针源码](../scripts/LinuxDeploymentProbe.java)

脚本先核对摘要、Java 17、可用内存、专用账号、运行目录与端口状态，再准备 `/opt/job-tracker`。专用系统账号 `job-tracker` 通过组权限读取 root 所有的 JAR 和配置，目录为 750，文件为 640。源私有 YAML 继续位于 root 私有目录。正式端口保留为 8082。试运行使用 `--server.port=0`，通过服务 PID 查找系统分配的 IPv4 回环监听端口。

探针在相同工作目录和系统账号下使用 Spring 配置加载器读取凭据。Connector/J 在只读事务中验证正式库、账号、会话 UTC+8 和 TLS，Lettuce 用应用 ACL 账号发送 Redis `PING`。实际 JAR 随后以 Java 17 启动临时 systemd 服务，初始堆 64 MiB、最大堆 192 MiB，服务内存上限 448 MiB，试运行最多 120 秒。验收要求 `/api/health` 为 `UP/UP`、匿名访问岗位接口为 HTTP 401，随后停止临时服务。 [systemd 临时服务](https://freedesktop.org/software/systemd/man/latest/systemd-run.html)

本地已用虚构账号、独立 MySQL 与 Redis 验证连接探针和 JAR 启动，确认 JDBC TLS、Lettuce PONG、健康接口与匿名 401。

用户在 Linux 实际执行试运行，上传包及包内四项摘要均通过，运行时为 OpenJDK 17.0.20.1。探针确认 MySQL 正式 `job_tracker`、`job_tracker_linux@127.0.0.1`、会话 `+08:00`、TLS 套件 `TLS_AES_256_GCM_SHA384`，Redis DB 0 以 `job_tracker_app` 返回 `PONG`。实际 JAR 通过 `job-tracker-smoke.service` 监听当次回环端口 `35603`，健康接口为 `UP/UP`，匿名岗位接口为 HTTP 401。脚本返回试运行通过并已停止临时服务，保留 `/opt/job-tracker` 及专用系统账号。

继续部署时沿用已验收的目录和 JAR。正式常驻服务将使用回环 8082，Nginx 和共享额度切换分别实施。试运行期间保持模型关闭。

## Linux 常驻服务

将 `job-tracker.service` 和 `install-linux-service.sh` 上传到服务器 `/root/`，再在 Linux root SSH 终端执行 `bash /root/install-linux-service.sh`。单元沿用已经试运行的 JAR、外置文件及专用系统账号。 [systemd 单元](../deploy/linux/job-tracker.service) · [安装脚本](../scripts/install-linux-service.sh)

- `User=job-tracker` 和 `WorkingDirectory=/opt/job-tracker` 固定应用身份及相对配置路径。使用 Ubuntu amd64 的 Java 17 完整路径，命令参数固定回环 8082 和模型关闭。
- Java 堆初始 64 MiB、最大 192 MiB，systemd `MemoryMax=448M` 限制进程组总内存。失败后等 5 秒重启，60 秒最多启动 3 次。 `ProtectSystem=strict` 提供只读系统视图，`PrivateTmp` 保留独立临时目录。 [systemd 服务](https://www.freedesktop.org/software/systemd/man/latest/systemd.service.html) · [systemd 内存限制](https://www.freedesktop.org/software/systemd/man/latest/systemd.resource-control.html)
- 安装前核对端口、服务名、可用内存、MySQL 和 Redis 的运行及开机状态。脚本检查磁盘上的 Nginx 配置是否引用 8082，并检查本机 cloudflared 进程，命中时停止并要求先核对入口。Linux 上先执行 `systemd-analyze verify`，随后启动 Java。确认 8082 的进程和地址、健康接口和匿名 401 后才设置开机启动。 [systemd 单元校验](https://www.freedesktop.org/software/systemd/man/latest/systemd-analyze.html#systemd-analyze%20verify%20FILE%E2%80%A6) · [Nginx 配置检查](https://nginx.org/en/docs/switches.html)
- 验收记录 `active`、`enabled`、内存当前值与峰值、重启次数以及本机 HTTP 结果。开机启用通过 systemd 状态确认，服务器重启留到有维护窗口时验证。失败时脚本尝试停用本次 Java 服务并保留单元文件诊断。成功后如需回退，执行 `systemctl disable --now job-tracker.service`，业务库、Redis 及运行目录继续保留。

本地检查覆盖脚本语法和单元参数。用户在 Linux 运行安装脚本后回传 `enabled`、`ActiveState=active`、`NRestarts=0`，监听 `127.0.0.1:8082`，健康为 `UP/UP`，匿名岗位接口为 HTTP 401。实测 `MemoryCurrent=232775680` 字节，约 222 MiB，峰值为 232976384 字节，服务上限 448 MiB。开机链接已创建，实际开机复验留到维护窗口。

下一步核对 Nginx 现有站点，准备静态前端目录、`/api/` 回环反代、访问入口和回退方案供用户确认。Linux 正式库和额度配置按既有边界继续使用。

## 新版前端与图片识别发布

从 `frontend` 分支 `f5e9b01` 的源码构建 Linux JAR 和前端 `dist`。发布包包含 JAR、前端文件、两份非私有运行配置和 SHA-256 清单。Linux 外置 YAML 和 systemd 单元移除初次部署的模型关闭覆盖。未放置私有模型文件时，Spring 仍默认关闭模型。

发布包位于 `target/linux-delivery/job-tracker-linux-release-f5e9b01.tar.gz`，SHA-256 为 `5a5c6f4b06dfb1324c18924dd143b2d7b34ddeacda354a57bbca6e17337c9fc9`。Java 17 隔离后端测试 715 项、Linux JAR 构建、前端测试 26 项和生产构建均通过。配置探针使用虚构 Key，验证模型开关、30/300 共享额度和可信代理判断。已核对前端入口引用、字体、包内摘要和私有文件排除。用户交互上传后确认摘要通过，并批准切换。

### 切换步骤

1. 在 Linux 暂存目录验证发布包和内部清单。核对 `job-tracker.service` 的有效启动参数、启动前状态和 `5175` 站点。确认 Java 仍只监听 `127.0.0.1:8082`，Nginx 使用 `job-tracker.locations.conf` 将 `/api/` 发给它，并用 `$remote_addr` 覆写 `X-Forwarded-For`。新版单元只信任 Nginx 连接所用的 `127.0.0.1/32`。直连入口和域名另行处理。
2. 将 Mac 已验收的私有模型与额度 YAML 通过 SSH 暂存到 Linux 的 root 私有目录。切换时以 `root:job-tracker`、权限 `640` 放入 `/opt/job-tracker/config/`。同一 Redis、HMAC、北京时间窗口和 `30/300` 上限继续生效。Linux 经 Nginx 按真实客户端 IP 选额度键。
3. 保留原 `/opt/job-tracker/job-tracker.jar`、`/opt/job-tracker/config/application-linux.yaml`、已存在的私有配置、`/etc/systemd/system/job-tracker.service` 和 `/opt/job-tracker-nginx-preview/frontend`。停止 Linux Java 后替换上述程序和配置，静态文件仍使用 `5175` 站点的原目录。通过 `systemd-analyze verify` 和 `nginx -t` 后重新加载 systemd 并启动 Java。
4. 核对 `5175` 首页、静态资源、深链接、`/api/health` 和匿名岗位请求。用户确认额度页面可读后，用一张虚构招聘截图验收识别草稿和单次扣额。HTTP 连通性、登录后的网页验收和真实模型调用分别记录。

将 [发布脚本](../scripts/upgrade-linux-release.sh) 与两份私有 YAML 暂存至 `/root/job-tracker-linux-private/`，在 Linux 的交互式 root SSH 终端执行 `bash /root/job-tracker-linux-private/upgrade-linux-release.sh`。发布脚本在切换后校验首页、深链接、JS、CSS、字体、健康和匿名权限。配置或健康检查失败时，自动恢复原 JAR、配置、单元、前端及 Java 的原运行状态。成功后也可用脚本输出的 `--rollback` 命令恢复。回退继续使用共享 Redis 中已有的额度计数。

脚本通过 Bash 语法检查，并在隔离临时目录模拟验证成功切换、手动回退和 HTTP 检查失败后的自动回退。

2026-11-04 用户在 Linux 首次执行时，旧 Java 因之前手动停止产生的退出码 143 被 systemd 记为 `failed`，发布在替换文件前中止。用户核对 systemd 日志后清除失败状态并重跑。服务器回传新版已启动，`5175` 首页、深链接、静态资源、健康 UP/UP 和匿名 401 全部通过。用户随后确认 Linux 图片识别正常。旧版文件位于脚本输出的备份目录。

### 日常启动与关闭

在 Linux root 终端用 `systemctl start job-tracker` 启动，用 `systemctl stop job-tracker` 停止。Java 由 systemd 管理，关闭 FinalShell 后继续运行。启动后可用 `curl -fsS http://127.0.0.1:5175/api/health` 验证。停止后可用 `ss -Hltn 'sport = :8082'` 确认 Java 端口已释放，Nginx 仍能返回静态页面。若停止时显示退出码 143，核对已无 Java 监听后可用 `systemctl reset-failed job-tracker` 清除失败标记。
