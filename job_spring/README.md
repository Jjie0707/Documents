# 岗位投递与面试记录

用 Vue 和 Spring Boot 记录岗位投递、面试轮次与问题。上传招聘截图可生成岗位草稿，确认后再保存。

## 运行方式

| 运行位置 | 浏览器入口 | 请求链路 |
| --- | --- | --- |
| Mac 开发 | [localhost:5173](http://localhost:5173/) | Vite → Mac Java `8082` → SSH 转发 → Linux MySQL、Redis |
| Windows 开发 | [localhost:5173](http://localhost:5173/) | Vite → Windows Java `8082` → SSH 转发 → Linux MySQL、Redis |
| Windows 域名入口 | [job.jwilu.com](https://job.jwilu.com/) | Cloudflare Tunnel → Windows Vite `5173` → Windows Java `8082` |
| Linux 服务器 | `http://Linux公网地址:5175/` | Nginx 提供构建后的前端，`/api/` 转给 Linux Java `8082` |

Java 通过 MySQL 保存业务数据，通过 Redis 共享识别额度。Linux Java 直接连接本机回环地址的 MySQL `3306` 和 Redis `6379`。Maven profile 决定 Spring 加载哪份外置配置，各入口沿用同一套业务接口。 [基础配置](src/main/resources/application.yaml) · [Nginx 转发规则](deploy/linux/nginx/job-tracker.locations.conf)

## Mac 和 Windows 启动

需要 JDK 17、项目自带的 Maven Wrapper、Node.js 24 LTS 和 SSH 客户端。下文的项目根目录是包含 `pom.xml`、`config/` 和 `frontend/` 的目录。

1. 准备 `config/` 中的私有凭据。Mac 使用 `database-mac-local.yaml` 和 `redis-mac-local.yaml`，Windows 使用 `credentials-windows-local.yaml`。详细格式和账号准备见 [Mac 接入说明](docs/stage6-mac-datasource.md) 和 [Windows 接入说明](docs/stage6-windows-datasource.md)。
2. 在 Mac 终端或 Windows PowerShell 启动 SSH。把 `user@your-linux-host` 替换为已有 SSH 登录账号和服务器地址，连接后保持窗口开启。`13306` 和 `16379` 是当前设备的回环监听端口，转发目标是 Linux 上的 `3306` 和 `6379`。`ServerAliveInterval` 用于空闲时保活。

   ```bash
   ssh -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -o ServerAliveCountMax=3 -L 127.0.0.1:13306:127.0.0.1:3306 -L 127.0.0.1:16379:127.0.0.1:6379 user@your-linux-host
   ```

3. 在 IDEA 的 Maven Profiles 只选对应的 `mac` 或 `windows`，执行 Maven `compile`，再启动 `JobSpringApplication`。运行配置的工作目录设为项目根目录。Maven 编译将 `@profile.name@` 替换成选中的环境名。 [Maven 配置](pom.xml) · [Java 入口](src/main/java/jjie/job_spring/JobSpringApplication.java)
4. 另开终端，在项目根目录执行以下命令。`npm ci` 按锁文件安装依赖，首次运行或锁文件更新后执行。Windows PowerShell 可将 `npm` 写成 `npm.cmd`。

   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

5. 打开 [本地页面](http://localhost:5173/)。[本地健康检查](http://localhost:5173/api/health) 返回 `UP/UP` 时，前端代理和 MySQL 连接可用。登录后在识别面板刷新额度，验证 Redis 读取。

关闭时，在 IDEA 停止 Java，在前端和 SSH 的终端分别按 `Ctrl+C`。当前 Windows 域名入口使用 Vite 开发前端，访问依赖 Windows 上的 Vite、Java、SSH 转发和 Cloudflare Tunnel 持续运行。 [域名验收记录](docs/stage6-windows-datasource.md#windows-域名入口)

### 使用独立的本地开发库

需要与共享数据隔离时，使用独立的 MySQL 和 Redis 实例，并取消三个部署 profile。只在**全新的隔离 MySQL 实例**中执行 [初始化 SQL](docs/sql/001_init.sql)，它会创建 `job_tracker` 和四张业务表。

在 Git 忽略的 `config/application-local.yaml` 配置本机实例，再按上面的 IDEA 和 Vite 步骤启动。下面使用默认端口，按实际情况替换端口和本地开发账号。

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/job_tracker?characterEncoding=UTF-8&connectionTimeZone=Asia/Shanghai
    username: '填入本地开发账号'
    password: '填入本地开发密码'
  data:
    redis:
      host: 127.0.0.1
      port: 6379
```

## Linux 启动与关闭

已部署服务器在 `/opt/job-tracker` 运行 JAR，Nginx 在 `5175` 提供页面，Java 由 `job-tracker.service` 管理。在 Linux 的 root SSH 终端执行以下命令。启动后，健康检查返回 `UP/UP` 即可关闭 FinalShell，网页继续可访问。

```bash
systemctl start job-tracker
curl -fsS http://127.0.0.1:5175/api/health
```

停止 Java 后，Nginx 仍能提供静态页面，登录和数据接口暂停。`ss` 无输出表示 `8082` 已释放。若 systemd 把已确认的正常停止记录为退出码 `143`，可用 `systemctl reset-failed job-tracker` 清除状态标记。

```bash
systemctl stop job-tracker
ss -Hltn 'sport = :8082'
```

Linux 更新时用 `-Plinux` 构建 JAR，前端用 `npm run build` 生成 `frontend/dist/`。切换前校验发布包，备份旧 JAR、配置、systemd 单元和静态文件。回退按发布脚本输出的 `--rollback` 命令执行，恢复程序和原运行状态。共享库和额度计数按现有状态继续使用。 [Linux 发布及回退说明](docs/stage6-linux-datasource.md#新版前端与图片识别发布)

## 图片识别与私有配置

- `config/vision-model-local.yaml` 保存模型启用开关和 API Key，默认模型为 `glm-4.1v-thinking-flash`。
- `config/vision-quota-local.yaml` 保存额度 HMAC 密钥。共用 Redis 的识别入口应使用同一个 HMAC。默认每 IP 每天 30 次、全站每天 300 次，北京时间零点重置。
- 私有 YAML 在设备端配置，保留 Git 忽略规则和文件读权限。发布包使用编译产物和公开配置。 [忽略规则](.gitignore) · [模型与识别流程](docs/vision-model.md) · [共享额度配置](docs/stage6-redis-preflight.md)

## 验证顺序

在隔离环境使用虚构数据。前端在 `frontend/` 下运行 `npm test` 和 `npm run build`。后端测试用独立工作目录、H2 和临时 Redis，执行方式见 [Linux 开发验证](docs/stage6-linux-datasource.md#开发验证)。

需要演示岗位列表、面试复盘和账号隔离时，按说明在独立的 `job_tracker_demo` 库导入样例。 [演示数据与操作步骤](docs/stage6-demo-data.md)

实际部署先查首页和 `/api/health`，再在浏览器登录、查看列表、刷新额度。模型验收用一张虚构招聘截图，确认生成草稿且只扣一次额度。
