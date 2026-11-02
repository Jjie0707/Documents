# 阶段六：Maven 选择 Mac 数据源配置

## 当前状态（2026-11-02）

用户已验收[正式库迁入与一致性校验](stage6-final-migration.md)，正式四表业务权限已授权，Mac 私有密码已本机保存。独立真实 JDBC 只读验证已通过。

用户要求使用 `spring.profiles.active: '@profile.name@'`，在右侧 Maven Profiles 勾选环境后继续使用原 Java 启动按钮。现已按此方式完成修改，替换此前仅配置 spring-boot:run 插件的方案。当前仅新增 mac；Windows/Linux 配置和 profile 后续分别准备。

最新资源过滤、配置加载、隔离后端 715 项及 JAR 均通过。用户授权后助手启动 mac JAR 和本机前端，运行健康、权限边界及实际 Linux 数据源连接检查通过。用户现已明确验收：列表仍在，新增、访问、识别正常；Linux 正式库已产生验收新写入，成为当前业务数据来源。用户随后要求关闭本轮实例，助手已通过各自会话停止前后端并确认 5173/8082 无监听，SSH 13306 保留，未操作 MySQL/Redis 服务或数据。Mac 接入小任务验收完成，阶段六其他部署仍未完成。

Mac 旧库和试恢复库仅保留，不能再直接用于回切或覆盖 Linux。此前 4/28/10/14 是迁移时快照数量，不代表新增后的当前数量；不重跑旧库比较来要求新库恢复旧状态。用户验收包含真实识别调用，额度按既有逻辑保留，不推断调用次数、不清空或返还计数。

## 关键文件和执行顺序

| 文件 | 职责 |
| --- | --- |
| [pom.xml](../pom.xml) | 根属性 profile.name 默认为 default；Maven mac profile 将其设为 mac。Spring Boot 父 POM 已为 application*.yaml 配置 @...@ 资源过滤，无需新增插件或依赖。 |
| [src/main/resources/application.yaml](../src/main/resources/application.yaml) | 源文件保留 active: '@profile.name@'，编译时替换；mac 激活后必须导入外置 Mac 文件，缺失即失败。 |
| [config/application-mac.yaml](../config/application-mac.yaml) | 非敏感外置配置：正式库地址、专用账号、私有密码引用和禁止 SQL 初始化。 |
| config/database-mac-local.yaml | 用户已在本机生成的私有密码文件，权限 600、Git 忽略、不进 JAR。本文不保存其值。 |
| [scripts/configure-mac-db-password.py](../scripts/configure-mac-db-password.py) | 隐藏输入并确认密码，只排他创建私有文件，不连接数据库或启动 Java。已运行成功，不重复执行覆盖。 |
| [.gitignore](../.gitignore) | 忽略新私有密码文件，原三份私有文件的忽略规则保留。 |

```text
Maven Profiles 勾选 mac
  → 编译处理资源，@profile.name@ 替换为 mac
  → target/classes/application.yaml 中 active 为 mac
  → 原 Java 主类启动，Spring 读取编译后的配置
  → 必需导入 config/application-mac.yaml
  → 环境变量或私有文件提供密码
  → 连接池 → Mac 127.0.0.1:13306 → SSH → Linux 127.0.0.1:3306/job_tracker
  → 原 Controller/Service/Mapper 使用新数据源
```

文件后缀负责 Spring 配置分组，POM 的 profile 负责 Maven 面板选项，资源过滤将两者连接。Maven 勾选或编译本身不会更改已运行进程，必须重新启动后端才生效。

未修改业务 package 或 Java 方法。数据库账号限制 Java 可以执行的 SQL；网站 users 表中的用户仍由 Spring Security 和业务归属条件隔离。

## IDEA 选择与启动方式

1. Reload All Maven Projects，重新加载 POM。
2. 在当前项目 Maven Profiles 中仅勾选 mac。
3. 使用 Java 17 执行一次 Maven Lifecycle → compile，确保资源替换完成；以后切换 profile 后也要重新编译。终端等价命令是 `./mvnw -Pmac compile`。
4. 核对 target/classes/application.yaml 中 active 为 mac，然后按确认的切换方案，使用原来的 Java 主类 Run 按钮。无需手填 Spring profile 或 additional-location 参数，工作目录仍须为项目根目录。

本轮已经完成 -Pmac 编译和打包，当前生成文件为 active: mac。IDEA 后续重新构建时仍需保留 mac 勾选。若 IDE 未处理资源，先重新导入 Maven 并执行 Maven compile；未经处理的占位符会被实际配置加载器拒绝，不可直接作为运行配置使用。

未选择环境 profile 时生成 default，保持原默认配置。已有运行参数或环境变量若显式设置 spring.profiles.active，会按 Spring 优先级覆盖编译值，切换时应核对没有冲突设置。

与此前插件专属方案不同，当前选定的环境名会写入编译产物和 JAR；私有密码、模型 Key 和外置配置仍不打包。后续为其他设备构建时须明确选择相应环境，不能把 mac 构建默认当成 Linux 部署配置。

