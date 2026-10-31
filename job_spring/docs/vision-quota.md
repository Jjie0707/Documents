# 5.5：Redis 每日识别额度查询

2026-10-31 本次30/300验证：Java17全量706项（0失败/错误/跳过）、JAR通过；真实独立Redis同IP60个请求仅30成功、不同IP360个请求仅300成功。默认查询响应30，读/检查不扣，既有计数按新上限计算；无用户数据库/Redis操作或真实模型调用。前端代码未改，未重跑前端测试/构建或浏览器，用户可重启后端后在原额度面板确认剩余=30-今日已用（不小于0）。


2026-10-31 额度调整：按用户要求，当前默认每IP每天30次、全站每天300次；Java回退值与application.yaml同时更新。保留当天已使用次数，不清空Redis，上海次日零点重置规则不变。重启后端后生效，前端按接口返回值展示。此前5/100及其截图、测试记录为历史值。

2026-10-31 用户已明确确认显示正确、5.5 验收完毕。5.6 当前已开发与自动验证完成，见[原子预占说明](vision-quota-reservation.md)。下方为5.5交付时记录，代理策略及写计数以5.6为准。

## 这次交付

新增页截图区域展示当前 IP 当日剩余次数、已使用次数、额度可用状态和重置时间，并可主动刷新。默认每IP每天 30 次，全站每天 300 次，按 Asia/Shanghai 自然日计算。页面打开/刷新只查询；选择、预览、检查图片均不扣次数。模型识别尚未开放，额度可用不是模型已可调用的承诺。

Redis 未配置、连接失败、命令超时或计数损坏时，显示额度不可用；手动岗位和原图片检查继续可用。不改表、不加 Mapper、不创建 HTTP 文件、不调用模型。

## 接口契约

`GET /api/vision/quota`，要求已登录，未登录 401；只读 GET 无需 CSRF，原写接口 CSRF 保持。响应 `Cache-Control: no-store`。正常查询以及“成功查明额度服务不可用”的快照均返回 HTTP 200；网络请求失败则由前端单独显示“额度查询失败”。

以下为虚构响应示例：

```json
{
  "available": true,
  "reason": "AVAILABLE",
  "dailyLimit": 30,
  "used": 2,
  "remaining": 28,
  "resetAt": "2026-10-31T00:00:00+08:00"
}
```

| reason | available | used / remaining | 含义 |
| --- | --- | --- | --- |
| AVAILABLE | true | 确定数值 | 当前 IP 还有额度 |
| EXHAUSTED | false | 确定数值 / 0 | 已到或超过限额 |
| NOT_CONFIGURED | false | null / null | 密钥不足 32 字节、为空或限额不大于 0 |
| CLIENT_UNAVAILABLE | false | null / null | 无法得到连接来源 |
| STORE_UNAVAILABLE | false | null / null | Redis 或键生成不可用 |
| INVALID_COUNTER | false | null / null | 计数格式非法或超出 long 范围 |

异常时不能把未知次数写成 0，从而误导用户认为剩余完整额度。`available` 仅是 IP 额度快照；未来真正识别必须在服务端重新原子检查 IP 与全站额度。

## Java 执行顺序与职责

1. Spring Security 校验登录，`VisionQuotaController.quota()` 取 `request.getRemoteAddr()`，委托 Service，设置 no-store。Controller 不处理 Redis 或业务判断。
2. `VisionQuotaService.query()` 从注入的 `Clock` 取一次时间，转换上海日期，同时计算次日零点。一次取值避免跨零点时日期键与重置时间不一致；注入 Clock 也让零点测试无需真的等到半夜。
3. `VisionQuotaProperties` 绑定每日上限和服务端密钥。缺配置时提前返回，手动功能仍能启动使用；配置对象 toString 脱敏。
4. `VisionQuotaKeyService.dailyIpKey()` 对“日期 + IP”做 HMAC-SHA256。格式是 `vision:quota:v1:{日期}:ip:匿名摘要`；日期参与摘要，避免不同日期直接使用同一标识。它不是加密后可解密的 IP。密钥不能每日更换或随应用重启随机重建，否则当天会变成新计数桶。
5. `StringRedisTemplate.opsForValue().get(key)` 读取字符串计数。缺 key 按 0；拒绝负数、小数、空白、非数字和溢出；`Math.max(0L, limit - used)` 防止显示负剩余。
6. 返回 `VisionQuotaResponse`，前端 `VisionQuotaPanel.vue` 显示结果。组件挂载和点刷新才发请求；失败不清空手动表单，不禁用图片检查。

`StringRedisTemplate` 由 Spring Boot 的 Redis starter 自动配置，底层客户端为 Lettuce。它与业务 Service 通过构造器注入协作；这里不需要 MyBatis Mapper，因为没有执行 SQL。异常返回固定状态，不附连接异常原文、密钥或 IP。

## Redis 入门：这次到底存什么

可以先把 Redis 理解为一个独立运行、供 Java 读写的键值数据库。Java 进程、Redis 进程、MySQL 进程彼此独立；只加 Maven 依赖并不等于启动 Redis。

本项目的分工如下：

| 存储 | 用途 | 示例 |
| --- | --- | --- |
| MySQL | 长期业务记录及关联关系 | 岗位、面试、用户 |
| Redis | 识别限额计数 | “某日某匿名 IP 已调用 2 次” |

