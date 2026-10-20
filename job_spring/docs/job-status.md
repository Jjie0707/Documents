# 阶段 3 · 第三个小任务：岗位状态修改后端接口

2026-10-20，用户确认开始岗位状态修改后端接口。
新增岗位接口与网页新增表单均已由用户确认验收通过。本次只实现 PATCH 接口，前端下拉菜单另做。
沿用用户现有的 common、controller、service、mapper、advice 分包，不修改数据库表结构。

2026-10-20，用户明确确认本次岗位状态修改接口已验收完毕。
本页保留后端交付记录，后续接入的网页操作见[第四个小任务](job-status-ui.md)，页面验收另行记录。

## 接口约定

`PATCH /api/jobs/{id}/status`，要求登录会话、有效 CSRF Token 和 `application/json` 请求体：

```json
{
  "status": "APPLIED"
}
```

| 状态代码 | 含义 |
| --- | --- |
| TO_APPLY | 待投递 |
| APPLIED | 已投递 |
| WRITTEN_TEST | 笔试中 |
| INTERVIEWING | 面试中 |
| OFFER | 已获 Offer |
| REJECTED | 未通过 |
| WITHDRAWN | 已放弃 |

只接受这些代码的精确字符串，不自动裁剪空白或转换大小写，不接受数字枚举序号。
任意合法状态之间可切换，便于手动修正记录；本次没有新增状态流转限制。
重复设置相同状态返回成功，不新增岗位。请求 DTO 只有 status，其他 JSON 字段被忽略，
不能借此修改主键、归属、公司名、岗位名或其他业务字段。

| HTTP 状态 | 含义 |
| --- | --- |
| 200 | 更新成功，返回当前岗位 JobResponse，Cache-Control 为 no-store |
| 400 | 缺少/null 状态、非法状态或请求体格式错误，以及超出 Long 范围/非整数的岗位 ID |
| 401 | 有有效 CSRF Token，但未登录 |
| 403 | CSRF Token 缺失或无效；未登录时也可能先返回 403 |
| 404 | 岗位不存在或属于其他用户，两者响应一致 |
| 503 | 数据库访问失败，只返回通用提示 |

业务错误返回 code、message、fields。缺少/null 状态时 fields.status 有具体提示；
请求体无法读取、非法状态或 ID 格式错误时使用通用提示，不回显原始输入或异常内容。
Security 的 401/403 沿用既有 code、message 响应。

## 关键代码与数据流

```text
PATCH JSON + 会话 Cookie + CSRF Token
  → Security 检查登录与 CSRF
  → @RequestBody 解析 JSON 和 DTO，其中 JobStatus 转换只接受合法代码
  → @Valid 对转换成功的 DTO 执行 @NotNull 校验
  → JobStatusController：@AuthenticationPrincipal 读取当前用户 ID
  → JobStatusService：在事务中执行限定归属的单字段 UPDATE
  → JobReadService：按岗位 ID + 当前用户 ID 回读，不存在或越权返回 404
  → JobResponse 返回更新后的岗位
```

- `common/JobStatus.java`：枚举限定状态集合。`@JsonCreator` 明确从状态代码解析，避免将数字当作枚举下标。
- `common/UpdateJobStatusRequest.java`：只接收状态，`@NotNull` 拒绝缺省或 null；不将请求直接绑定数据库实体。
- `controller/JobStatusController.java`：使用 `@PatchMapping`，取得路径 ID、请求 DTO 与当前登录身份。
- `service/JobStatusService.java`：通过 LambdaUpdateWrapper 明确指定 WHERE 和 SET；事务覆盖更新及回读。
- `advice/JobStatusExceptionHandler.java`：只处理新 Controller 的有限错误响应。
- `service/JobCreateService.java`：新增时的默认状态改为 `JobStatus.TO_APPLY.name()`，含义不变。

更新语句等价于：

```sql
UPDATE job_applications
SET status = ?
WHERE id = ? AND user_id = ?;
```

必须在 UPDATE 本身加上 user_id 条件，而不仅在页面或查询时检查归属。
公司、岗位名、薪资等业务字段不参与 SET；现有 MySQL updated_at 仍由数据库规则自动维护。
用户 ID 从登录身份取得，不能通过 URL 查询参数或 JSON 切换用户。

