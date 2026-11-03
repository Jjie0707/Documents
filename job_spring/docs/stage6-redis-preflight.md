# 阶段六 Redis 部署准备

2026-11-03 Linux Redis 8.0.5 已安装、配置并启动。用户回传初次启动与只读验证通过，业务端连接及正式额度切换待后续任务。

## 环境核对

用户在杭州 Linux 服务器的 SSH 终端实际检查了 Ubuntu 26.04.1 LTS。内存总量 1.7 GiB，当时可用 633 MiB，Swap 为 0。数据目录所在分区可用 34 GiB。服务器当时显示 +08:00，NTP 已同步。

安装前 APT 显示 `redis-server` 未安装，候选为 `5:8.0.5-1ubuntu0.1`。Redis 与 Valkey 的 systemd 服务均未找到，TCP 6379 无监听。同期在 Mac 确认本机 6379 有 Redis 监听，MySQL 的 SSH 转发 13306 也在监听。

## 安装验收

用户在 Linux SSH 终端完成安装。首次 APT 确认输入有误，APT 在下载前取消，已写入的启动保护保留。续跑时核对并复用该文件，安装了 `redis-server`、`redis-tools` 和 `liblzf1`，APT 报告 0 升级、3 新增、0 移除。

`redis-server --version` 返回 8.0.5，systemd 返回 `LoadState=loaded`、`ActiveState=inactive`、`ConditionResult=no`，TCP 6379 只有表头。服务已生成开机启动链接，准备标记缺失时由启动条件跳过。应用认证、持久化和正式额度切换仍待验证。

## 请求链路