一个 key 类似查询用的名字，value 是它对应的数据。这里 value 为字符串 `"2"`，Java 转为 long 后计算剩余。`GET` 只读，不存在的 key 返回空；本步没有 `SET` 或递增操作，因此网页刷新不会生成计数键。[Redis GET 官方文档](https://redis.io/docs/latest/commands/get/)

TTL 是键还剩多久过期；设置过期时间后 Redis 会自动清除到期键。每日额度将使用“按日期分键 + 次日零点过期”：日期隔离今天/明天的数据，过期负责清理旧数据。本步只计算并显示重置时刻，**尚未写入计数，也不设置 TTL**；5.6 将在原子预占时一并设置过期。已有 key 的普通 GET 不续期。[Redis EXPIRE 官方文档](https://redis.io/docs/latest/commands/expire/)

下一步不能先 GET、在 Java 判断再单独递增：并发请求可能都读到剩余 1 次并同时通过。5.6 会把检查 IP/全站上限、预占和过期作为原子操作处理。本步不提前实现这部分。

## 配置与本机启动

公开 `application.yaml`：Redis 默认 127.0.0.1:6379，连接/命令超时各 1 秒；关闭未使用的 Redis repositories。每日IP上限默认30、全站上限默认300。环境变量为 `REDIS_HOST`、`REDIS_PORT`、`VISION_QUOTA_DAILY_LIMIT`、`VISION_QUOTA_HMAC_SECRET`；Redis 认证可使用 Spring 标准 `SPRING_DATA_REDIS_PASSWORD`，不能放到前端。

本次已安装 Homebrew Redis 8.6.2（以及安装器所需依赖），未启动系统服务或设置开机启动。另生成 `config/vision-quota-local.yaml`，权限 0600，保存本机随机 HMAC 密钥；已加入 Git 忽略并 optional 导入，不读取或覆盖原 `config/application-local.yaml`。该文件在 resources 之外，不进 JAR。不要把内容贴到聊天、文档或提交；另一台机器需独立配置，同一部署的多个后端则须使用一致的密钥和 Redis。

当前 Mac 验收时，在一个终端启动**仅本机、临时无持久化**的 Redis，保持终端开启：

```sh
/opt/homebrew/bin/redis-server --bind 127.0.0.1 --port 6379 --save "" --appendonly no
```

另开终端确认连通：

```sh
/opt/homebrew/bin/redis-cli -h 127.0.0.1 -p 6379 ping
```

返回 `PONG` 表示连接正常。若 6379 已被占用，先检查现有服务，勿为了测试停止或清空未知实例。再从项目根目录按原方式启动/重启 Java 17 后端，确保新的依赖、接口和两份 optional 本机配置被加载；前端 Vite 刷新，部署静态文件则重新构建。

上述命令只用于当前学习验收；没有持久化，退出后计数会丢失。5.6/上线前需明确重启恢复、持久化、淘汰策略及运行方式，不能把此临时命令当作生产限额部署。结束自己刚启动的终端实例可 Ctrl+C，无需执行清库命令。

## 代理边界（5.5 历史规则）

5.6 已增加显式可信代理链解析，默认仍不信任转发头；真实域名的入口约束与配置须结合拓扑核对，见其功能说明。

目前 `server.forward-headers-strategy=none`，只取连接 IP，不读取 X-Forwarded-For/Forwarded/CF-Connecting-IP。伪造这些头不能改变桶。Vite/Tunnel 转发时可能多台设备共用回环或代理地址；当前不能宣称已经按真实公网 IP 完整限额。可信来源、IP 规范化与安全代理链在 5.6 处理，不能简单信任任意转发头。

## 验证记录与网页验收

2026-10-30 中断后复核：5.5 代码、配置、页面、测试和说明均完整；重新对当前工作区执行隔离 H2 全量 608 项后端测试（0 失败/错误/跳过）、JAR、前端 20 项及构建，全部通过。本次未重复浏览器实测；前次真实 Redis/H2 网页记录和截图仍保留，前端重建产物文件名一致。临时测试端口 18085/15175/16385 当前均无监听；本机/域名验收与 Redis 入门说明待交付用户，5.6 未开始。

- 新增 27 项 Java 测试，覆盖只读/不续期交互、配置上限、耗尽/long 边界、坏计数、连接故障、密钥缺失、日期切换、匿名键隔离、未登录拒绝、no-store 和伪造转发头。全量 **608 项，0 失败/错误/跳过**及 JAR 通过；前端原 20 项测试及构建通过。
- 初次离线执行因 Redis 新运行时依赖尚未缓存而失败；联网补齐后全量构建通过。所有全量测试禁用本机配置导入，默认数据源强制 H2，不连接用户 MySQL。
- 独立 Redis 16385 + H2 后端 18085 + 页面 15175：重复查询和图片检查后 Redis 仍为零 key；写入虚构 2 次后显示 3/5，TTL 随时间减少未续期；虚构 5 次显示耗尽；停 Redis 显示不可用且手动保存成功；重启 Redis 后恢复查询。桌面、375px 窄屏通过，无横向溢出。测试进程和标签已清理，未修改用户数据/服务/隧道。

本机仍需验收：

1. 启动上述 Redis、重启后端、刷新网页，登录后打开“添加岗位”。应出现30/30（当日已用次数需扣除） 及北京时间次日零点。
2. 反复“刷新额度”，再选择截图并“检查图片”，次数应保持不变，原预览/检查正常。
3. 在自己刚启动的 Redis 终端 Ctrl+C，再刷新额度，应显示暂不可用；已填写内容保留，可继续手动录入。
4. 重新运行 Redis，刷新额度应恢复；通过自己的域名及手机检查布局、刷新提示。真实 Tunnel/IP 归属仍按上述边界判断。

本机验收结果由用户确认；隔离浏览器不能替代真实域名/真机检查。下一项为 5.6，当前尚未开始。
