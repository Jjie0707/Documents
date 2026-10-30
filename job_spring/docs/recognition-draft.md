# 阶段 5 · 5.4：识别草稿契约与结果校验

2026-10-30，用户确认 Android/iOS 上传与拍照正常，5.3c 验收通过并授权继续。本项 Java 开发与自动验证完成；用户随后在了解无需新网页验收后明确授权继续 5.5，最新进度见 [5.5 说明](vision-quota.md)。

## 本项交付与边界

新增独立十字段草稿 DTO、严格 JSON 解析与字段校验服务、固定业务异常及测试。后续 5.7 从模型响应中取出内容文本，再调用本服务；本项没有模型客户端、识别 HTTP 接口、假识别按钮、网页草稿、Mapper 或数据库写入。

草稿与创建岗位是两个阶段：识别结果允许缺少公司名和岗位名，用户补齐后才交给现有 CreateJobRequest 创建。DTO 不继承数据库实体，也不含 id、userId、status、图片路径或模型原文。

## 十字段契约

输入必须是一个纯 JSON 对象；字段名区分大小写。输入字段允许省略，显式 null 与空白字符串统一为空。输出 DTO 具有以下十字段，缺失值保持 null。

| 字段 | 含义 | 非空值规则 |
| --- | --- | --- |
| companyName | 招聘公司 | 字符串，最多 200 Unicode 码点 |
| jobTitle | 岗位名 | 字符串，最多 200 码点 |
| city | 城市 | 字符串，最多 100 码点 |
| salary | 薪资原文 | 字符串，最多 100 码点；不接受数字自动转文本 |
| responsibilities | 岗位职责 | 字符串，最多 10000 码点，保留内部换行 |
| requirements | 任职要求 | 字符串，最多 10000 码点，保留内部换行 |
| educationRequirement | 学历要求 | 字符串，最多 100 码点 |
| sourcePlatform | 来源平台 | 字符串，最多 100 码点 |
| originalUrl | 原始招聘链接 | 最多 2048 码点，完整 HTTP/HTTPS、有效主机、无账号密码，复用原 URL 校验 |
| applicationDeadline | 截止日期 | 输入为 YYYY-MM-DD 字符串，转换成 LocalDate；1000～9999 年有效日期，允许过去日期 |

所有文本先 strip 首尾空白，再按码点计数；不会截断、扩写或把数组拼成字符串。无效日期不自动改正，不根据当天补年份。模型提取的语义仍需用户核对：Java 只验证结构和格式，不能证明公司名或来源平台真实正确。

模型接入时继续要求：仅依据图中可确定的内容填值；平台名称、链接或完整日期不在图片中则 null，不依据文件名/颜色猜测、不拼造网址。当前规则不会凭空补出这些信息，但也不能代替模型的证据约束和用户检查。

合法的部分输入示例：

```json
{"jobTitle":"Java 实习生","responsibilities":"维护接口\n编写测试","sourcePlatform":null}
```

会得到岗位名和职责，其余字段 null。只有来源/日期等单项非空时也允许作为部分草稿；“无有效内容”在本服务中严格指十字段规范化后全部为空，不额外推断截图是不是招聘信息。

## 执行顺序与关键 Java 文件

```text
模型内容文本（未来 5.7 提供）
 → RecognitionResultService.parseAndValidate
 → 原始长度检查 → 严格 JSON 对象解析
 → 固定字段白名单 + 字符串/null 类型检查
 → JobRecognitionDraft 构造规范化 + 原日期解析器
 → Validator.validate 检查码点长度和原 URL 规则
 → 全空检查 → 返回草稿
 → 未来用户补齐/修改 → 原岗位创建接口保存
```

