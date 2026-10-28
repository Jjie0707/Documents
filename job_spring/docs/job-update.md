# 阶段 3 · 第五个小任务：编辑岗位基本信息后端接口

2026-10-28 增量：原 PUT 已在[阶段 5 的 5.1](job-description-fields.md)扩展为完整六字段，新增 responsibilities、requirements，选填且各最多 10,000 Unicode 码点；缺省/null/空白清空两项，未开放字段保持。开发验证完成、待本项用户验收。下方四字段约定为阶段 3 历史记录，当前契约以该增量说明为准。

2026-10-20，用户确认详情页状态操作验收完毕并继续阶段 3。
本次只实现四个基本字段的编辑接口，沿用 common、controller、service、advice、mapper 分包。
不修改表结构；网页编辑表单、删除、搜索、状态筛选与分页另做。

2026-10-20，用户已确认本次编辑接口验收通过。后续网页接入见[第六个小任务](job-edit-form.md)，
本页保留接口交付记录，页面验收另行记录。

## 接口约定

`PUT /api/jobs/{id}`：要求登录会话、有效 CSRF Token，以及 JSON 请求体。

```json
{
  "companyName": "虚构公司",
  "jobTitle": "Java 后端测试岗位",
  "city": "测试城市",
  "salary": "面议"
}
```

| 字段 | 规则 |
| --- | --- |
| companyName | 必填，去除首尾空白后非空，最多 200 个 Unicode 码点 |
| jobTitle | 必填，去除首尾空白后非空，最多 200 个 Unicode 码点 |
| city | 选填，最多 100 个 Unicode 码点 |
| salary | 选填，最多 100 个 Unicode 码点 |

本项目当前 PUT 的编辑范围就是这四个基本字段，每次提交它们的完整目标值。
**city/salary 省略、null 或只有空白都表示清空原值，不表示保持不变。**
公司名或岗位名省略/null/空白会拒绝整个请求，不会清空记录。
状态仍由独立 PATCH 接口修改；主键、所属用户及职责等其他业务字段不参与本次更新。
多余的 JSON 字段被忽略，即使传入 userId、id、status 也不会修改对应列。
本接口只更新已有岗位，未找到时不自动新增。

| HTTP 状态 | 含义 |
| --- | --- |
| 200 | 更新成功，包括重复提交相同内容；返回 JobResponse，Cache-Control: no-store |
| 400 | 字段不合法、JSON 无法读取、路径 ID 非整数或超出 Long 范围 |
| 401 | 未登录且 CSRF 检查已通过 |
| 403 | CSRF Token 缺失或无效，未登录时也可能先返回此状态 |
| 404 | 岗位不存在或属于其他用户，两者响应一致 |
| 503 | 数据库访问失败，返回通用提示，不返回底层异常 |

业务错误返回 code、message、fields；字段校验错误时 fields 中给出对应字段提示。
Security 的 401/403 沿用既有响应结构。错误不回显用户输入、数据库连接信息或异常细节。

## 执行顺序与关键代码

```text
PUT JSON + 会话 Cookie + CSRF Token
  → Security 检查登录及 CSRF
  → @RequestBody 将 JSON 构造为 UpdateJobRequest，先规范化四个字符串
  → @Valid 校验规范化后的 DTO
  → JobUpdateController 从路径取得岗位 ID，从登录身份取得用户 ID
  → JobUpdateService 在事务内执行带归属条件的 UPDATE
  → JobReadService 按同一岗位 ID + 用户 ID 回读
  → 转成 JobResponse，返回已保存结果
```

1. `common/UpdateJobRequest.java` 是独立的 record 请求 DTO。紧凑构造器先 strip，空字符串转 null，
   然后由 Bean Validation 校验。`@NotBlank` 拒绝必填项为空，`@CodePointLength` 控制字符数。
   单独定义编辑 DTO，允许未来新增与编辑规则分别调整；不把数据库实体直接当作请求体。
   重写 toString，避免直接打印 DTO 时带出岗位内容。
2. `controller/JobUpdateController.java` 负责 HTTP 参数、校验入口、当前身份和响应。
   `@AuthenticationPrincipal AppUserDetails` 来自 Security 当前登录上下文，用户 ID 不取自请求。
3. `service/JobUpdateService.java` 负责更新规则；复用现有 JobMapper，无需增加 SQL XML。
   LambdaUpdateWrapper 用实体 getter 引用定位字段，明确列出 WHERE 与 SET。
