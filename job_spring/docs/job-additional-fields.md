# 阶段 5 · 5.2：补齐手动新增、编辑的其余四个岗位字段

2026-10-29，用户已明确确认 5.2 验收通过，不推断具体设备逐项结果。随后仅开发 [5.3a 基础截图上传](screenshot-upload.md)，等待本项验收；下方 5.2 待验收表述为历史交付记录。

2026-10-29，用户确认 5.1 验收通过并授权继续。本次只完成 5.2 的 API、UI 和开发验证，等待本项用户验收；截图上传从 5.3a 开始，尚未实现。阶段 2、3、4 和 5.1 的用户验收结论保持，不推断设备逐项结果。

## 目标和字段契约

原 `POST /api/jobs`、`PUT /api/jobs/{id}` 及新增/编辑页面补齐以下四项。它们与原六项合计十个内容字段，详情和响应原本已有对应字段。

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| educationRequirement | String | 选填，strip 后最多 100 个 Unicode 码点，自由文本，不另建学历字典 |
| sourcePlatform | String | 选填，strip 后最多 100 个 Unicode 码点，自由文本，不自动推测来源 |
| originalUrl | String | 选填，strip 后最多 2,048 个 Unicode 码点，含明确主机的完整 HTTP/HTTPS URL |
| applicationDeadline | LocalDate | 选填，JSON 使用 YYYY-MM-DD；严格真实日期，年份 1000～9999，允许过去日期 |

公司、岗位名继续必填，其余八项选填。字符串先去首尾空白，空串转 null；日期缺省/null/空白也转 null。

**PUT 提交完整十字段目标值。选填项缺省/null/空白表示清空。** 旧六字段请求会清空本次新增四项；当前网页已同步预填并提交十字段，修改任一字段时保留其余表单内容。状态由独立 PATCH 修改，客户端不能指定用户归属、主键和审计字段；不存在与越权继续统一 404。

链接只检查语法并保存，不发起网络请求或检查网站是否存在。接受大小写 HTTP/HTTPS 协议、路径中的中文、查询和片段；主机使用 URI 可解析的 ASCII 名称（国际化域名需使用 Punycode）或 IP。拒绝相对地址、协议相对地址、其他协议、主机缺失、用户信息、无效转义、空格以及错误端口（显式端口须为 1～65535）。原文保留，不自动补协议、拼接网址、改写大小写或编码。详情继续按文本显示链接。

日期不带时间和时区，不接受数组、对象、数字、布尔值、时间戳或时区后缀；例如 2026-02-30 拒绝，不自动纠正。无效日期返回 400 和 fields.applicationDeadline，错误不回显输入。前端使用原生 date 输入，具体显示格式和选择器由浏览器决定，传输值仍为 YYYY-MM-DD。前端同时检查 badInput、日期上下限等 validity 状态；空白选填日期允许保存。

本次无生产数据库结构变更、新依赖或 HTTP 文件。未连接用户 MySQL 核实结构或执行 SQL；仓库 SQL 不等于已核实本机结构。

## 关键 Java 文件和执行顺序

```text
新增输入 / GET 详情后编辑预填十字段
  → 前端必填、码点长度和日期有效性提示
  → api.js 获取 CSRF Token，POST / PUT JSON + 会话 Cookie
  → Security 登录/CSRF 检查
  → Jackson：截止日期使用 ApplicationDeadlineDeserializer
  → DTO 紧凑构造器规范化字符串
  → @Valid：码点长度及 @ValidJobUrl 检查
  → Controller 从登录身份取得当前用户
  → Service 在事务内 INSERT / 按 id + user_id UPDATE
  → Mapper 写入，编辑后按归属回读
  → JobResponse → 详情 GET 回读
```

1. `job/common/CreateJobRequest.java`、`UpdateJobRequest.java`：增加四项，前三项 String 使用现有 normalize；日期直接使用 LocalDate。请求 DTO 继续独立于数据库实体，toString 保持脱敏。
2. `job/validation/ValidJobUrl.java`、`JobUrlValidator.java`：参照现有 auth/validation 的注解与 ConstraintValidator 分工。`isValid` 用 URI.parseServerAuthority 检查主机，再限定协议、userInfo 和端口；不做网络访问。DTO 的码点长度注解独立负责存储上限。
3. `job/common/ApplicationDeadlineDeserializer.java` 的 `deserialize`：先确认 JSON 是字符串，strip 后空白返回 null；固定四位年格式配合 LocalDate.parse 严格检查闰年、月和日，再限制数据库支持的年份。只有日期含义，避免使用 Date/Instant 引入时区换日。
4. 两个岗位 advice 的 `unreadableBody`：从 Jackson 的字段路径识别 applicationDeadline，只返回固定字段提示，不返回原异常消息。其他 JSON 错误继续使用原通用 400 响应。日期解析失败发生在 Controller 方法执行之前，不能只依靠 Service 检查。
5. `JobCreateService.createForUser`：新增四个显式 setter，再 mapper.insert。`JobUpdateService.updateForUser`：新增四个 `.set(...)`，null 也实际写入 SQL NULL；UPDATE 自身限定 id/userId，事务内回读，支持同值重复保存。状态、归属和审计字段不在 SET 中，原并发编辑约定保持。

