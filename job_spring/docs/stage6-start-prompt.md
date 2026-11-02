# 新对话提示词：阶段六部署和交付

2026-11-02 验收和关闭最新更新：用户已确认列表保留，新增、访问和识别正常；Linux 正式库已有新写入，旧 Mac 库只作保留副本，不可直接回切或覆盖 Linux。按用户要求，助手已关闭自己启动的前后端，5173/8082 无监听，后台 SSH 13306 保留，数据库/Redis 未停启、额度未清理。Maven mac 配置保留，用户可勾选后编译再用原 Java Run；Windows/Linux profile 尚未完成。下一步须先询问并等待用户确认一个未完成小任务，建议 Linux Redis 环境及现有额度保全方案的只读核对。见[Mac 接入记录](stage6-mac-datasource.md)；不重复请求密钥、不重做迁移、不按旧快照恢复新增前状态，也不自行安装或切入口。

2026-11-02 正式库验证更新：用户已确认暂停所有 Mac 源库写入，保持原后端不启动，MySQL/Redis/SSH 继续运行。最终备份、传输及正式 job_tracker 创建/导入完成，用户实际运行校验脚本成功：两端结构、数量、内容摘要一致，完整性异常均为 0，无 SQL 警告，四表数量 4/28/10/14。数据复制和只读校验已完成，但 job_tracker_mac 仍只有临时库 SELECT，正式库业务权限、三端 YAML 和 Mac 项目切换尚未执行。继续时优先读取[正式迁移记录](stage6-final-migration.md)和总计划顶部，先核对冻结是否仍持续及当前步骤，不自行恢复写入或切换连接，也不重新要求用户重复此前全部核对。

2026-11-02 更新：下方保留阶段六开始前的原始提示词。目前已确认双 Java 后端共享 Linux MySQL/Redis 的方向，完成[临时库试恢复](stage6-mysql-trial-restore.md)、[Mac SSH 转发](stage6-ssh-mysql.md)及[Mac 专用账号只读验证](stage6-mysql-account.md)。继续时先读这些记录和总计划顶部，不要按旧提示重复环境核对。job_tracker_mac@127.0.0.1 目前仅有临时库四表 SELECT 权限；正式库写权限、正式迁移、Redis、三端 YAML 和应用部署尚未完成，Mac 项目未切换数据源，不能直接用当前只读临时库连接代替正式切换。

将以下内容复制到新对话即可。

```text
继续开发我的“岗位投递与面试记录”Java 后端学习项目，准备进入阶段 6：部署和交付。

项目目录：
/Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring

请先读取：
1. /Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring/docs/JOB_TRACKER_DEVELOPMENT_PLAN.md
2. /Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring/docs/stage5-handoff.md（最新交接，以它为准）
3. /Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring/docs/stage5-acceptance.md
4. /Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring/docs/stage5-plan.md
5. /Users/ouyangjunjie/Desktop/GitHub/Documents/Documents/job_spring/docs/pages.md
必要时再读取关联的模型、Redis、认证、数据结构与功能文档。

也请核对当前适用的 AGENTS.md、Git 分支和工作区状态、实际代码、数据库设计、配置、依赖、测试，以及已有部署材料。文档中早期交付记录可能过时，请以最新验收和实际代码为准，保留所有已有改动。

我已验收阶段 2、3、4、5。最近明确确认手机和电脑都可以成功识别并填入，阶段五按既定范围结束。上一轮开发验证为 715 项后端测试及 JAR、26 项前端测试及构建通过；这是历史基线，不代表阶段六后续修改已经测试。

当前默认模型是 glm-4.1v-thinking-flash，API Key 已通过本机私有配置提供，不要让我发到聊天里。当前额度是每 IP 每天 30 次、全站每天 300 次，按北京时间自然日重置。识别草稿只在页面内存中，用户主动填入、人工修改后复用原 POST /api/jobs 保存。不要把已验收内容重新当成待开发功能，也不要擅自换模型或付费调用。

阶段六原定目标：Linux 部署、Nginx、HTTPS、MySQL/Redis 配置、README 本地启动和线上访问说明、虚构演示数据及部署回归。我已有自有域名和 Tunnel 使用经历，请先核对实际环境与代理链，不要假定我已经有 Linux 服务器、远程连接、Docker 或确定的部署拓扑。缺少信息时，只询问必要的非敏感信息，不索取密码、密钥或 Cookie。

继续沿用以下协作方式：
1. 每次只做一个小任务；可将该任务相关的后端、UI 和部署配置一起完成，不新增 HTTP 文件。上次合并 5.8/5.9 不代表本阶段也一次全部实施。
2. 实现前说明目标、涉及文件、数据流或请求链路、设计理由、边界和验收方式。
3. 我主要学习 Java 后端。交付时主动解释关键 Java 文件、方法、执行顺序和设计思想；前端只解释必要数据流，部署配置解释关键参数及其作用。
4. 沿用现有 package 和 controller/service/mapper/common/advice 职责，不随意重构。
5. 运行必要测试和构建，区分自动测试、隔离网页检查、真实部署和仍需我验收的部分；同步功能/进度文档，每个任务完成后等待我验收。
6. 保留已有数据、代码改动和服务；测试使用隔离库、独立 Redis 和虚构数据，不初始化或重建我的现有库，不清空真实 Redis 或额度。
7. 私有配置不进入 Git、日志、文档、前端或构建产物。检查上传超时、会话/CSRF、HTTPS、可信代理/IP 额度、数据库和 Redis 网络边界、备份恢复及更新回退。
8. 不自行提交、推送、合并或购买服务。线上入口切换、DNS/隧道调整或中断现有服务前，先准备可审阅的具体方案、验证和回退步骤，再由我确认执行。

现在请先核对现状，拆分阶段六的小任务，并提出 6.1“部署方案与环境准备核对”的目标、涉及文件、请求链路、设计理由、边界和所缺信息，报告当前分支状态，再询问我是否开始实施。

在我明确确认开始之前，只做读取、核对和规划，不修改文件，不创建或切换分支，不安装软件、不启停服务、不修改数据库、DNS、隧道或线上配置，也不要开始部署。
```