- `vision/common/JobRecognitionDraft.java`：独立 record。复用 `@CodePointLength`、`@ValidJobUrl` 和 `ApplicationDeadlineDeserializer`，不加创建请求的 `@NotBlank`。构造器负责首尾空白/null 规范化，toString 固定脱敏。
- `vision/service/RecognitionResultService.java`：构造器注入 Jakarta Validator。普通 Service 方法不会因 DTO 上有注解就自动校验，因此显式调用 `validator.validate(draft)`，仅在全部规则通过后返回。
- Service 内独立创建 Jackson 3 JsonMapper，不修改 Spring 全局 ObjectMapper。先读成树检查类型，避免宽松绑定把数字变成字符串；开启重复键和尾随 token 拒绝。树与流解析器的职责参考 [Jackson databind](https://github.com/FasterXML/jackson-databind) 和 [Jackson core](https://github.com/FasterXML/jackson-core)，本项目按已安装 3.1.5 编译并测试。
- `vision/exception/RecognitionResultException.java`：只携带固定 code/message。解析失败不附带原异常 cause、不输出模型 JSON 或字段值；本项还没有 Controller，因此没有提前添加 HTTP 状态映射或全局 advice。

## 失败规则与资源限制

| 错误码 | 含义 |
| --- | --- |
| INVALID_RECOGNITION_RESULT | 空文本、非对象、语法错误、Markdown 代码围栏、额外说明/第二个 JSON、重复/未知字段、类型错误、非法链接/日期、超长字段等 |
| EMPTY_RECOGNITION_RESULT | 合法对象，但十字段规范化后全部为空 |
| RECOGNITION_RESULT_TOO_LARGE | 原始 JSON 文本超过 300000 个 Java UTF-16 单元 |

上述错误拒绝整份结果，不静默删除非法字段，也不重试模型。输入文本上限在解析前检查；解析器限制嵌套深度 4、字段名 64、数字 token 128、字符串 300000，关闭异常位置中的源文本。未知嵌套结构仍会被字段类型规则拒绝。300000 上限已验证可容纳所有字段达到合法上限、且补充平面字符全部以 Unicode 转义表示的情况。

本服务接收的 String 已在内存中；未来模型客户端还须在读取 HTTP 响应时设置有限字节数与超时，不能把本项 String 上限误当成网络响应读取限制。也没有验证供应商 JSON 输出稳定性、费用、额度或实际识别效果。

## 本轮验证与验收

- 新增 `RecognitionResultServiceTests` **86 项通过**，纯内存、虚构内容、不启动数据库或网络。覆盖十字段映射、部分/全空、创建必填约束仍存在、每字段错误类型、异常 JSON、重复/未知字段、每项码点边界、链接/日期、输出字段固定、最大转义输入、长度/深度限制及异常脱敏。
- Java 17 全量 **581 项通过，0 失败、0 错误、0 跳过**（历史 495 + 新增 86）；JAR 打包通过。全量测试禁用本机配置导入，默认数据源强制为 H2 内存库并关闭自动初始化，各集成测试使用隔离 H2 fixture。
- 本轮重新执行前端 20 项测试与生产构建通过；前端业务代码没有新增修改，工作区中已有 5.3c 改动保留。
- 本项无新页面或 HTTP 入口，未另做隔离浏览器检查。此前手机上传/拍照由用户确认验收；本轮自动验证不替代未来真实识别网页验收。
- main、cf36f0e，保留既有未提交内容；无分支切换、提交/推送/合并，无用户 MySQL 操作、生产初始化 SQL、私密配置读取或模型调用。

学习验收建议：依次阅读 DTO、parseAndValidate 和测试，比较“只有岗位名可返回草稿”“salary 为数字被拒绝”“全空对象返回 EMPTY”三种情形。可用 JDK 17 运行 `./mvnw -Dspring.config.import= -Dtest=RecognitionResultServiceTests test`，该测试类无数据库操作。

网页回归步骤（本机可选）：刷新新增页，选择/拍照并检查图片，确认仍只显示图片元数据；用虚构岗位检查手动新增/编辑。这里不会出现识别草稿，本项规则通过测试和代码验收；真实识别与结果页面按 5.7 接入。

本项完成后曾停止等待确认；用户现已授权推进 5.5 Redis 每日额度查询与页面展示，见其独立说明。