实体、JobResponse、Controller、Mapper 和详情读取无需修改，沿用原 package 职责，没有跨业务重构。

## 必要前端数据流

`JobCreateView.vue`、`JobEditView.vue` 的 form、inputs 从六项扩为十项；预填和 hasChanges 遍历 inputs，因此只修改新字段也能保存。原始链接使用 url 输入改善键盘，最终链接规则由后端校验；日期使用 date 输入，带 min/max 及空值处理。其余字段保留原校验和 textarea。

桌面沿用两列，700px 及以下单列；新增日期/链接说明。编辑页面明确说明所有选填项留空会清除原值。字段错误保留草稿并聚焦；busy、取消、401/404 和结果不确定后的重新加载继续沿用现有流程。

## 2026-10-29 实际开发验证

自动验证：

- Java 17 相关五个测试类 128 项通过；随后全量 **472 项通过，0 失败、0 错误、0 跳过**，比 5.1 增加 72 项。JAR 打包、前端生产构建和 git diff --check 通过。
- 新增 JobAdditionalFieldsIntegrationTests，在隔离 H2 中覆盖 POST/PUT 的十字段写入与详情/列表回读、规范化、缺省/null/空白、四项独立清空、原六项保留、同值重复更新、跨用户隔离、登录/CSRF、长度和 emoji 边界。
- 覆盖日期最小/最大年、过去闰日、非法闰日/格式/日期、时间或时区及 JSON 非字符串类型；链接覆盖协议、主机、用户信息、端口、转义、相对地址和长度。非法输入不新增、不更新，不回显内容。
- 现有测试构造器适配十字段；原“其他四项不可写”的断言改为保护 id/userId/status/审计字段；旧六字段 PUT 明确测试清空新四项。
- 首轮两项 emoji 边界测试暴露 H2 VARCHAR 按 UTF-16 单元计数的差异。仅将 H2 fixture 中本次三个字符串列容量放宽到双倍，DTO 和测试仍严格限制实际 100/2048 码点；MySQL SQL 设计没有修改。修正后相关测试及全量构建通过。
- Maven 离线使用现有缓存与 JDK 17，关闭私密配置导入；没有修改 pom。JAR 核对未包含本机私密配置。

隔离网页检查：

- 临时前后端仅监听 127.0.0.1，独立 H2 内存库，使用运行时随机账号和虚构岗位。
- 新增十字段真实保存，过去闰日 2024-02-29 正常回读；详情刷新、十字段编辑预填、未修改禁用、只修改新字段可保存、取消不保存正常。
- 学历超长和日期低于 1000 年被前端拦截；后端拒绝非法协议链接，错误定位并聚焦链接输入，草稿保留。延迟真实新增时新四项与原字段一并禁用。
- 375px 窄屏清空新四项后保存，详情显示未填写；原六项及面试中状态保留。日期、链接均未横向溢出；1280px 桌面详情检查通过。
- 临时代理先转发真实 PUT 保存，再返回损坏 JSON；页面锁定继续提交，重新加载后四项回读正确，恢复未修改状态；最终刷新确认服务器已保存内容。
- 临时服务和页面已按验证流程关闭，视口恢复。无用户 MySQL 写入/重建，无真实数据、密钥或模型调用，无域名/隧道/私密配置改动，无 Git 提交、推送或合并。

自动测试和隔离网页检查不替代用户本机 MySQL、现有域名与真实手机验收。

## 用户网页验收步骤（待验收）

1. 用 JDK 17 重启后端、刷新前端，保留已有配置和数据库；无需执行 SQL。
2. 新增一个虚构岗位，填学历、来源、完整 HTTP/HTTPS 链接和日期，保存后核对详情及刷新回读；也可用过去日期验证历史补录。
3. 编辑时确认十字段预填；只修改新四项中的任一项应启用保存；修改后取消，再进入应恢复服务器值。
4. 分别清空新四项后保存，详情应显示未填写；修改其他字段时未动的新四项应保留。核对职责、要求、原状态和其他岗位保持正确。
5. 学历/来源超过 100 字符、链接超过 2048 字符应提示；不完整或其他协议链接应被拒绝。日期使用合法年月日，越界/未完成的输入应提示。只填公司、岗位名也能保存。
6. 在现有域名和真实手机核对日期选择器、链接键盘、滚动、清空、保存和回读；具体系统选择器待真机验收，窄屏截图不代表真机已通过。

本项完成即停，等待用户验收。确认继续后再提出 5.3a 基础截图选择、预览与上传校验。
