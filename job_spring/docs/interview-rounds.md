# 阶段 4 · 第一个小任务：新增并查看面试轮次

2026-10-21，用户确认阶段 3 已验收，并确认按约定开始阶段 4 的第一个小任务。
本次同时交付后端接口与岗位详情页 UI，不新增 HTTP 文件。
**状态：初版与[拒绝重复轮次](interview-round-duplicates.md)已由用户验收；[一轮一页](interview-round-pagination.md)和[轮次编辑](interview-round-update.md)开发完成，等待本次验收；阶段 4 整体尚未完成。**

## 目标与边界

在当前用户自己的岗位详情中新增面试轮次，并查看该岗位已保存的轮次。
保留已有页面排列：电脑左侧岗位信息、右侧面试/状态/删除；700px 及以下按同一顺序单列排列。
当前已完成新增、分页查看、编辑轮次及[单轮删除/岗位关联清理](interview-deletion-plan.md)；删除已由用户验收通过；[新增并查看问题](interview-questions.md)和回顾页已由用户验收；[问题编辑/单条删除](interview-question-actions.md)和[详情栏目切换](job-detail-tabs.md)开发完成待验收；问题搜索/主题筛选仍未实现，不涉及图片识别、部署或自动修改岗位状态。
轮次列表已改为一轮一页，详情页提供上一轮/下一轮与编辑本轮；岗位列表既有分页对象与筛选接口保持不变。

开始前通过只读 information_schema 查询核对了本机 MySQL 的相关字段、索引、检查约束与外键。
没有执行初始化 SQL、修改表结构或向用户 MySQL 写入测试业务数据。

## 接口与字段

| 请求 | 行为 |
| --- | --- |
| GET /api/jobs/{id}/interview-rounds | 200 返回 items/page/size/total/totalPages，固定每页 1 条，支持 page 和可选 roundId 定位 |
| POST /api/jobs/{id}/interview-rounds | JSON 新增，201 返回本次保存的轮次 |
| PUT /api/interview-rounds/{id} | 五字段完整目标值更新，200 返回原 ID 的已保存轮次 |

以上接口都要求登录；POST/PUT 还要求有效 CSRF Token。成功响应带 Cache-Control: no-store。
POST 返回 id、roundNo、interviewTime、roundType、result、review，不暴露 userId 或内部实体。
本轮没有轮次详情接口，因此不返回指向未实现详情接口的 Location。

| 字段 | 规则 |
| --- | --- |
| roundNo | 必填，JSON 整数，1～2147483647；拒绝小数、数字字符串、布尔值与超范围数字 |
| interviewTime | 选填，ISO 本地日期时间字符串，例如 2026-10-21T14:30:00；缺省或 null 表示未填写 |
| roundType | 选填，最多 50 个 Unicode 码点，自由文本 |
| result | 选填，最多 100 个 Unicode 码点，自由文本 |
| review | 选填，最多 2000 个 Unicode 码点，支持多行；这是应用层上限，数据库仍为 TEXT |

文本先 strip，选填项缺省、null 或纯空白存为 SQL NULL，不截断超长文本。
时间使用 LocalDateTime，与数据库 DATETIME(3) 一样按 Asia/Shanghai 解释，不进行浏览器时区换算。
时间可以在过去或未来，年份限定 1000～9999；拒绝无效日期、空串、数组及带 Z/时区偏移量的值。
秒可省略，可带小数秒；统一截取到毫秒精度，再插入并返回，保证 POST 与后续 GET 的时间精度一致。
页面输入本地日期时间；只有分钟时补秒为 00，编辑时保留原秒和毫秒。

轮次号手动填写，不自动计算下一轮；按 round_no ASC、id ASC 排序。
数据库没有同岗位轮次号唯一约束。验收修正后，新增事务先锁父岗位并查重，已有同号轮次返回 409，不保存、不覆盖；历史重复记录保留。每次成功 POST 新增一条，不提供幂等键，详见[重复轮次规则](interview-round-duplicates.md)。
多余 JSON 字段被忽略：userId 只来自登录身份，jobApplicationId 只来自 URL，id 由数据库生成。