Linux Java 通过 `127.0.0.1:6379` 使用 Redis。Mac 与 Windows 后续通过各自的 `127.0.0.1:16379` 及 SSH 本地转发访问 Linux 回环地址上的 Redis。两个设备各自使用本地端口，Mac 原有 6379 可保留。 [OpenSSH 本地转发](https://man.openbsd.org/ssh.1)

[application.yaml](../src/main/resources/application.yaml) 通过 `spring.data.redis` 配置连接。 [VisionQuotaService.java](../src/main/java/jjie/job_spring/vision/service/VisionQuotaService.java) 中的 `query()` 读取 IP 计数，`reserve()` 在模型调用前执行 [Lua 脚本](../src/main/resources/redis/reserve-vision-quota.lua)，同时检查和更新 IP 与全站计数。脚本依赖 Redis 7.0 起提供的 `EXPIRETIME`。 [Redis 命令文档](https://redis.io/docs/latest/commands/expiretime/)

## 配置与执行顺序

1. 用户已在 Linux 执行 `LC_ALL=C apt-get -s install --no-install-recommends redis-server`。结果为 0 升级、0 移除，仅新增 `redis-server`、`redis-tools` 和 `liblzf1`。正式安装时再核对提示与模拟结果一致。 [APT 模拟说明](https://manpages.ubuntu.com/manpages/trusty/man8/apt-get.8.html)
2. 已在 Mac 解包核对 Ubuntu 官方候选包的安装脚本和 systemd 单元。脚本在 systemd 环境中跳过 SysV 启动路径，由 `deb-systemd-invoke` 请求启动 `redis-server.service`。单元读取 `/etc/redis/redis.conf`，包内默认绑定回环。安装前新增 `/etc/systemd/system/redis-server.service.d/10-job-tracker-ready.conf`，使用 `ConditionPathExists=/etc/redis/job-tracker.ready`。保持标记缺失，安装后验收 `inactive` 和 6379 无监听。本次 APT 调用设 `NEEDRESTART_SUSPEND=1`，跳过该次自动重启检查。若安装失败，保留启动条件，先检查软件包状态和错误，再决定继续或取消。 [Ubuntu 软件包](https://packages.ubuntu.com/resolute/redis-server) · [systemd 启动条件](https://www.freedesktop.org/software/systemd/man/latest/systemd.unit.html#ConditionPathExists=) · [needrestart 参数](https://manpages.ubuntu.com/manpages/jammy/man1/needrestart.1.html)
3. 在发行版配置中设置 `bind 127.0.0.1` 和 `protected-mode yes`。应用使用独立 ACL 账号，限制到 `vision:quota:v1:*` 和连接、读取、执行额度脚本所需的命令，管理账号单独保管，默认用户关闭。ACL 文件与连接凭据只在部署设备私有位置存放。命令集合以实际 Spring Data Redis 客户端和 Lua 的隔离测试为准。 [Redis 网络安全](https://redis.io/docs/latest/operate/oss_and_stack/management/security/) · [Redis ACL](https://redis.io/docs/latest/operate/oss_and_stack/management/security/acl/)
4. 对专用额度实例拟设 `maxmemory 32mb` 和 `maxmemory-policy noeviction`，让内存不足时拒绝写入，避免计数被提前淘汰。启用 AOF 并拟使用 `appendfsync always`，在额度写入频率低的前提下优先保全已写计数。单独测量进程 RSS、持久化负载和命令延迟，确认是否需要调整。Java 常驻服务的内存预算另外验证。 [Redis 内存策略](https://redis.io/docs/latest/develop/reference/eviction/) · [Redis 持久化](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/)

## 配置脚本交付

通过 FinalShell 将脚本上传为 Linux 上的 `/root/configure-linux-redis.py`，在已登录的 root SSH 终端运行 `python3 /root/configure-linux-redis.py`。 [配置脚本](../scripts/configure-linux-redis.py)

用户通过截图确认脚本已运行成功，并返回备份与凭据的私有目录及权限。真实启动验证接续该结果。

脚本先核对 systemd 启动保护、服务状态、端口及 Ubuntu 安装包原版配置的摘要。它为 `/etc/redis/redis.conf` 添加末尾 include，由 `/etc/redis/job-tracker.conf` 提供上面七项设置。主配置采用同文件系统原子替换，保留属主和权限。原版备份以及随机生成的管理账号、应用账号密码存放在服务器 `/root/job-tracker-redis-private/`，目录 700、文件 600。Redis 的 `/etc/redis/job-tracker.acl` 只存密码摘要，供 `redis` 组读取。应用账号可操作额度命名空间，管理账号供本机验证与维护。脚本输出配置状态与路径，启动保护保持生效。

脚本退出异常时先核对配置文件状态。需要取消这次配置时，保持启动保护，核对原版备份摘要并恢复发行版主配置，再处理本次新增的两个 Redis 配置文件和私有凭据。

开发验证使用 Mac 的独立 Redis 8.6.2 和随机虚构凭据。Unix socket 检查覆盖匿名拒绝、账号权限、项目 Lua 扣减和 AOF 重启恢复。另用本项目已有构建产物中的 Spring Data Redis、Lettuce 和 Java 17，通过独立回环端口验证了命名账号认证及 Lua 执行。Ubuntu Redis 8.0.5 的实际配置应用和服务启动单独验收。

## 验证与切换

用户在 Linux 网页预览可访问后明确关注 Mac 本地 Java 的路径。Mac 本地 Java 已验收通过 SSH 连接 Linux MySQL，Redis 连接配置仍沿用既有本机配置。用户建立独立的 `16379` 转发后，Mac 实测该端口由 SSH 监听，匿名 `PING` 返回 `NOAUTH`。MySQL 转发 `13306` 和本机 Redis `6379` 同时在监听。

Mac 已在仓库外保存 Redis 应用凭据，文件权限为 600。用户确认通过 `16379` 认证后返回 `PONG`。2026-11-03 只读抽查时，Mac 本机 Redis DB 0 的当日额度键为 0，Linux 当日全站额度键不存在。应用账号权限限于已知额度键的业务操作。Linux 当日完整键数由服务器本机管理账号另行核对。

用户在 Linux 实际执行了初次启动与只读检查。服务返回 `active`，仅监听 `127.0.0.1:6379`，匿名连接返回 `NOAUTH`，应用账号 `PING` 返回 `PONG`。管理账号查到 `maxmemory=33554432`、`maxmemory-policy=noeviction`、`appendonly=yes`、`appendfsync=always`，持久化状态为 `aof_enabled:1`、`aof_last_write_status:ok`。准备标记已保留，允许已启用的 systemd 单元在开机时启动。真实服务器只做了连接与状态验证，扣减和 AOF 重启恢复证据来自前述隔离实例。

- Linux 初次启动验收由 root 创建 `/etc/redis/job-tracker.ready` 后启动新服务。确认只监听 `127.0.0.1:6379`、匿名 `PING` 返回 `NOAUTH`、应用账号返回 `PONG`，用管理账号只读检查配置和 AOF。凭据从服务器私有文件读取。若验证中止，撤回本次准备标记并停止新 Redis，保留配置与数据文件以便诊断。ACL、Lua 扣减和重启持久化使用独立实例与虚构计数验证。Mac 与 Windows 的 Redis 转发单独验证，并在建立前确认各自本机端口可用。
- 切换前核对每个实际识别入口、Redis 数据库编号、北京时间重置、额度配置和 IP 识别规则。各后端使用同一额度 Redis 和同一个现有 HMAC 密钥。核对密钥一致性时只记录比较结果。
- 首选在一个自然日结束时统一切换。事先约定暂停识别请求并等在途请求结束，旧计数按原期限自然到期。确认新自然日的旧端没有产生计数，再统一连接 Linux Redis 并恢复识别。若需日内切换，另行确认冻结窗口、当日键和值与绝对到期时间的迁移校验。
- 切换前出错时，保持现有识别入口指向原 Redis，并只处理新 Linux 实例。Linux Redis 开始记录真实额度后，应用版本回退仍沿用该额度存储。若存储故障，暂停识别，先核对最新计数再恢复；手动岗位录入继续沿用原路径。

### Mac Java 配置与验收

`config/application-mac.yaml` 在 Maven 的 `mac` profile 生效时读取可选的 `config/redis-mac-local.yaml`。私有文件已加入 Git 忽略规则，正式切换时以 600 权限创建，配置 Redis `127.0.0.1:16379`、DB 0 和 `job_tracker_app`。MySQL 继续经 `13306` 转发至 Linux 正式库。Java 17 隔离配置验证覆盖私有文件缺失时的 Redis 默认端口 `6379`，以及存在时的端口、账号和密码覆盖。两种情形均校验了 MySQL、模型 Key 和 HMAC 的现有加载路径。验证只使用虚构配置。

正式切换前暂停识别并等在途请求结束，复查两端同一北京时间自然日的额度键。两端均无当日计数时可当天切换。确认后创建私有文件，使用 IDEA 的 Maven `mac` profile 启动 Mac Java。如果已运行 Mac 后端，安排一次重启。验收时从 Mac 的前端打开识别页面，检查只读额度接口 `GET /api/vision/quota` 和岗位列表，并核对 Mac Java 的 Redis 连接指向 `16379`。如果验证失败且 Linux 还未接收真实扣减，可移出新私有文件后重新启动 Mac Java，恢复本机额度连接。Linux 已记录真实扣减时，按上文处理最新计数。

2026-11-03 用户在 Linux 以管理账号只读检查 DB 0，确认当日额度键数为 0。获准切换后，确认 Mac 正常后端端口 `8082` 无监听，复查 Mac 本机当日键数为 0、Linux 当日全站键不存在、转发认证返回 `PONG`。已创建 Git 忽略的 Mac 私有 Redis 配置，权限 600。当天没有额度需要搬运。

使用现有 `mac` 构建产物和 Java 17 启动临时回环探针，确认实际 Spring 配置使用 `13306` 与 `16379`。HTTP 健康为 UP/UP，匿名岗位接口返回 401。`VisionQuotaService.query` 使用虚构 IP 查询真实 Redis，返回已用 0、剩余 30。探针以只读方式验证真实连接，使用临时端口且关闭模型，完成后自动退出。用户随后可通过 IDEA Maven `mac` profile 启动常规后端，登录后核对岗位列表和识别页额度。常规实例的浏览器验收单独记录。
