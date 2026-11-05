# 阶段六部署验收

## 自动测试与隔离验证

- Linux 发布版本 `f5e9b01` 的 Java、前端与 Maven 源码和当前工作区一致。该次构建通过 715 项后端测试、26 项前端测试，以及 Linux JAR 和前端生产构建。 [Linux 发布记录](stage6-linux-datasource.md#新版前端与图片识别发布)
- 独立 MySQL、Redis 和 Java HTTP 验证通过，覆盖注册登录、重复导入、岗位和面试记录读取以及跨账号访问返回 `404`。用户选择跳过虚构数据的网页演示。 [隔离验证记录](stage6-demo-data.md#验证记录)

## 实机证据

2026-11-04 从客户端只读复查两个公网入口。Windows 域名首页为 HTTPS `200`，Linux `5175` 首页为 HTTP `200`。两边 `/api/health` 均返回 `UP/UP`，匿名 `/api/jobs` 均返回 `401`。Windows 域名当前提供 Vite 开发前端，Linux 提供构建后的静态页面。 [Windows 域名入口](stage6-windows-datasource.md#windows-域名入口) · [Linux Nginx 入口](stage6-nginx-preview.md#5175-站点连通性)

| 入口 | 已确认的用户操作 |
| --- | --- |
| Mac 本机 | 登录、已有岗位、新增、图片识别 |
| Linux `5175` | 新版页面、图片识别、后台启停 |
| Windows 本机与域名 | 本机登录、已有岗位、共享额度和图片识别，Mac 可打开域名，手机用移动网络可访问并完成图片识别 |

## 交付与凭据核对

- README 已整理三端启动、访问、SSH 转发和回退。 [项目 README](../README.md)
- 核对 8 个私有配置路径的 Git 忽略规则、跟踪状态和本机已知 Git 引用历史。用 Mac 上现有的模型、额度、MySQL、Redis 四类凭据做精确匹配，项目文件、已知 Git 对象和 Linux 发布包均未匹配到对应值。核对过程中仅记录检查结果。
- Linux 发布包外层及包内 9 个文件的 SHA-256 均匹配，JAR 激活 `linux` profile，包内排除了私有配置文件。2026-11-05 用户确认已轮换此前截图露出的 Tunnel Token。

2026-11-05 用户确认手机通过移动网络可访问域名并完成图片识别，Windows 图片识别也通过。阶段六当前部署范围验收完成。用户反馈手机经域名识别较慢，暂未采集分段耗时。公网请求包括原图上传和 Windows 后端等待模型结果两段，后续可通过浏览器请求的 Timing 初步区分。