| 状态 | 含义 |
| --- | --- |
| 400 | 字段校验、JSON 类型、时间或路径 ID 格式错误 |
| 401 | 未登录，且已通过 CSRF 检查；GET 未登录直接 401 |
| 403 | CSRF 缺失或无效；POST 未登录时也可能先得到 403 |
| 404 | 岗位不存在或属于其他用户，两者响应相同 |
| 409 | 同岗位轮次号重复（DUPLICATE_ROUND），或其他关联/完整性约束冲突 |
| 503 | 其他数据库访问失败，返回固定提示 |

业务错误使用 code/message/fields。字段错误不回显输入内容，格式错误不暴露 Jackson、SQL 或数据库异常。
Security 的 401/403 沿用已有处理，不改变 SecurityConfig。

## 初版新增数据流与关键 Java 代码

下方以新增为主；当前分页读取与编辑的执行顺序分别见[分页说明](interview-round-pagination.md)和[编辑说明](interview-round-update.md)。

```text
岗位详情加载成功 → GET 该岗位轮次
用户填写表单 → 现有 post 封装 GET /api/auth/csrf → POST JSON + Cookie + CSRF Token
 → Security 检查会话和 CSRF
 → Jackson 构造 CreateInterviewRoundRequest，按字段反序列化并规范化文本/时间
 → @Valid 检查必填、范围、字符数和日期年份
 → Controller 从路径取岗位 ID，从 @AuthenticationPrincipal 取用户 ID
 → InterviewRoundCreateService.createForUser 开启事务
 → JobMapper.lockOwnedJob 按岗位 ID + 当前用户 ID 锁定归属岗位
 → InterviewRoundMapper.findExistingRoundId 当前读查重；已有同号则返回 409
 → 显式设置新实体的归属、岗位和本次开放的字段
 → InterviewRoundMapper.insert；数据库复合外键检查归属，自增 ID 回填
 → InterviewRoundResponse.from → 201
 → 页面只在 POST 成功后清空表单，再 GET 列表展示数据库结果
```

### 请求 DTO、实体与响应 DTO

CreateInterviewRoundRequest 只接收本次允许填写的五个业务字段；不把请求直接绑定实体。
紧凑构造器将选填空白转成 null、把时间截取到毫秒。@NotNull/@Min 保证轮次有效，
@CodePointLength 按 Unicode 码点计长。isInterviewTimeSupported 限定 MySQL DATETIME 的年份范围；
advice 将这个派生校验属性的错误归到 fields.interviewTime，方便表单展示。
DTO 的 toString 隐藏内容，不在代码中输出复盘输入。

RoundNoDeserializer 先检查 JsonToken.VALUE_NUMBER_INT，再使用 getIntValue。
原因是默认 JSON 转换会把 1.5 截断成 1：如果只在转换后的 Integer 上加 @Min，原始非法输入可能被放过。
InterviewTimeDeserializer 用 ISO_LOCAL_DATE_TIME 严格解析本地时间，避免 LocalDateTime 静默接受带时区语义的输入。
这两个反序列化器仅标注在新 DTO 字段上，不改变其他接口的 JSON 规则。

InterviewRound 是 MyBatis-Plus 实体；InterviewRoundResponse 明确列出外部响应字段，不返回内部归属列。
Mapper 继承 BaseMapper，沿用现有项目方式，无需新的 SQL XML、依赖或全局配置。

### Service 的归属条件与事务

createForUser 在事务内先锁定当前用户的岗位，未找到时统一处理“不存在/别人的岗位”404；然后查重，仅无同号时插入。
readService.listForUser 同样先检查岗位；轮次查询本身还明确包含：

```sql
WHERE job_application_id = ? AND user_id = ?
ORDER BY round_no ASC, id ASC
LIMIT 1 OFFSET ?
```

