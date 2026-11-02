# 阶段六：Mac 专用数据库账号与只读权限验证

## 当前状态（2026-11-02）

后续更新：正式库迁入与一致性校验通过后，用户已授权并执行正式四表 SELECT、INSERT、UPDATE、DELETE 授权，提供 SHOW GRANTS 九行结果（全局 USAGE、临时四表 SELECT、正式四表业务权限）。Mac 外置配置准备与验证见[Mac 数据源准备](stage6-mac-datasource.md)，应用尚未切换。下方保留最初只读账号任务的过程，不把历史权限范围当作当前最终范围。

本小任务已完成账号创建、表级授权和 Mac 经 SSH 登录验证。用户确认登录结果，交付后授权继续[正式迁移准备](stage6-final-migration.md)。用户提供了授权输出和登录查询结果；远程账号操作由用户在 Linux 执行，助手没有获取密码或直接登录服务器。

前置任务：[临时库试恢复](stage6-mysql-trial-restore.md)、[SSH 转发验证](stage6-ssh-mysql.md)。用户已验收 SSH 任务并明确授权本次账号准备。

本次只准备了临时库只读访问。正式库、正式写权限、应用 JDBC 连接和正式迁移仍需后续步骤，不能直接把当前只读账号配置到后端后声称应用可正常增删改查。

## 目标、范围与链路

使用独立 MySQL 账号，为后续 Mac 后端连接 Linux 数据库准备凭据和明确的授权边界。保留 root 管理账号、Mac 原数据库、Linux 其他库和私有模型配置；不开放公网 3306，不改服务或 YAML。

```text
Mac MySQL 客户端
  → TCP 127.0.0.1:13306
  → SSH 转发
  → Linux TCP 127.0.0.1:3306
  → MySQL 认证 job_tracker_mac@127.0.0.1
  → 临时库四表 SELECT
```

账号名用于区分 Mac 应用用途，不能单凭名称限制物理设备。来源约束为 MySQL 看到的 Linux 回环地址；SSH 将远程连接转为该地址，后续仍必须保护 SSH 访问和数据库密码。Windows、Linux 专用账号本次未创建。

## 创建前核对

用户提供的三组只读结果：

- mysql.user 中不存在名为 job_tracker_mac 的账号，没有覆盖已有账号。
- 查询仅发现临时库 job_tracker_trial_20261101，未发现正式 job_tracker 库。
- skip_name_resolve 为 0，主机名解析开启；本次采用显式 IPv4 回环来源 127.0.0.1，实际登录结果已确认命中该账号。

本轮开始检查 Git 时为 main 跟踪 origin/main，工作区干净。此后仅修改本轮部署文档，没有分支切换、提交或推送。

## 本机密码输入与授权设计

用户在 Linux Bash 中执行提供的命令，流程如下：

1. 通过 -p 提示输入已有 MySQL root 密码，只读检查 log_raw。命令在该值非 0 或查询失败时停止，不自行修改服务器日志设置。
2. 通过 read 的隐藏输入方式设置并确认新账号密码。本次辅助命令只接受 16～128 位 ASCII 字母、数字及指定符号，以避免密码中的引号、反斜杠或换行影响 SQL 组装；这只是该辅助命令的限制，不是 MySQL 密码字符集限制。
3. 新密码仅保存在非导出的临时 Shell 变量中，关闭命令追踪，通过管道送入 MySQL 客户端；不写入命令参数、脚本文件或 SQL 文件。MYSQL_HISTFILE 指向 /dev/null，执行后清除变量并退出子 Shell。MySQL 默认对 CREATE USER 的明文密码进行日志脱敏；本次未读取服务器日志内容。
4. CREATE USER 不使用 IF NOT EXISTS，不覆盖或修改已有同名来源账号；客户端不使用 --force，创建或授权报错时停止，要求先核对部分执行状态而非盲目重跑。
5. 分别对四张表执行 GRANT SELECT，并用 SHOW GRANTS 查看结果。没有授予库级通配、全局权限、写入、DDL 或管理账号权限。

本次账号和权限如下，密码不保存在本文：

