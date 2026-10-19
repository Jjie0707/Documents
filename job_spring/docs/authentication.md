# 阶段 2：账号、会话与岗位数据隔离

2026-10-19，用户确认注册接口已手动验收通过，并授权完成阶段 2 剩余部分。
本页记录登录、退出、当前用户查询、登录/注册页面，以及岗位只读接口的用户隔离规则。

## 当前范围

- 注册页面调用已完成的 JSON 注册接口；成功后转登录页，不自动登录。
- 登录页面使用 Spring Security 内置表单认证，成功后进入主页。
- 右上角显示账号用户名及退出按钮，不新增昵称字段。
- 刷新页面时通过当前用户接口恢复显示，不把密码、Session ID 或登录凭据保存到 localStorage。
- 岗位列表和详情按当前用户读取，替换阶段 1 的静态示例。无数据时显示真实空列表。
- 岗位新增、编辑、删除、状态修改、搜索、筛选、分页仍在阶段 3；面试功能在阶段 4。
- 现有写入接口只有注册和认证相关操作，没有开放岗位或面试的写入接口。
- 目前的列表一次返回当前账号的全部已有记录，分页将在阶段 3 接入。

## API 与数据流

| 请求 | 行为 |
| --- | --- |
| GET /api/auth/csrf | 匿名获取本次会话的 CSRF Token |
| POST /api/auth/register | JSON 注册；201 成功、409 重名、400 参数错误 |
| POST /api/auth/login | 表单格式登录；200 返回 ID、用户名，401 为统一的“用户名或密码错误” |
| GET /api/auth/me | 从登录身份取得 ID、用户名；未登录返回 401 |
| POST /api/auth/logout | 使 Session 失效、清除认证与 Cookie，返回 204 |
| GET /api/jobs | 返回当前用户的岗位数组；没有记录时返回 `[]` |
| GET /api/jobs/{id} | 同时按岗位 ID 与当前用户 ID 查询；不存在或属于别人均返回 404 |

登录请求的 Content-Type 为 `application/x-www-form-urlencoded`，字段是 `username`、`password`。
前端使用 URLSearchParams 编码，能正确处理密码中的空白、加号和 & 等字符。
注册仍使用 `application/json`，两者格式不同。

```text
登录页面
  → 获取 CSRF Token，浏览器保留会话 Cookie
  → 提交账号密码到 Security 内置认证过滤器
  → AccountUserDetailsService 查询 users
  → DaoAuthenticationProvider 使用 BCrypt 比对哈希
  → 登录成功，更新 Session ID，保存认证状态，清除过期 CSRF 状态
  → 浏览器后续携带 HttpOnly Cookie
  → /me 返回当前账号；岗位接口使用该身份中的 ID 查询
```

退出同样要求 POST 与有效 CSRF Token。GET 不执行退出。
前端每次提交前重新获取 CSRF Token，不重复使用登录前的旧 Token。
Cookie 用于识别会话，CSRF Token 用于提交校验，两者用途不同。
登录失败返回相同提示，不区分账号不存在还是密码错误；数据库错误只给出服务不可用提示。
登录时同样拒绝超过 72 个 UTF-8 字节的密码，防止超过 BCrypt 输入范围后按相同前缀比对。

## 后端隔离规则

- AppUserDetails 保存数据库用户 ID；它继承 Spring Security User，认证成功后密码凭据被清除。
- `/me` 与岗位 Controller 从 `@AuthenticationPrincipal` 读取身份。
- 列表查询固定带 `WHERE user_id = 当前用户ID`。
- 详情查询同时带 `WHERE id = 岗位ID AND user_id = 当前用户ID`。
- 前端传入的 `userId` 查询参数不会改变身份和查询范围。
- 前端路由守卫用于跳转和显示，真正的数据访问限制由后端执行。
- 所有将来的岗位/面试写入接口也必须按当前身份限制归属；本阶段不宣称这些尚未实现的写入接口已通过隔离验收。

