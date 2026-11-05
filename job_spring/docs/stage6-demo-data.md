# 阶段六虚构演示数据

在隔离库 `job_tracker_demo` 展示登录、岗位列表、状态筛选、面试轮次和问题复盘。使用虚构公司、城市、岗位和招聘链接，图片识别保持关闭。 [演示 SQL](sql/stage6_demo_data.sql)

## 准备与导入

1. 准备独立的本地 MySQL 和 Redis。在临时目录复制一份 [建表 SQL](sql/001_init.sql)，只在副本中把 `CREATE DATABASE job_tracker` 和 `USE job_tracker` 的库名都改成 `job_tracker_demo`，然后在独立 MySQL 上执行副本。
2. 在独立的源码副本中按 [README 的独立本地启动步骤](../README.md#使用独立的本地开发库) 启动 Java 和前端。取消勾选 `mac`、`windows`、`linux` 后重新编译，使用默认配置。将本地 JDBC 库名改为 `job_tracker_demo`，MySQL 和 Redis 端口使用隔离实例的端口。应用账号只需演示库的业务读写权限。选择 `8082` 和 `5173` 空闲的演示环境。
3. 在演示页面分别注册 `stage6_demo_a` 和 `stage6_demo_b`。密码在网页中自行设置，注册逻辑负责生成哈希。
4. 在项目根目录用 MySQL 客户端批量执行演示 SQL。下例中的 `13307` 是示例端口，`demo_writer` 是示例账号，按隔离实例实际值调整。密码在本机提示中输入。

   ```bash
   mysql --protocol=TCP -h 127.0.0.1 -P 13307 -u demo_writer -p < docs/sql/stage6_demo_data.sql
   ```

   成功返回 `READY`。若返回 `MISSING_DEMO_ACCOUNTS`，先核对两个账号是否注册在演示库。若返回 `IMPORT_BUSY`，等另一个导入会话结束再执行。使用 MySQL 客户端默认的遇错即停行为，连接退出时未提交的事务会回滚。

SQL 给每张表都写明 `job_tracker_demo` 库名。它先用用户名查到用户 ID，再按归属插入岗位、轮次和问题。并行导入通过同一个 MySQL 命名锁排队。重复执行时，岗位按所属用户、样例来源和链接识别，轮次按岗位和轮次号识别，问题按轮次和题目文本识别。请先完成导入，再编辑这些样例标识。 [MySQL 命名锁](https://dev.mysql.com/doc/refman/8.4/en/locking-functions.html)

## 演示路线

| 账号 | 可见数据 | 验证动作 |
| --- | --- | --- |
| `stage6_demo_a` | 4 个岗位、3 个轮次、4 道问题 | 按面试中筛选，打开「北屿时序实验室」，查看两轮面试和一面的事务、幂等问题 |
| `stage6_demo_b` | 1 个已投递岗位 | 登录后检查列表只显示「澄岬观测站」，直接打开 A 的岗位链接应提示记录不存在 |

需要重做整套演示时，另建隔离实例并重复准备步骤。可直接关闭本次启动的临时实例来结束演示。

## 验证记录

在本机临时 MySQL 9.3、独立 Redis 和 Java 17 上验证。两个账号未注册时导入返回 `MISSING_DEMO_ACCOUNTS`，四表计数为零。通过 Java API 注册后连续导入两次，演示库均为 2 个账号、5 个岗位、3 个轮次、4 道问题。导入时会话默认选中另建的空 `job_tracker`，其中四表始终为空。Java HTTP 检查通过网页登录所用的注册和登录接口、两账号岗位列表、轮次和问题读取，以及账号 B 访问账号 A 的岗位返回 `404`。
