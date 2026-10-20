# 阶段 3 · 第一个小任务：最小新增岗位后端接口

2026-10-19，用户确认阶段 2 已验收完毕，并授权进入阶段 3。
本次只实现新增岗位的后端接口，前端表单留作后续小任务。列表和详情继续复用阶段 2 的接口。

截至 2026-10-20，用户已确认当前实现的接口全部验收通过，包含本页新增岗位接口。
本页保留第一个小任务的交付记录；网页新增入口和表单现见[第二个小任务](job-create-form.md)。

## 接口与字段

`POST /api/jobs`，使用 `application/json`，要求登录会话 Cookie 与有效 CSRF Token。

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| companyName | 是 | 公司名，去除首尾空白后 1–200 个 Unicode 字符 |
| jobTitle | 是 | 岗位名，去除首尾空白后 1–200 个 Unicode 字符 |
| city | 否 | 城市，去除首尾空白后最多 100 个 Unicode 字符 |
| salary | 否 | 薪资原文，去除首尾空白后最多 100 个 Unicode 字符 |

选填字段缺省、传入 null 或只有空白时存为 SQL NULL；不会截断超长文本。
请求 DTO 只包含这四个字段，其他 JSON 字段被忽略。客户端传入的 id、userId、status、岗位职责等均不会写入。
归属用户取自服务端登录身份，初始状态固定为 `TO_APPLY`，数据库生成主键和时间字段。
不修改现有表结构，不自动执行 SQL 脚本。

| HTTP 状态 | 含义 |
| --- | --- |
| 201 | 新增成功；响应为 JobResponse，Location 指向 `/api/jobs/{新ID}`，Cache-Control 为 no-store |
| 400 | 必填、长度校验失败，或 JSON 请求体无法读取 |
| 401 | 有有效 CSRF Token，但未登录 |
| 403 | CSRF Token 缺失或无效；即使未登录也可能先返回 403 |
| 503 | 数据库访问失败，只返回通用提示 |

业务错误响应包含 code、message、fields。校验响应只包含字段名与提示，不回显输入内容；
Security 层的 401/403 沿用现有 code、message 格式。
成功响应不包含所属用户 ID、账号密码或数据库异常。
每次成功 POST 都新增一条记录，没有自动去重；手动检查时避免反复执行成功请求。

## 数据流与关键代码

```text
HTTP Client（以后是前端表单）提交 JSON + Cookie + CSRF Token
  → Security：检查会话与 CSRF
  → JobCreateController：@RequestBody 转换请求，@Valid 执行字段校验
  → @AuthenticationPrincipal 提供 AppUserDetails，读取当前用户 ID
  → JobCreateService：构造实体，设置当前用户和 TO_APPLY
  → JobMapper.insert：写入 job_applications，回填自增 ID
  → JobResponse.from：构造对外响应，返回 201
  → GET 列表 / 详情：仍按当前用户读取
```

- `CreateJobRequest` 使用 record 描述入参。紧凑构造器先规范文本，`@NotBlank` 校验必填，
  `@CodePointLength` 按 Unicode 字符数校验上限；toString 不输出岗位内容。
- `JobCreateController` 只处理 HTTP 参数、身份和响应，不从请求 JSON 取得用户归属。
- `JobCreateService` 用明确的 setter 赋值，避免将客户端 JSON 直接绑定数据库实体。
  `@Transactional` 使写入处于事务内，数据库运行时异常时回滚；当前只有一条 INSERT。
- `JobMapper` 复用 MyBatis-Plus BaseMapper。实体已有 AUTO 主键映射，插入后 job.id 可用于响应和详情 URL。
- `JobCreateExceptionHandler` 仅处理新增 Controller 的错误，不影响现有注册与只读接口。

本页后端小任务交付时没有修改前端，通过 API 创建后刷新主页查看新卡片。
后续已接入四字段[网页新增表单](job-create-form.md)；详情状态选择仍禁用。
编辑、删除、修改状态、搜索、筛选、分页、完整岗位字段表单均未实现。

## 本机手动检查（用户已确认通过）

1. IDEA 使用 JDK 17，重启后端（8082），保留本地私密配置。无需重建数据库。
2. 在 [auth.http](http/auth.http) 中选择 local 环境，使用临时测试账号 A 登录。
   凭据继续存放在 Git 忽略的 `http-client.private.env.json`，不要写到本次请求示例中。
3. 打开 [jobs.http](http/jobs.http)，执行请求 1 获取登录后的 Token，再执行请求 2。
   应返回 201、新 ID 和 `TO_APPLY`，Location 应指向新岗位详情地址。
4. 执行请求 3、4：列表包含新岗位，详情四个字段正确；在 IDEA Database 面板核对 user_id 为 A 的 ID。
   浏览器登录同一个 A 账号并刷新主页，应该出现新卡片，点击可进入详情。
5. 需要检查可选字段时，可将成功请求中的 city、salary 删除或改为空白再执行一次。
   这会创建另一条测试记录，数据库对应列应为 NULL。
6. 执行请求 5，应返回 400，fields.companyName 有提示，记录数不增加。
   另将公司名/岗位名改为 201 个字符，或城市/薪资改为 101 个字符，应同样拒绝且不新增。
7. 执行请求 6，应返回 403，记录数不增加。
8. 记下步骤 3 的岗位 ID。通过 auth.http 获取当前 Token、退出 A，再登录另一个临时账号 B。
   B 的列表不包含 A 的新岗位，访问该详情 ID 返回 404。
9. 若需检查伪造参数，B 登录后重新执行 jobs.http 请求 1，在请求 2 中额外加上 A 的 userId、
   `"status": "OFFER"` 和任意 id。成功创建的记录仍属于 B、状态仍为 TO_APPLY，ID 由数据库生成。
10. 退出 B 后，重新获取匿名 Token 再执行新增请求，应返回 401 且不新增；不带 Token 则可能先返回 403。

HTTP Client 需保留同一主机的 Cookie，不要混用 localhost 与 127.0.0.1。
IDEA HTTP Client 与浏览器不共享会话，需要分别登录。
仅使用虚构岗位，禁止提交真实账号、真实岗位、密码、哈希、Cookie 或 Token。
本次没有删除接口，不提供清库或重建库步骤。

## 自动化验证与边界

- 新增集成测试使用独立 H2 内存库，覆盖插入回读、默认状态、归属隔离、伪造字段、
  省略/空白可选项、长度边界、非法参数、坏 JSON、匿名访问及 CSRF。
- 新增 MVC 测试模拟数据库异常，验证 503 不泄露数据库详情或岗位输入。
- 原认证集成测试中“新增尚未开放”的断言，改为验证新增接口拒绝缺少必填字段。
- 新增测试的凭据在运行时生成；H2 业务测试逐项回滚，不向本机 MySQL 写入测试记录。
- H2 不等同于 MySQL，自动化通过不代表本次新增接口已完成用户本机验收。

2026-10-19 验证结果：Java 17 下全部 51 项测试通过，0 失败、0 错误、0 跳过，
其中本次新增 16 项；`git diff --check` 通过。
验证使用离线 Maven 缓存和 JVM 启动时加载的 Mockito 测试代理，避免受限环境中的动态代理附加问题；
没有修改 pom.xml 或本地私密配置。前端代码本次未改，本轮前端生产构建已在进度核对时通过。
用户已确认本页现有接口验收通过；网页新增表单的验收另行记录。