## 关键文件

- `config/SecurityConfig.java`：认证 Provider、登录处理、退出处理、401/403 JSON 响应与密码编码器。
- `auth/AccountUserDetailsService.java`：从数据库加载注册账号。
- `auth/AppUserDetails.java`、`CurrentUserResponse.java`：区分服务端身份与对外响应。
- `job/JobReadService.java`：每次数据库查询都带用户归属条件。
- `frontend/src/api.js`：Cookie、CSRF、JSON/表单编码与错误处理。
- `frontend/src/auth.js`：仅在内存中维护用户显示状态，通过 `/me` 校准。
- `frontend/src/router.js`：未登录跳转登录页，登录后进入主页。
- `frontend/src/views/AuthView.vue`：登录/注册表单、提交中状态与错误提示。

## 手动验收

1. IDEA Reload Maven 并重启后端（8082）；运行前端 `dev`（5173）。保留本地私密配置，现有数据库无需重建。
2. 打开 `http://localhost:5173/`，未登录时应进入登录页。
3. 点击创建账号，使用临时测试用户名注册；成功后转登录页并提示注册成功。
4. 使用错误密码登录，应得到统一错误提示；使用正确密码登录后，进入主页，右上角显示用户名。
5. 刷新页面，用户名和登录状态保留；新账号列表为空，这是正常结果。
6. 点击退出，应回到登录页；直接输入主页或岗位详情地址，应仍回到登录页。
7. 为验收有实际记录时的数据隔离，分别注册 `phase2_demo_a`、`phase2_demo_b` 两个临时账号。
8. 可自行查看并手动执行 [可选演示岗位 SQL](sql/002_phase2_isolation_demo.sql)。它只为这两个已存在的账号添加测试岗位，不删除、不修改原记录，不会由应用自动执行。
9. SQL 最后的查询应返回两条测试岗位及其 ID。若没有返回记录，先确认测试账号已注册或替换脚本中的测试用户名。
10. 浏览器普通窗口登录 A，另一个浏览器或无痕窗口登录 B。两个普通标签页共享 Cookie，不能用来模拟两个独立账号。
11. A 列表只显示 A 的记录，B 列表只显示 B 的记录。A 将详情 URL 的 ID 改为 B 的岗位 ID，应该显示“岗位不存在或无法访问”，后端为 404。
12. 手机与电脑在同一局域网时，用 Vite 的 Network 地址访问；检查登录/注册表单、退出按钮和详情单列布局。

也可使用 [auth.http](http/auth.http) 手动验收 API。需要选择 local 环境，账号参数沿用 register.http 的私密文件。
登录、退出前分别获取 Token，HTTP Client 或 Postman 必须保留同一主机的 Cookie。
不要将浏览器里的 Session Cookie、CSRF Token、真实密码或真实岗位信息提交到仓库。

## 验证边界

本次代码验证结果：

- Maven 共 35 项测试通过，包括登录成功/失败、会话更新、密码凭据清除、CSRF、退出、密码长度边界和两个用户的岗位隔离。
- 前端生产构建通过。
- 浏览器实际完成注册、重名提示、错误密码、登录、刷新保持身份、退出、新账号空列表、两个账号各自列表以及越权详情访问检查。
- 检查了桌面和窄屏布局；浏览器模拟窄屏不能代替真实手机验收。

自动化测试和浏览器联调均使用独立 H2 内存库，测试账号/岗位只存在于测试环境，没有向用户 MySQL 写入数据。
H2 不完全模拟 MySQL 排序规则；注册的 MySQL 手动验收已由用户确认，新增登录与数据隔离仍需要按上述步骤在用户环境验收。
真实手机、Cloudflare Tunnel 和生产部署不视为已经验收；本次保留用户已有的 Vite allowedHosts 配置。
完整阶段 2 验收以用户完成手动检查为准，后续功能不提前标记完成。
