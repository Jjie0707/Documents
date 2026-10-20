# 阶段 3 · 第九个小任务：岗位关键词搜索后端接口

2026-10-20，用户已确认网页删除正常并继续阶段 3。
本次扩展现有 GET 列表接口，复用 JobReadController、JobReadService、JobMapper 与既有分包。
只做关键词搜索后端；网页搜索、状态筛选和分页分别推进，不修改前端或数据库结构。

用户已明确确认本次接口可正确模糊查询，验收通过。随后接入[网页搜索](job-search-ui.md)，页面验收另行记录。
本页保留搜索接口交付时的范围与验证记录。

## 接口约定

`GET /api/jobs?keyword=Java`，需要登录会话，不需要 CSRF Token 或 JSON 请求体。

- keyword 可省略；去掉首尾空白后为空，与原 GET /api/jobs 相同。
- 去掉首尾空白后最多 100 个 Unicode 码点，中文和常见补充字符都按码点计数。
- 在公司名、岗位名、城市三个字段中做包含搜索，任意一个字段命中即可，英文大小写不敏感。
- 整个 keyword 作为一个连续片段匹配，不拆分成多个关键词，不搜索薪资、职责或要求。
- `%`、`_`、`!`、反斜线、引号等都按字面内容处理，不开放用户自定义 SQL 通配符或正则。
- 仍只返回当前用户自己的岗位，结果为原有 JobResponse 数组，按 ID 倒序；没有匹配时返回 200 和 []。
- userId 等额外查询参数不会改变身份或查询归属。

英文大小写通过 SQL 的 LOWER 两侧处理；重音等其他字符的等价规则仍受数据库字符集和排序规则影响，
H2 与本机 MySQL 不保证所有 Unicode 比较细节都一致。
请求参数中的 +、&、% 等需要 URL 编码，如 Java+& 应发送 keyword=Java%2B%26。

| HTTP 状态 | 含义 |
| --- | --- |
| 200 | 返回当前用户匹配的岗位数组，Cache-Control: no-store |
| 400 | keyword 超过上限，返回 INVALID_REQUEST 和 fields.keyword 提示，不回显原输入 |
| 401 | 未登录 |
| 503 | 数据库访问失败，沿用通用提示，不返回异常细节 |

详情接口和原有列表/错误响应保持既有结构，新增 400 使用 code、message、fields。

## 执行顺序与关键代码

```text
GET /api/jobs?keyword=...
  → Security 检查登录
  → @ModelAttribute 将 URL 查询参数构造为 JobListQuery
  → DTO 构造器 strip，空字符串转 null
  → @Valid 校验规范化后的关键词长度
  → JobReadController 从登录身份取得当前用户 ID
  → JobReadService 构造归属条件，非空关键词追加 AND (...) 搜索分组
  → JobMapper 绑定参数并执行 SELECT，按 ID 倒序
  → JobResponse 数组
```

1. `common/JobListQuery.java` 是 GET 查询 DTO。@ModelAttribute 读 URL 参数，和写接口的
   @RequestBody 读 JSON 不同；record 紧凑构造器先规范化，@CodePointLength 再检查长度。
   Java String.length() 计算 UTF-16 代码单元，不能直接代替本次字符上限。
   重写 toString，避免直接打印 DTO 时带出关键词。
2. `controller/JobReadController.java` 增加 @Valid @ModelAttribute 参数，原身份获取和 no-store 保留。
   不传关键词时仍使用同一入口，原网页不需要同步修改。
3. `service/JobReadService.java` 先设置 user_id，再用 and 把三个 OR 条件放进同一组。
   不能把 OR 直接接在 user_id 条件之后，否则可能把其他用户在岗位名或城市命中的记录也选出来。
4. 搜索模式在关键词前后加 % 实现包含匹配。显式指定 ESCAPE '!'，先把输入中的 ! 转为 !!，
   再将 %、_ 分别转为 !%、!_；输入的符号保持字面含义。
5. apply 中的 SQL 片段全部固定，{0} 由 MyBatis-Plus 变成绑定参数。关键词不拼接进 SQL 文本。
   **参数绑定**防止输入变成 SQL 语法，**LIKE 转义**控制通配符含义，两者解决的问题不同。