自己的岗位没有轮次与无权访问不是同一种情况，不能都返回空结果；分页响应的 items 每次最多一条。
用户归属来自服务端身份；前端隐藏按钮或伪造 userId 都不能决定权限。

归属查询给出可理解的 404；数据库 (job_application_id,user_id) 复合外键仍在 INSERT 时保证关联完整性。
@Transactional 遇运行时异常会回滚；父岗位 FOR UPDATE 锁使当前接口对同一岗位的并发新增按顺序查重。
其他数据库完整性约束冲突由 advice 返回通用 409，不能写入悬空轮次；重复异常单独返回字段提示。
2026-10-22 已接通轮次删除及岗位关联清理：保留 RESTRICT，Service 事务先删问题、再删轮次/岗位，普通关联不再阻止岗位删除。
历史同号记录可按记录 ID 逐条编辑或删除，不自动清理；请使用专用虚构记录，详见[删除与回滚说明](interview-deletion-plan.md)。

### 文件职责

新增 Java 文件均在 jjie.job_spring.interview 下：

- common：InterviewRound、CreateInterviewRoundRequest、InterviewRoundResponse、RoundNoDeserializer、InterviewTimeDeserializer。
- controller：InterviewRoundCreateController、InterviewRoundReadController，负责 HTTP 参数、身份与状态码。
- service：InterviewRoundCreateService、InterviewRoundReadService，负责归属检查、写入和查询规则。
- mapper：InterviewRoundMapper，执行数据库操作。
- advice：InterviewRoundExceptionHandler，集中处理新接口异常，避免影响已有业务。
- exception：DuplicateInterviewRoundException，表达重复轮次业务冲突。

未重构既有 job、auth 或 Security 分包。

## 必要的前端数据流

JobDetailView.vue 把当前岗位 ID 和其他操作锁定状态传给 InterviewRoundsPanel.vue。
轮次组件管理表单草稿、已保存列表、加载与保存状态；复用 api.js 的 request/post，无需修改 API 封装或增加路由。

- 首次加载、重试与保存成功后均 GET 列表；请求失败显示错误，不伪装成“暂无轮次”。
- 400 字段错误保留草稿并聚焦首个错误字段；其他明确失败也保留草稿。401 清除页面身份并转登录；404 通知详情页隐藏旧岗位。
- 保存中禁止重复提交，并锁定岗位编辑、状态和删除。反过来，岗位状态提交/删除确认/结果不确定时也禁用轮次写入。
- POST 已成功、后续 GET 失败时同时保留保存成功提示与列表失败提示；重试只发 GET，不再 POST。
- 请求超时、连接失败或响应损坏时，保存结果不确定：锁定写操作，先“重新加载轮次并核对”，读成功后才能点击“已核对，恢复操作”。核对失败保持锁定。
- GET 核对不能撤销或保证之前的 POST 已结束；服务端没有幂等键。恢复前应查看记录，避免再次提交相同内容。
- 取消清空表单且不写入；刷新或离页放弃草稿。已发出的写请求可能继续在服务端完成。
- 组件按岗位 ID 重新创建；active 和请求编号防止离页/旧岗位请求更新新页面。
- 文本用 Vue 插值显示，不将复盘当作 HTML；多行与长文本允许换行。

## 初版网页验收步骤（用户已完成；重复规则修正另行验收）