| 账号 | 来源 | 对象 | 授权 |
| --- | --- | --- | --- |
| job_tracker_mac | 127.0.0.1 | job_tracker_trial_20261101.users | SELECT |
| job_tracker_mac | 127.0.0.1 | job_tracker_trial_20261101.job_applications | SELECT |
| job_tracker_mac | 127.0.0.1 | job_tracker_trial_20261101.interview_rounds | SELECT |
| job_tracker_mac | 127.0.0.1 | job_tracker_trial_20261101.interview_questions | SELECT |

授权输出另含 USAGE ON *.*，表示没有授予全局权限，不等于允许读取任意数据库。本次没有用真实记录进行写入或删表拒绝测试。

## 验证结果

用户先提供了四条 SELECT 授权及创建成功输出，随后在 Mac 使用以下连接参数，通过提示输入新账号密码进行只读查询：

- 客户端：Mac MySQL 9.3，显式使用 TCP。
- 地址/端口：127.0.0.1:13306，依赖已建立的前台 SSH 转发。
- 数据库：job_tracker_trial_20261101。
- 用户名：job_tracker_mac，不使用 root。

实际结果：

| 查询项 | 结果 |
| --- | --- |
| VERSION() | 8.4.11-0ubuntu0.26.04.1 |
| DATABASE() | job_tracker_trial_20261101 |
| CURRENT_USER() | job_tracker_mac@127.0.0.1 |
| users 数量 | 4 |
| job_applications 数量 | 28 |
| interview_rounds 数量 | 10 |
| interview_questions 数量 | 14 |

CURRENT_USER() 确认的是实际匹配的 MySQL 授权账号；结合版本、库名、数量和授权输出，验证本次新账号已能经 SSH 读取预期临时库。这些数量属于本次试恢复快照，正式迁移时须重新核对。没有查询或输出实际用户名、密码哈希值、岗位或面试正文。

## 后端学习要点与后续边界

- MySQL 应用账号与 users 表中的网站账号是两层身份。数据库账号控制 Java 可以访问哪些表；网站登录及用户间的数据隔离仍由 Spring Security 和业务查询条件负责。
- 后续 Spring datasource 配置将把地址、端口、用户名和私有密码交给连接池，Mapper 通过该数据源执行 SQL。当前没有修改 Java package、Controller/Service/Mapper 或 YAML，项目仍沿用原 Mac 数据源。
- 正式应用除 SELECT 外还需要 INSERT、UPDATE、DELETE，以支持注册、岗位和面试管理；目前未授予。应用无需持有初始化建库、改表或账号管理权限，正式迁移由管理账号执行。
- 临时库包含真实备份数据，不能当作任意写入测试库。后续开发测试继续使用隔离库、独立 Redis 和虚构数据。

## 撤回方案与下一小任务

若需撤回，先确认目标仅为本次新建的 job_tracker_mac@127.0.0.1，并确认没有应用依赖；由管理账号撤销四表 SELECT，或移除该新账号即可，不删除临时库、源库或任何业务数据。本次未执行撤回；现有 Java 项目尚未使用该账号。

下一步建议为正式迁移准备：先确认暂停写入窗口及最终备份方案，重新核对源库状态、备份并验证，再恢复到独立正式目标库并按正式表授予应用权限。试恢复库和当前只读授权不自动升级为正式生产数据源。

正式迁移通过后再准备三端可选外置 YAML，最后按确认的重启与回退方案切换 Mac。Redis、Windows/Linux 后端部署、HTTPS 和全天在线运行维护仍为后续任务，不能因本次连接成功而视为完成。

## 检查与交付范围

助手对辅助命令进行了 Bash 语法与合成密码字符边界检查，没有替用户执行账号 SQL。远程创建、授权及登录结果来自用户提供的实际执行结果。

本轮仅新增本记录并同步总计划和交接文档，进行了文档链接、空白及 diff 检查；未重跑 Java/前端测试或构建，也未进行应用网页或真实线上部署验收。阶段五 715 项后端/JAR、26 项前端/构建为历史基线。

参考：[MySQL 8.4 表级授权](https://dev.mysql.com/doc/refman/8.4/en/grant.html)、[密码与日志](https://dev.mysql.com/doc/refman/8.4/en/password-logging.html)、[账号来源定义](https://dev.mysql.com/doc/refman/8.4/en/account-names.html)。