4. 城市、薪资使用 `.set(..., null)` 可实际写成 SQL NULL。
   若直接把含 null 的实体传给常见的实体更新方法，默认字段策略可能跳过这些值，导致选填项无法清空。
5. `@Transactional` 覆盖更新和回读，异常会回滚。这不是只读后再无条件写入：UPDATE 自身就限制归属。
   重复提交相同数据也应成功；影响行数可能表示实际变化数，不能仅凭 0 就判断岗位不存在。
   因此按相同归属条件回读，存在即返回，不存在/不属于当前用户则统一 404。
6. `advice/JobUpdateExceptionHandler.java` 只处理本 Controller 的参数、404 和数据库错误，
   Controller/Service 无需在每个分支重复拼接错误 JSON。

等价的更新 SQL：

```sql
UPDATE job_applications
SET company_name = ?, job_title = ?, city = ?, salary = ?
WHERE id = ? AND user_id = ?;
```

状态、归属、其他业务列不在 SET 中，保持原值；MySQL 的 updated_at 仍由数据库自身规则维护。
事务保证这次操作的原子性，但没有实现版本号或冲突检测；同一用户并发编辑相同字段时以后执行的更新为准。
普通 PUT 不会消耗 CSRF Token；当前会话 Token 可复用，登录或退出后重新获取。

## 本机手动检查（用户已确认通过）

1. IDEA 使用 JDK 17，重启后端（8082）；保留既有私密配置，无需重建表。
2. 通过 [auth.http](http/auth.http) 登录临时账号 A，通过 [jobs.http](http/jobs.http) 新增虚构岗位。
   如果网页创建，请把测试岗位 ID 填到 [job-update.http](http/job-update.http) 的 jobId。
   HTTP Client 与浏览器不共享 Cookie，需分别登录；请求都使用同一主机名。
3. 先记录原状态，最好通过已验收的状态功能改为“面试中”，便于检查编辑不会重置状态。
4. 执行 job-update.http 请求 1、2，应返回 200、原 ID、去除首尾空白的四个值，原状态不变。
   重复请求 2 仍应 200；请求 3、4 回读，确认列表与详情一致，岗位数量不增加。
5. 请求 5 清空城市和薪资，再回读确认两个字段为空；数据库应为 NULL。
   先执行请求 2 恢复非空值，再执行请求 6，确认省略选填项也会清空。
6. 请求 7、8、9 应依次返回 400、403、404，之后回读确认失败请求未修改数据。
   可再检查岗位名为空和超长字段返回 400、非法岗位 ID 返回 400。
7. 保留 A 的岗位 ID，退出 A、登录临时账号 B，重新获取 Token 后尝试编辑 A 的岗位，应返回 404。
   A 再登录回读，确认内容没有被 B 改动。userId 查询参数或 JSON 字段也不能绕过归属限制。
8. 未登录但使用有效匿名 CSRF Token 时 PUT 应为 401；缺少 Token 可能先为 403。
9. 浏览器登录 A，刷新列表和详情，可看到接口修改结果。本页接口交付时尚无网页编辑入口，
   后续已接入[网页编辑表单](job-edit-form.md)，页面检查另行记录。

只使用虚构岗位和临时账号；密码、哈希、Cookie、Token、真实用户或岗位内容不写入代码或提交 Git。

## 开发验证与用户验收的区分

- 新增集成测试在独立 H2 内存库中执行并回滚，凭据运行时随机生成，不向用户 MySQL 写入业务数据。
- 覆盖四字段更新、规范化、选填项真正清空、长度边界、重复提交、其他列保持不变、列表/详情回读、
  越权与伪造字段、非法参数/JSON/ID、匿名访问、CSRF 缺失及无效。
- MVC 测试模拟数据库异常，检查通用 503，不暴露异常细节。
- 本次只改后端，未改前端、未重复执行前端构建或浏览器联调。
- 用户已确认本次编辑接口验收通过；该结论与上述 H2 自动化结果分别记录，不扩展为网页编辑的验收结论。

2026-10-20 验证结果：Java 17 下新增 26 项测试通过，全量 107 项测试全部通过，
0 失败、0 错误、0 跳过。全量包含当前分包下的注册、认证、新增、状态和只读接口回归。
验证使用离线 Maven 缓存与 JVM 启动时加载的 Mockito 测试代理，没有修改 pom.xml 或私密配置。
`git diff --check` 和新增文件空白检查通过。未操作域名/隧道或提交 Git。
