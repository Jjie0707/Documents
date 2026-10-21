# 阶段 3 · 第十一个小任务：状态筛选接口与页面

当前列表响应已于阶段 3 改为分页对象，详见[分页接口与页面](job-pagination.md)；本页数组响应与未分页描述保留为当时交付记录。

2026-10-21，用户确认上一次网页关键词搜索验收完成，并授权一个小任务同时完成后端接口与 UI，方便直接在页面验收，不再新增 HTTP 文件。
本次实现状态筛选及其与关键词搜索的组合。分页留到下一个小任务，面试功能仍属于阶段 4。
开发验证已完成；本次用户本机 MySQL、实际域名与真实手机验收尚待确认。

## 目标、文件与数据流

列表增加“投递状态”下拉框，选择后点击搜索或回车提交；“全部状态”不限制状态。
关键词和状态同时填写时取交集。“重置条件”同时清空两项，恢复全部岗位。
筛选只读取列表，不修改岗位已保存的状态。

| 文件 | 职责 |
| --- | --- |
| `job/common/JobListQuery.java` | 列表查询参数，包含可选关键词与可选 JobStatus 枚举 |
| `job/common/JobStatusQueryConverter.java` | 将 URL 中的状态字符串转换为枚举，空白转换为 null |
| `job/controller/JobReadController.java` | 复用已有 GET 与 @Valid @ModelAttribute，不另加接口方法 |
| `job/service/JobReadService.java` | 在当前用户条件之外按需增加状态等值条件，组合关键词查询 |
| `job/advice/JobReadExceptionHandler.java` | 对状态绑定失败返回固定字段提示，不暴露原输入或异常细节 |
| `JobStatusFilterIntegrationTests.java` | 状态、组合条件、输入边界及用户隔离的 26 项集成测试 |
| `frontend/src/views/JobsView.vue`、`frontend/src/style.css` | 下拉框、条件提交与重置、结果条件、错误提示和窄屏排列 |

Java 文件保持现有 `jjie.job_spring.job` 包划分，没有引入新的业务分层或依赖，也没有改数据库结构。

```text
选择状态 / 输入关键词（草稿，不查询）
 → 点击搜索或回车 → 保存本次提交的两项条件
 → GET /api/jobs?keyword=Java&status=INTERVIEWING
 → Security 校验登录 → MVC 绑定 JobListQuery，转换 status 并校验 keyword
 → Controller 从登录身份获取 userId
 → Service：当前用户 AND 指定状态 AND（公司 OR 岗位 OR 城市包含关键词）
 → Mapper 参数绑定查询 → JobResponse 数组 → 页面展示结果与已生效条件
```

## 接口约定

- 继续使用 `GET /api/jobs`，返回数组和 ID 倒序不变，不引入分页响应。
- `keyword` 保持此前去首尾空白、最多 100 个 Unicode 码点、包含搜索及 LIKE 字面转义约定。
- `status` 缺省、空串、纯空白都表示全部状态；有效代码允许首尾空白，会先 strip。
- 非空状态只接受既有七种大写枚举代码，详见[状态修改接口](job-status.md)。不接受中文标签、数字、错误大小写或未知代码。
- 非法状态返回 400，`code=INVALID_REQUEST`，`fields.status=请选择有效的投递状态`；不会把原始非法内容返回页面。
- 无匹配返回 200 和 `[]`；未登录仍为 401。传入伪造 userId 不能改变查询归属。
- 这是只读 GET，无需请求 CSRF Token；筛选不会使现有 Token 失效。

例如 `?keyword=Java&status=INTERVIEWING` 只返回当前用户处于面试中、且三个搜索字段之一含 Java 的岗位。
`?keyword=Java` 表示所有状态中的 Java 岗位；仅 `?status=INTERVIEWING` 表示所有面试中岗位。

## 本次需要理解的后端代码

### 1. 用类型表达输入，而不是到处传字符串

`JobListQuery` 使用 `JobStatus status`。转换成功后，Service 收到的只可能是合法枚举或 null，
不必反复判断字符串是否合法。null 表示“不加状态条件”，不能默认成 TO_APPLY，否则未筛选时只能看到待投递岗位。
关键词保留 compact constructor 的统一规范化以及 @CodePointLength 校验。

### 2. GET 参数转换与 JSON 反序列化是不同入口

`JobReadController.list` 的 `@ModelAttribute` 绑定 URL 查询参数；Spring MVC 使用 Converter 转换字段。
`JobStatusQueryConverter` 实现 `Converter<String, JobStatus>`，通过 @Component 让 Spring Boot 注册到 MVC 转换体系。
`convert` 先 strip，空白返回 null，否则 `JobStatus.valueOf(value)` 按精确枚举名称查找。

已有 `JobStatus` 上的 @JsonCreator 负责 JSON 请求体，例如状态修改 PATCH；它不负责 GET 查询参数。
因此本次单独增加 MVC Converter，没有放宽此前 JSON 状态接口的输入规则。
未知代码转换失败时属于“绑定失败”；合法类型上的长度约束等属于“校验失败”。二者都会在调用 Service 前处理。
前端下拉框只是一种输入辅助，后端仍必须阻止手工构造的非法请求。