重复更新相同状态时，驱动返回的行数口径可能不同。Connector/J 的
[useAffectedRows 设置](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-connection.html#cj-conn-prop_useAffectedRows)
区分匹配行与实际改变行的计数。因此不使用“更新返回 0”作为唯一的 404 判断，
而是在同一事务中按相同归属条件回读。记录存在即可返回当前状态，重复设置不会误报不存在。

本页接口任务交付时没有修改前端；后续已接入[详情页状态操作](job-status-ui.md)，可以在网页选择并保存。
编辑、删除、搜索、筛选和分页仍未实现。

## 本机手动检查（用户已确认通过）

1. IDEA 使用 JDK 17，Reload Maven 并重启后端（8082）；保留本地私密配置，无需重建表。
2. 通过 [auth.http](http/auth.http) 登录临时账号 A，在 [jobs.http](http/jobs.http) 新增一条虚构岗位，
   或通过已完成的网页表单新建并记录 ID。不要使用真实岗位作为本次修改对象。
3. 打开 [job-status.http](http/job-status.http)。默认 jobId 复用 jobs.http 保存的 createdJobId；
   如通过网页创建，手动把 jobId 改成该测试记录的 ID。
4. 执行请求 1 获取登录后的 Token，再执行请求 2。应返回 200、原岗位 ID、APPLIED。
   请求 3 重复提交同一状态仍应返回 200，岗位数量不增加。
5. 执行请求 4、5，确认详情与列表状态为 APPLIED，公司名、岗位名、城市、薪资未被修改。
   浏览器登录 A，刷新列表和详情，状态应显示“已投递”，下拉菜单仍禁用。
6. 将请求 2 的状态依次改为其他合法代码，均应返回 200；可改回 TO_APPLY。
7. 执行请求 6、7、8、9，依次应为 400、400、403、404，之后回读确认状态没有被这些失败请求修改。
   可再将 status 改成 null、小写 applied、带空白的代码或数字 0，应均为 400。
8. 保留 A 的测试岗位 ID。在 auth.http 退出 A、登录临时账号 B，再执行本文件请求 1 更新 Token。
   B 修改 A 的测试岗位应为 404；即使加上 A 的 userId 查询参数或 JSON 字段，也不应成功。
9. A 再次登录回读，确认步骤 8 没有改变状态。检查自己的合法更新只影响这一条岗位。
10. 退出后重新获取匿名 Token，再尝试 PATCH 应返回 401；不带 Token 则可能先返回 403。

HTTP Client 与浏览器不共享登录 Cookie，需分别登录；所有请求保持同一主机名。
普通状态 PATCH 不消耗 Token，可以复用当前有效值；登录/退出后需重新获取。
密码继续通过本地私密环境提供，不要将密码、哈希、Cookie、Token 或真实用户/岗位数据提交 Git。

## 自动化验证与边界

- 新增 H2 集成测试覆盖七种状态及重复提交、其他字段不变、列表/详情回读、跨用户访问、伪造参数、
  缺省/null、非法代码/数字/类型/JSON、路径 ID 错误、匿名访问和 CSRF。
- 使用真实表单登录与会话 Token 完成两次 PATCH，确认正常状态修改不会清除 Token。
- 新增 MVC 测试模拟数据库异常，检查通用 503；Service 单测模拟影响行数为 0 的重复更新，检查成功回读。
- H2 业务测试在独立内存库运行并逐项回滚，新增凭据运行时随机生成，不向用户 MySQL 写入数据。
- H2 与模拟返回值不等同于本机 MySQL 驱动行为，重复更新在用户环境中仍按手动步骤检查。

2026-10-20 验证结果：Java 17 下全部 81 项测试通过，0 失败、0 错误、0 跳过，
其中本次新增 30 项；`git diff --check` 通过。
本轮全量测试包含用户当前分包调整后的注册、认证、新增与只读接口回归。
验证使用离线 Maven 缓存与 JVM 启动时加载的 Mockito 测试代理，没有修改 pom.xml 或私密配置。
本次没有修改前端，未重复执行前端构建或浏览器联调；之前前端表单的用户验收仍是单独记录。
用户已确认本次状态接口验收通过，与上述 H2 自动化结果分别记录。
接口开发验证未操作真实数据、域名/隧道或 Git 提交；状态修改页面的开发与验收单独记录。