参考 [Maven Profiles](https://maven.apache.org/guides/introduction/introduction-to-profiles) 和 [Spring Boot Maven 资源过滤](https://docs.spring.io/spring-boot/how-to/properties-and-configuration.html)。实际已核对本项目 Spring Boot 4.1.1 父 POM，过滤分隔符为 @，不会替换运行时的 ${...}。

## 数据源与密码边界

- Mac 地址 127.0.0.1:13306 指向 SSH，正式库 job_tracker，用户名 job_tracker_mac，不使用 root。
- 密码优先取 JOB_TRACKER_MAC_DB_PASSWORD，否则取私有文件中的 job-tracker.mac.database-password；缺失时不回退到原 DB_PASSWORD。没有将密码写进 Maven 属性。
- 私有文件已确认权限 600、属于当前用户并被 Git 忽略。密码工具在本机隐藏输入两次，不接受密码参数或管道输入，检查忽略规则，拒绝覆盖/跟随目标符号链接，不输出密码。
- connectionTimeZone=%2B08:00 与 forceConnectionTimeZoneToSession=true 明确设置 JDBC 会话 UTC+8；不依赖命名时区表，也不修改服务器全局时区。参考 [Connector/J 时区参数](https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-datetime-types-processing.html)。
- spring.sql.init.mode 保持 never，应用不执行初始化或恢复 SQL。
- 原 application-local.yaml、vision-quota-local.yaml、vision-model-local.yaml 保留；配置解析对比确认模型、Key、HMAC、额度、Redis、server 和上传参数一致，未输出其值。
- Redis 仍沿用原配置；未安装 Linux Redis、迁移计数或完成多实例共享额度，不清空真实额度，不要求重新发送模型 Key。

## 已有账号和实际 JDBC 证据

用户提供 SHOW GRANTS 九行结果：全局 USAGE、临时四表 SELECT、正式四表 SELECT/INSERT/UPDATE/DELETE。未授予 DDL、账号管理或其他库权限。原临时库权限暂保留，账号密码不变。

独立临时 Java 验证仅加载配置 Environment，在内存使用私有密码，未创建应用上下文、HTTP 服务、Redis 或模型客户端。建立只读事务、查询后回滚关闭，实际结果为：

- Linux MySQL 8.4 / job_tracker / CURRENT_USER 为 job_tracker_mac@127.0.0.1。
- 会话 +08:00、UTC 偏移 480 分钟、服务端 transaction_read_only=1。
- 四表数量 4、28、10、14，与正式迁移快照一致。

该连接验证发生在资源过滤调整前，数据源地址、账号和密码引用未改变；调整后又用实际编译产物、不传 Spring 激活参数的方式验证选择结果及私有密码解析一致。它证明配置和连接准备可用，不代表当前运行的原后端已经切换；重新建立 SSH 和实际重启验收仍待执行。

## 实际切换与只读运行验收（已执行，页面验收待用户）

1. 用户确认继续执行后，以 ssh -fNT 在本机恢复后台转发，13306 已监听；没有开放公网数据库端口，没有修改 MySQL/Redis 服务或域名入口。
2. 执行时 8082 已无监听，未向原用户进程发送停止信号。助手从项目根目录用已验证的 Java 17 执行 `java -jar target/job_spring-0.0.1-SNAPSHOT.jar`，未追加 profile 参数；编译产物已记录 mac，日志确认激活 mac 并在 8082 启动成功。
3. 运行验收：GET /api/health 返回 HTTP 200、status=UP、database=UP；无 Cookie 的 GET /api/auth/me 与 GET /api/jobs 均为 401，没有读取业务正文。
4. 对实际运行进程核对：已建立到 127.0.0.1:13306 的连接，未观察到到 Mac 127.0.0.1:3306 的连接。结合 mac 启动日志、独立 JDBC 账号/库名验证及健康检查，确认本次运行后端已使用 Linux 正式数据源。
5. 5173 当时空闲，助手用原 `npm run dev -- --host 127.0.0.1` 启动前端，仅供本机只读验收，未改 Vite 配置文件。首页 HTTP 200，经前端代理的 /api/health 为 UP，匿名 /api/auth/me 为 401。没有模拟真实账号登录、返回业务记录、调用模型或执行写入。

运行期间页面入口为[本机页面](http://127.0.0.1:5173/)，当前已按用户要求关闭。用户已确认列表保留，新增、访问和识别可用；不额外推断各系统/域名、编辑删除或错误分支均逐项实测。未索取网站密码、Cookie、真实记录或截图。Mac 旧库继续作为保留副本，不能恢复双端各写一份。

### 当前实例和操作权

本轮后端由命令启动，不在 IDEA 原 Run 窗口中管理；其 mac 配置来自 Maven 资源过滤的构建结果。前端也由本轮启动。两者现已按用户指令关闭。IDEA Profiles 的选择及原 Run 的操作步骤已准备，但本轮没有代替用户点击 IDE 按钮，不能将 JAR 运行验证描述为 IDE UI 操作已验收。

关闭时只对本轮两个会话发送 Ctrl+C，后端正常优雅关闭及连接池关闭，未使用宽泛的 pkill。检查确认本轮前后端端口均释放，SSH 仍监听；没有停止或接管其他实例。用户之后可按 mac 勾选、编译和原 Java Run 的方式自行运行；新写入继续统一到 Linux，不使用 default 回到旧 Mac 副本。

后台 SSH 不要求终端窗口一直开启，但断网、休眠仍可能令其失效；不是自动重连、开机启动或全天在线方案。Linux Java/Nginx/HTTPS、Windows 部署和共享 Redis 仍待后续任务。

### 回退边界

如果本轮运行失败，在尚无 Linux 新业务写入时，可在用户确认后停止本次实例、保留文件和数据，检查脱敏错误；是否恢复原配置由用户明确决定。恢复配置需取消 mac 勾选并重新编译得到 default，或明确指定运行时 default，同时记录源库冻结窗口变化。Linux 有新写入后必须先保护和处理新数据，不能直接返回旧快照。

当前未执行回退。用户已完成验收并在 Linux 产生新增记录，本次迁移窗口的同快照比较已结束，新业务统一写 Linux，旧 Mac 库保持保留副本。若以后确需回迁，先备份并保护 Linux 新数据、重新设计增量/全量回迁及验证，不直接恢复旧快照。

## 本轮验证及限制

- 实际 Maven process-resources 分别验证未选择时 active=default、-Pmac 时 active=mac，${DB_PASSWORD} 和模型 Key 的运行时占位符仍保留，没有过滤真实密码。
- 使用实际 Spring Boot 4.1.1 配置加载器，只加载 Environment：编译后的 default/mac 不依赖启动参数即可选择正确数据源；私有密码可解析，保留配置一致；显式运行时覆盖仍可用；mac 外置文件缺失即报错；原始未替换占位符被拒绝。没有自动创建真实数据源或应用服务。
- 密码工具检查覆盖私有权限（包括极端 umask）、已有文件/符号链接保护、格式、Git 忽略、两次确认、交互终端与参数限制和输出不含合成密码。原三份私有文件保持不变。
- 最终资源过滤版本下 -Pmac 全量后端 715 项，0 失败/错误/跳过，JAR 打包通过。禁用私有导入、强制 H2、隔离 Redis 和受控本机 HTTP；未连接业务库或真实模型。使用临时 Maven 缓存、Mockito 启动代理和允许测试端口的方式排除了沙箱限制，未修改依赖或测试断言。
- 最终 JAR 验证 active=mac 且不残留 @profile.name@；运行时秘密占位符保留，外置和私有配置不打包。日志 /private/tmp/job-stage6-filtered-mac-package.log，临时验证代码在 /private/tmp，均不是部署依赖。
- 助手实际运行检查通过：mac 启动日志、后端健康/匿名权限、运行进程的 SSH 数据源连接、前端首页/API 代理；助手未执行业务写入、模型调用或真实用户登录。用户随后自行完成新增、访问、识别验收，记录该范围，不推断调用次数或具体设备。前端代码未改，未重跑其自动测试/构建。
- 用户要求关闭后，只关闭本轮前后端并核对端口；未重新运行测试/构建或旧库比对，未更改数据或清理真实额度。剩余任务包括 Linux Redis/共享额度、Windows/Linux 配置和账号、Linux 常驻与 Nginx/HTTPS、Windows 域名入口、README/虚构演示数据及部署回归。

## 阶段六未完成事项与下一任务建议（未开始）

- Linux Redis：核对实际资源/端口/软件包，设计认证、网络隔离、持久化、noeviction 和恢复策略；迁移或保全当前额度并保持共享 HMAC，不重置真实已用次数。方案确认后再安装配置。
- Windows/Linux：补齐 application-windows.yaml、application-linux.yaml 及相应 Maven profile、专用账号和私有密码注入，分别验证连接；当前尚不能视为这两端配置已可用。
- Linux 全天运行：Java 17 服务、内存预算、前端静态构建、Nginx/HTTPS、日志、更新及回退，保留已有服务。先准备具体变更，不自行中断。
- Windows 域名入口：核对 Tunnel、前端托管和同域 API 代理链，再提出入口调整、验证与回退方案；未改 DNS 或 Tunnel。
- README 本地/线上说明、虚构演示数据、备份恢复和手机/电脑部署回归，区分自动、隔离、实际部署和用户验收。

建议下一小任务为“Linux Redis 部署准备核对”：先只读确认环境和资源，给出内存/持久化/认证/额度保全设计，不安装、不启停服务、不操作真实 Redis 或数据库。用户当前仅要求提出剩余工作并确认是否继续，尚未授权该下一任务。