### 3. 先固定用户边界，再叠加可选业务条件

重点阅读 `JobReadService.listForUser`：

```java
.eq(JobApplication::getUserId, currentUserId)
// status 非 null 才追加：
conditions.eq(JobApplication::getStatus, query.status().name());
```

userId 来自登录身份，不来自前端。`.name()` 得到数据库保存的稳定代码，如 INTERVIEWING，
不使用中文展示名，也不使用枚举序号。状态用等值匹配，关键词仍用包含匹配。

最终条件必须是：

```text
user_id = 当前用户
AND status = 指定状态（可选）
AND (公司匹配 OR 岗位匹配 OR 城市匹配)（可选）
```

状态条件放在关键词 OR 分组外，不能写成没有括号的一串 AND / OR；否则可能绕过状态或用户限制。
已有 SQL 片段固定、值通过参数绑定、LIKE 中 % / _ / ! 按字面转义的处理继续保留。

### 4. 异常出口提供业务提示

`JobReadExceptionHandler.invalidQuery` 从字段错误中识别 status，统一返回固定提示。
转换异常默认消息可能携带原输入、Java 类型或内部异常，不能直接交给浏览器。
其余绑定失败也使用固定提示，合法校验约束继续用既定业务消息。
成功无匹配和请求失败保持不同语义，页面不会把错误伪装成“0 条匹配”。

## 必要的前端数据流

两项条件都区分草稿、已提交条件、成功结果对应条件。输入但未搜索，不改变当前结果标签。
URLSearchParams 编码非空条件，复用 request 发 GET，不获取 CSRF Token。
重试使用已提交的两项条件；“搜索”才提交当前草稿。“重置条件”和“查看全部岗位”清除两项条件。
请求编号继续确保晚到的旧响应不能覆盖新的查询结果，离页后旧响应不能更新页面。
字段错误只关联到仍对应本次请求的草稿，成功重试会清除相应提示。

条件只保存在当前列表组件，刷新或离开列表再返回会恢复全部；没有写入 URL 或浏览器存储。
数量仍是完整匹配数组的长度，尚未分页。

## 用户手动验收（待确认）

1. 使用 JDK 17 重启后端，前端保持运行并刷新。用虚构岗位准备至少两种不同状态，其中两种都包含同一个关键词。
2. 关键词留空，选择“面试中”后点搜索，应只显示自己的面试中岗位；试另一状态。仅改变下拉框、不提交时，结果不应变化。
3. 输入共有关键词并保留一种状态，点搜索或回车，应只显示同时满足两项的岗位；结果下方标明关键词和中文状态。
4. 将状态切回“全部状态”并搜索，应保留关键词；点“重置条件”，应同时清空关键词和状态，恢复全部列表。
5. 提交无匹配组合，应出现“没有匹配的岗位”；“查看全部岗位”恢复全部。筛选后打开详情，岗位原有状态不应因筛选而改变。
6. 手机访问现有域名，检查输入、状态选择、两个按钮均可操作，无横向溢出。刷新恢复全部；可选用另一账号确认看不到原账号记录。

只使用临时账号与虚构数据，不把密码、Cookie、密钥或真实用户数据写进代码或 Git。本轮未新增 HTTP 文件。

## 2026-10-21 开发验证（不等同用户验收）

- 后端全量 172 项测试通过，0 失败、错误或跳过；其中新增状态筛选集成测试 26 项。
- 新测试覆盖七种状态分别筛选、七种与关键词组合、排序、空白状态、首尾空白、非法大小写/数字/代码、错误消息不回显、无匹配、伪造 userId、跨用户隔离和未登录。
- 前端生产构建通过，未新增依赖。
- 浏览器连接独立 H2，通过临时账号与 14 条虚构岗位验证七种状态各返回两条，关键词组合返回一条；全部状态保留关键词，重置清空两项，无匹配提示正确。
- 模拟 400 状态字段错误，焦点与提示正确，恢复后重试清除提示；模拟 503，修改草稿后重试仍使用原提交条件，再搜索才应用新草稿。
- 延迟旧状态查询，新状态查询先返回；旧响应晚到未覆盖结果。相同条件加载中搜索按钮禁用，切换条件可提交新查询。
- 1280px 桌面布局与 375px 窄屏布局已观察；窄屏完成组合查询，无横向溢出。
- `git diff --check` 与新增文件行尾空白检查通过；临时测试服务和标签页已关闭，测试视口已恢复。

开发联调仅使用本机临时端口和隔离 H2，没有向用户 MySQL 写入业务数据，没有改动域名或隧道，没有提交 Git。
上一个网页关键词搜索任务已由用户确认验收；本次状态筛选的本机 MySQL、实际域名和真实手机验收仍待用户确认。
