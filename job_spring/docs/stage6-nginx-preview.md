# 阶段六 Nginx 与前端入口准备

2026-11-03 Linux Nginx 回环预览已通过服务器验收，用户确认 Mac 浏览器经 SSH 能打开页面。回传结果覆盖页面可达性。用户回传的现有 Nginx 配置摘要包含 3 条 listen 指令，其中 2 条带默认站点标记，公网 IP 显式站点名匹配数为 0。Java 常驻服务监听 `127.0.0.1:8082`。

## 文件与链路

```text
Mac 浏览器 → SSH 本地转发 → Linux 127.0.0.1:18080 的独立 Nginx
    /、/assets/、/fonts/ → 前端 dist
    /api/                → Linux Java 127.0.0.1:8082
```

预览包位于本机 `target/linux-delivery/job-tracker-web-preview.tar.gz`，SHA-256 为 `1c775cfe433808d7ff9ce1df3ff675c871e8c43bb6f5339e226d1e967d835464`。包内为构建后的前端及两个 Nginx 配置，正式库和私有凭据仍留在服务器原位置。 [独立预览配置](../deploy/linux/nginx/job-tracker-preview.conf) · [静态与反代配置](../deploy/linux/nginx/job-tracker.locations.conf)

- Vue Router 使用 history 路由，`try_files` 给页面路径回退到 `index.html`。入口 HTML 每次重验证，带内容摘要的 assets 长期缓存，固定路径的字体缓存一天。缺失资源返回 404。
- `proxy_pass http://127.0.0.1:8082` 保留原 `/api/` 路径，使前端、会话和 CSRF 都使用同一个浏览器源。关闭 API 缓存、请求和响应缓冲。代理使用 HTTP/1.1 传递请求体，外层体积上限 6 MiB 与 Spring 一致，代理读取间隔 130 秒覆盖前端最长 120 秒的识别等待。 [Nginx 反代参数](https://nginx.org/en/docs/http/ngx_http_proxy_module.html)
- 反代覆写 `X-Forwarded-For` 为 Nginx 所见的客户端地址。Linux 仍使用当前模型关闭状态，公网真实 IP 和信任代理在额度切换时单独验收。

## 私有预览执行与回退

确认 Linux `18080` 空闲后，上传预览包并校验摘要。解包到独立的 `/opt/job-tracker-nginx-preview/`。静态文件和配置由 root 管理，`www-data` 可读。仅该目录的 `run/` 由 `www-data` 写入，用于独立 PID 和临时目录。

将预览包和启动脚本上传到 Linux 的 `/root/`，在 root SSH 终端执行 `bash /root/start-linux-nginx-preview.sh`。脚本在校验包摘要和 Java 健康后准备目录，使用 `nginx -t -p /opt/job-tracker-nginx-preview/ -c job-tracker-preview.conf` 校验独立配置，再以 `www-data` 启动临时单元 `job-tracker-nginx-preview.service`，监听回环 `18080`。预览单元最多运行 30 分钟。已有 Nginx 主进程继续服务其现有站点。 [预览启动脚本](../scripts/start-linux-nginx-preview.sh)

在 Linux 通过回环验收首页、深链接、哈希资源、缺失资源 404、健康接口 UP/UP、匿名岗位接口 401。需要在 Mac 浏览器查看时，单独建立本地 SSH 转发，随后访问 `http://127.0.0.1:18080/`。用只读页面操作验证现有数据展示，真实业务写入和模型调用留到相应验收。

```bash
ssh -N -o ExitOnForwardFailure=yes -L 127.0.0.1:18080:127.0.0.1:18080 root@8.136.39.58
```

回退时停止本次临时单元并关闭本次 SSH 转发。预览文件可先留作诊断，已有 Java、Nginx、MySQL 和 Redis 继续按各自服务管理。

## 公网入口前置条件

阿里云说明内地服务器以 IP 直接对外提供网站服务也需要备案，其代备案系统目前只支持域名备案。当前先走 SSH 私有预览，公网方案以实际备案与接入条件为前提，由用户单独确认。 [阿里云网站备案要求](https://help.aliyun.com/zh/icp-filing/basic-icp-service/product-overview/icp-filing-requirements-for-a-regular-website)

公开访问满足接入条件后，先核对现有 80/443 站点、阿里云安全组及主机防火墙。拟新增独立站点，以 `/var/www/job-tracker/current` 作为静态根目录，复用 `/api/` 反代配置。HTTP 只提供证书验证路径和 HTTPS 跳转，登录与会话走 HTTPS，HTTPS 站点给 `JSESSIONID` 加 `Secure`。Let's Encrypt 支持短期 IP 证书，若最终选 IP HTTPS，需核对证书客户端版本、80 验证路径、自动续期和续期后重载。部署前备份相关站点配置，`nginx -t` 通过后重载并核对旧站点及新入口。回退时恢复本次站点文件及链接，重新校验后重载。 [IP 证书](https://letsencrypt.org/2026/01/15/6day-and-ip-general-availability) · [Certbot IP 证书](https://letsencrypt.org/2026/03/11/shorter-certs-certbot) · [HTTP-01 验证](https://letsencrypt.org/docs/challenge-types/)

## 当前验证

前端重新执行 26 项测试和生产构建，均通过。打包检查确认 HTML 引用的构建文件、字体和许可存在。

用户在 Ubuntu 的 Nginx 1.28.3 上执行预览脚本，包摘要和 `nginx -t` 均通过。临时单元实际监听 `127.0.0.1:18080`，首页与深链接返回相同的入口 HTML，JS 与字体内容匹配，缺失资源返回 404。经反代的健康接口为 `UP/UP`，匿名岗位接口为 401。用户随后确认 Mac 浏览器通过短时 SSH 转发可以访问页面。该路径使用 Linux Java，Mac 本地 Java 作为另一条入口。

## 5175 站点连通性

2026-11-03 用户在 Linux 主 Nginx 启用独立站点 `/etc/nginx/sites-enabled/job-tracker-5175`，监听 IPv4 TCP `5175`。静态根目录为 `/opt/job-tracker-nginx-preview/frontend`，复用同目录的 `job-tracker.locations.conf`，将 `/api/` 转发至 `127.0.0.1:8082`。启用前检查未发现 `8080` 的监听或匹配的代理指令，因此本次只新增站点。该站点依赖已解压的前端文件及规则文件。

用户回传 `nginx -t` 通过、站点已加载、本机首页 HTTP 200、健康接口 UP/UP，随后确认外网可访问。此处记录 HTTP 连通性验收。回退时移除该站点的启用链接，运行 `nginx -t` 并重载 Nginx。如本次开放了云安全组 `5175`，同时撤回该入方向规则。

用户随后停用了 Nginx 的 `80` 端口站点。域名站点和 `default` 的启用链接移入 `/root/nginx-disabled-port80/`。用户回传配置校验通过、`80` 监听检查无输出、`5175` 健康检查仍为 UP/UP。需要恢复时，将两个链接移回 `/etc/nginx/sites-enabled/`，校验后重载。