6. `advice/JobReadExceptionHandler.java` 新增查询字段校验处理，仅返回字段名与提示；
   原详情 404、数据库 503 处理保持不变。

SQL 等价于：

```sql
SELECT ... FROM job_applications
WHERE user_id = ?
  AND (
    LOWER(company_name) LIKE LOWER(?) ESCAPE '!'
    OR LOWER(job_title) LIKE LOWER(?) ESCAPE '!'
    OR LOWER(city) LIKE LOWER(?) ESCAPE '!'
  )
ORDER BY id DESC;
```

没有关键词时不追加 AND 搜索分组。查询只读，不修改岗位和状态，也不消耗 CSRF Token。
本次未分页，包含搜索与 LOWER 不保证利用普通字段索引；这是小规模列表搜索，不是全文检索。
后续分页、状态筛选另做，不能把当前实现记为阶段 3 已全部完成。

## 本机手动检查（用户已确认通过）

1. IDEA 使用 JDK 17，重启后端（8082），保留本地私密配置，无需改表。
2. 登录临时账号 A，使用已有新增页面创建虚构样例：公司名含 Java、岗位名含 Java、城市含 Java 各一条，
   再建一条三个字段都不含 Java 的对照岗位。使用临时账号 B 创建相同关键词的岗位作隔离对照。
3. HTTP Client 通过 auth.http 单独登录 A，打开 [job-search.http](http/job-search.http)。
   浏览器和 HTTP Client 不共享 Cookie，需分别登录；GET 搜索不用先获取 CSRF Token。
4. 请求 1 不筛选；请求 2、3、4 都只返回 A 的三个搜索字段含 Java 的记录，按 ID 倒序。
   请求 5 空关键词与原列表一致。公司、岗位、城市以外的字段命中不会影响本次结果。
5. 请求 6 无匹配应为 200 和 []。如需检查符号，创建专用虚构样例含 %、_、Java+&，
   请求 7、8、9 应只匹配包含对应字面字符的记录，不能因为 % 或 _ 返回全部岗位。
6. 请求 1 会准备超长测试字符串，再执行请求 10，应为 400，fields.keyword 有提示，不回显关键词。
7. A 加上 B 的 userId 参数仍只返回 A 的结果；退出后登录 B 应只见 B 的结果。退出后搜索应为 401。
8. 刷新现有网页列表和详情，原查看、编辑、状态与删除功能应照常。本页接口交付时尚无搜索框，
   后续已接入[网页搜索](job-search-ui.md)，页面检查另行记录。

只使用虚构岗位与临时账号，不把密码、哈希、Cookie、真实搜索内容或真实用户数据写入代码或 Git。

## 自动验证与用户验收

- 搜索集成测试在独立 H2 内存库执行并回滚，凭据运行时随机生成，不向用户 MySQL 写入业务数据。
- 覆盖原列表兼容、空白关键词、三个字段独立/组合命中、英文大小写、顺序、无匹配和空账号。
- 为其他用户在三个 OR 分支分别放置匹配岗位，验证条件分组和伪造 userId 均不能绕过归属。
- 覆盖字面 %、_、!、反斜线、SQL 外观文本，URL 编码的 + 与 &，中文/补充字符的 100 码点边界及超长拒绝。
- 验证 GET 无需 CSRF、仍须登录、查询不写数据；MVC 模拟数据库异常检查通用 503 不暴露异常或关键词。
- 本页接口交付时未修改前端，不重复执行前端构建或浏览器联调。用户已确认接口验收通过，
  与上述 H2 自动化结果分别记录，不扩展为网页搜索的验收结论。

2026-10-20 验证结果：Java 17 下新增 28 项测试通过，全量 146 项测试全部通过，
0 失败、0 错误、0 跳过，包含注册、登录、列表/详情、新增、编辑、状态修改与删除回归。
使用离线 Maven 缓存与 JVM 启动时加载的 Mockito 测试代理，没有修改 pom.xml 或私密配置。
`git diff --check` 与新增文件空白检查通过；未修改域名/隧道或提交 Git。