1. 在 IDEA 使用 JDK 17 重启后端，前端保持运行并刷新页面。本次已核对本机结构，无需执行初始化 SQL 或重建数据库。
2. 用临时账号创建专用虚构岗位，进入详情，应显示“暂无面试轮次”和“新增面试轮次”。
3. 点击新增，空轮次/0/小数应拒绝；轮次类型超过 50 字符应提示。填写草稿后取消，列表不应新增记录。
4. 填第 2 轮、面试时间、技术面、待反馈与多行复盘并保存。检查成功提示、时间及换行；岗位投递状态应保持原值。
5. 再只填第 1 轮保存：第 1 轮排在第 2 轮之前，选填项显示“未填写”。刷新或回列表再进入，仍能查看。
6. 同一岗位再次保存相同轮次号，应提示重复并刷新列表，不新增、不修改原记录；历史重复记录保留。现已可编辑或按记录 ID 删除本轮。
7. 使用另一账号访问原岗位详情地址，应显示“岗位不存在或无法访问”，不能看到或新增对方的轮次；另一个自己的岗位也不应混入原岗位轮次。
8. 在专用虚构岗位测试删除：单轮删除仅移除目标；删除岗位会在明确确认后一并删除其全部面试记录。对照岗位和其他用户数据保留，不关闭外键。
9. 可选：限制网络速度，连续点击只发送一次 POST；保存中岗位状态、编辑与删除不可操作。
10. 可选：同浏览器另一标签退出，再回原表单保存，应转登录页。连接错误时先按页面核对列表，不直接重复保存。
11. 手机访问现有域名，检查单列顺序、日期时间输入、复盘换行与保存按钮，实际新增一轮并刷新回读。

用户已完成初版验收，未单独提供各设备逐项结果。重复轮次规则已按反馈修正，具体待验收步骤见[修正说明](interview-round-duplicates.md)；用户已确认该修正通过；分页与编辑现已实现，详见对应功能说明，等待本次验收。

## 2026-10-21 初版开发验证（历史记录；修正结果见新说明）

- 本机 MySQL 只读结构检查已完成，未读业务记录、改表或写入测试数据。
- JDK 17 下全量 229 项测试通过，0 失败、错误或跳过，其中新增 37 项；后端 JAR 打包通过。
- 新测试包含隔离 H2 的真实插入回读、文本规范化/NULL、毫秒精度、日期/整数/字符数边界、重复轮次号排序、伪造归属、跨岗位/跨用户隔离、登录/CSRF、404 一致性、复合外键和删除 RESTRICT。
- MVC 测试模拟数据库不可用和约束冲突，确认 503/409 不泄露内部细节；单独验证 50/100 个补充 Unicode 字符的字段校验。
- H2 VARCHAR 按 UTF-16 单元计长，不能直接代替 MySQL utf8mb4 的字符长度验证；H2 真实插入使用相同字段宽度的中文边界，补充字符在 MVC 层验证。真实 MySQL 特性与用户验收仍分别记录。
- 前端生产构建通过，无新增依赖。
- 浏览器连接仅监听本机临时端口的隔离 H2，使用运行时生成的临时凭据和虚构岗位；完成空态、必填/超长校验、取消、包含时间与多行复盘的保存、选填留空、排序、刷新和跨岗位列表检查。
- 延迟实际 POST 并连续双击，代理只收到一次成功保存请求；提交期间输入/取消/状态/编辑/删除均禁用。
- 模拟 400，字段提示和焦点正确，草稿保留；成功保存后 GET 503，只重试 GET 即恢复，不重复 POST。
- 模拟后端已保存但响应 JSON 损坏，页面保持不确定并锁定操作；核对 GET 503 后仍锁定，再核对成功可查看已新增轮次，经用户点击恢复，不重复新增。
- 保存响应延迟期间离开原岗位，再进入对照岗位，旧响应没有污染新岗位；跨标签退出后保存正确转登录。
- 检查 1280px 桌面与 375px 窄屏布局；窄屏实际新增并刷新成功，无横向溢出。
- 私密本地配置未打入 JAR；git diff --check 及新增文件空白检查通过。
- 临时测试服务、代理与标签页已关闭，浏览器视口已恢复。未修改域名/隧道，未提交或推送 Git。

测试新增文件：InterviewRoundIntegrationTests、InterviewRoundControllerTests、sql/interview-rounds-test.sql。
全量运行显式关闭本地配置导入，并覆盖无专用配置的上下文测试为隔离 H2；没有把原本的启动测试连到用户 MySQL。
构建首次离线尝试因缺少 Maven JAR 插件未完成，补齐依赖后全量测试和打包通过；表格以上结果为最终代码的检查结果。
