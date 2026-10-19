# 阶段 2 · 第一个小任务：用户注册接口

用户于 2026-10-18 确认进入阶段 2，并确认本页的用户名、密码长度规则。
本页保留注册接口的说明。2026-10-19 用户已确认注册接口验收通过，并继续授权实现登录、退出、注册页面及岗位数据隔离，当前说明见 [阶段 2 账号与隔离](authentication.md)。

## 参数约定

- 用户名去除首尾空白，之后为 1–50 个 Unicode 字符；不额外限制中英文或符号。
- 用户名唯一性遵循现有 `users.username` 的 `utf8mb4_unicode_ci` 排序规则，其中包括不区分大小写。
- 密码至少 8 个 Unicode 字符，UTF-8 编码长度不超过 72 字节。字符数与字节数不同，例如一个常用汉字通常占 3 个 UTF-8 字节。
- 不裁剪密码空白、不截断超长密码，不要求额外的大小写或特殊字符组合。
- 使用 Spring Security 的 BCryptPasswordEncoder，强度参数为 12；保存的是随机盐参与计算的哈希，不是可解密密码。
- 注册成功只返回 ID 与用户名，不返回密码或哈希，也不自动登录。
- 不修改现有表结构，创建和更新时间继续由数据库默认值生成。

## 接口

### GET /api/auth/csrf

允许匿名访问，响应包含 `headerName` 和 `token`，并设置 `Cache-Control: no-store`。
获取 Token 会创建匿名会话，客户端应保留 Session Cookie。
当前默认请求头名称是 `X-CSRF-TOKEN`；调用端应以响应中的 `headerName` 为准。

### POST /api/auth/register

JSON 请求包含 `username` 与 `password`。携带上一步返回的 Token 请求头及同一会话 Cookie。

| HTTP 状态 | 含义 |
| --- | --- |
| 201 | 注册成功，响应包含 `id`、`username` |
| 400 | 参数不符合规则，或 JSON 请求体格式错误 |
| 403 | CSRF Token 缺失或错误，尚未进入注册业务 |
| 409 | 用户名已存在，包括并发请求触发唯一索引冲突 |
| 503 | 数据库访问失败，响应不包含数据库异常内容 |

错误响应包含 `code`、`message`、`fields`。`fields` 只有字段名与提示，不包含输入值。
`permitAll()` 只允许匿名访问，并不会跳过 CSRF 校验。
匿名入口另包含阶段 2 新增的登录处理，具体权限和接口见 [阶段 2 说明](authentication.md)。

## 数据流与文件

```text
先 GET /api/auth/csrf，保留会话 Cookie 与 Token
  ↓
POST /api/auth/register（JSON + Token + Cookie）
  → Security：允许匿名，但检查 CSRF
  → AuthController：反序列化、去除用户名首尾空白、@Valid 校验
  → RegistrationService：按用户名查重
  → PasswordEncoder：计算 BCrypt 哈希
  → UserMapper：向 users 插入用户名与哈希，取得自增 ID
  → RegistrationResponse：只返回 ID、用户名
```

- `RegisterRequest` 与密码校验类：接收、校验参数；请求对象的 `toString()` 不显示凭据。
- `UserAccount`：数据库实体；`@TableId(type = AUTO)` 对应现有自增主键，`passwordHash` 显式映射 `password_hash`。
- `UserMapper`：继承 MyBatis-Plus BaseMapper，使用 `@Mapper` 注册。
- `RegistrationService`：在事务内完成写入；先查重以提供明确提示，再捕获唯一索引冲突处理并发情况。
- `RegistrationResponse`：单独的响应 DTO，避免将数据库实体直接暴露给客户端。
- `RegistrationExceptionHandler`：将校验、用户名冲突与数据库错误转换成有限的响应信息。

## IDEA 手动检查

1. Reload Maven，再从 IDEA 重新运行后端，端口为 8082。
2. 打开 [register.http](http/register.http)。在相同目录自行创建 `http-client.private.env.json`，结构如下；替换中文占位内容，只使用临时测试账号，不用真实常用密码：

   ```json
   {
     "local": {
       "registrationUsername": "自行填写新的测试用户名",
       "registrationPassword": "自行填写符合规则的测试密码"
     }
   }
   ```

   该私密文件已加入 Git 忽略规则，不要强制提交。执行 HTTP 请求时在 IDEA 选择 `local` 环境。
3. 先运行请求 1，获取 Token；再运行请求 2，应返回 201。HTTP Client 自动保留 Cookie，请始终使用同一主机名，不要混用 localhost 和 127.0.0.1。
4. 在 IDEA Database 面板查看新增行，确认 `password_hash` 为 BCrypt 哈希，而不是输入的明文密码。不要把实际哈希或密码贴进仓库。
5. 再执行请求 2，或将用户名改成仅大小写不同的形式，应返回 409，表中没有第二条同名用户。
6. 执行请求 3，应仍返回 401，证明注册没有自动登录。此时 `/api/jobs` 还没有业务实现。
7. 执行请求 4，应返回 403；执行请求 5，应返回 400，响应不回显密码。
8. 可将密码改为 7 个字符、73 个 ASCII 字符或 25 个相同常用汉字，带有效 Token 提交，应返回 400 且不会新增用户。
9. 若 Session 过期或后端重启，重新执行请求 1，再执行后续请求。

若 IDEA 版本不支持 HTTP Client，可以使用支持 Cookie 保留的 API 调试工具执行相同流程。
手动注册会真实写入开发库，建议使用可辨认的临时测试用户名。

## 自动化验证边界

注册集成测试使用独立 H2 内存数据库，测试结束回滚；不会向本机 MySQL 写入测试用户。
测试包含真实 Controller → Service → MyBatis-Plus → 内存数据库流程、密码哈希校验、CSRF 流程、大小写重复及非法参数。
H2 测试表使用 `VARCHAR_IGNORECASE` 验证不区分大小写；它不等同于完整模拟 MySQL 的排序规则，MySQL 实际写入仍需按上面步骤验收。
并发冲突分支另用模拟数据库唯一索引异常验证，不宣称已经做了 MySQL 并发压测。

2026-10-18 验证结果：Java 17 下 `./mvnw test` 通过，共 23 项测试（包含阶段 1 的 5 项回归测试）。
2026-10-19 用户确认已完成上述注册、重复注册、非法参数、CSRF 与哈希查看的手动检查。新功能的验收单独记录在阶段 2 说明中。
