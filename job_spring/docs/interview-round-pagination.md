# 阶段 4 · 验收修正 2：面试轮次一轮一页

2026-10-21，用户确认[拒绝重复轮次](interview-round-duplicates.md)验收通过，要求按顺序完成剩余分页与轮次编辑。
本次先完成分页并通过相关测试，再完成[轮次信息编辑](interview-round-update.md)，统一交付用户验收。
**状态：开发验证完成，等待本次用户验收；不代表阶段 4 整体完成。**

## 当前页面与接口

面试区每次只显示一条已保存的轮次，提供“上一轮”“下一轮”和“第 x / y 页 · 每页一轮”。
首/末页按钮禁用，无记录时显示原空态；编辑草稿期间禁用翻页，避免把草稿与另一条记录混淆。
历史重复记录仍各占一页，按 round_no ASC、id ASC 排列，不自动合并或清理。
编号不连续时也正常翻页，例如第 1、3、9 轮分别位于第 1、2、3 页。

```text
GET /api/jobs/{id}/interview-rounds?page=2
GET /api/jobs/{id}/interview-rounds?roundId=123
```

响应从初版数组改成对象：

```json
{"items":[],"page":1,"size":1,"total":0,"totalPages":0}
```

- 固定每页 1 条，size 始终为 1，totalPages 与 total 相同。不提供可调整的每页条数功能。
- page 缺省/空串使用 1，必须为 Integer 范围内的大于等于 1 的整数。超过末页时返回末页及实际 page。
- 空列表统一 page=1、total=0、totalPages=0、items=[]。
- 可选 roundId 是记录 ID，不是轮次号。指定当前用户该岗位下的记录时，返回该记录按排序所在的页。
- roundId 必须为 Long 范围的正整数；记录不在当前用户该岗位下或不存在时忽略定位，按 page 正常查询，不泄露其他岗位/用户的信息。
- 非法页码/定位 ID 返回 400 和固定字段提示，不回显原输入或类型转换异常。
- 登录、岗位归属、404、不缓存和只读 GET 无需 CSRF 的规则保持不变。

这是接口响应结构变更。重启后端后需要刷新新前端；本次已同步所有实际调用与相关测试，无新增 HTTP 文件。

## 文件、数据流和后端学习重点

- common/InterviewRoundPageQuery.java：通过 @ModelAttribute 接收 URL 参数，@Min 校验范围，构造器为 page 设置默认值。
- common/InterviewRoundPageResponse.java：显式定义 items/page/size/total/totalPages；不向前端暴露数据库实体或分页插件对象。
- controller/InterviewRoundReadController.java：接收并校验查询 DTO，从登录身份取得用户 ID。
- service/InterviewRoundReadService.listForUser：检查归属、统计记录数、计算定位页、修正页码，最后只取一条。
- mapper/InterviewRoundMapper.selectPageRow：固定排序，使用绑定参数 LIMIT 1 OFFSET #{offset}。
- mapper/InterviewRoundMapper.countBeforeRound：统计目标记录之前有多少条，再加 1 得到目标页码。
- advice/InterviewRoundExceptionHandler：处理分页类型绑定与范围错误；page/roundId 都使用固定提示。
- frontend/src/components/InterviewRoundsPanel.vue：当前页、总数、上一轮/下一轮、重试参数和保存后定位。
- frontend/src/style.css：沿用侧栏与手机单列，新增分页按钮排列。

```text
点击下一轮 → GET page+1 → Security 登录检查 → MVC 绑定并校验查询 DTO
 → Service 检查岗位 ID + 当前用户 ID
 → COUNT 当前用户该岗位全部轮次
 → 若有有效 roundId，查询目标并统计它之前的记录数
 → 页码限制在有效范围内
 → LIMIT 1 / OFFSET page-1 → 转响应 DTO → 页面只展示 items 中这一条
```

定位的排序条件必须与实际取页相同：

```sql
round_no < 目标轮次号
OR (round_no = 目标轮次号 AND id < 目标记录ID)
```

这就是为什么不能把 roundNo 当页码，也不能只统计更小的 roundNo：历史重复编号还需要用 ID 决定稳定位置。
COUNT、定位和取页全部限制 job_application_id 与 user_id，SQL 值使用 #{...} 绑定，不拼接用户输入。
未引入分页插件或新依赖，也没有修改数据库结构。
多条查询不提供跨请求快照保证；其他标签修改编号时页码和总数可能变化，保存后会重新按 ID 定位。

## 必要的前端数据流

- 组件进入时请求第 1 页；刷新页面或离开再返回恢复第 1 页，不写 URL/浏览器存储。
- 翻页只提交实际页码；成功后使用后端确认的 page/total 更新页面。加载时隐藏旧条目和分页，不伪装成空记录。
- 失败保存本次请求的 page/roundId，重试发送相同查询，而不是误回第 1 页。
- 新增/修改成功后，GET 带保存响应的记录 ID，显示其实际所在页；列表读取失败只重试 GET，不重复写入。
- 普通编辑期间不能翻页；写请求结果不确定时允许只读翻页核对，写操作继续锁定。
- 原 active、请求编号与岗位 key 继续防止旧请求更新新岗位。

## 本机网页验收（待确认）

1. JDK 17 重启后端，刷新前端；无需执行 SQL。
2. 使用专用虚构岗位，准备至少 3 轮，编号可以不连续。每次只显示一条，不再纵向铺开。
3. 上一轮/下一轮可完整查看，首/末页正确禁用；页面位置与业务编号分别显示。
4. 新增一个位于中间或末尾的编号，保存后应直接显示这条记录所在页，数量只增加 1。
5. 刷新恢复第 1 页；历史重复记录若存在，应能逐条翻看且数量不丢失。
6. 可选：第二页加载时断网，再恢复并重试，应仍查询第二页；错误不显示成“暂无轮次”。
7. 手机检查按钮可触达、每页一轮且无横向溢出；真实设备与现有域名由用户验收。

## 2026-10-21 开发验证

- 分页步骤先通过面试相关 53 项测试及前端构建，再进入编辑；最终与编辑一起全量检查。
- 最终 JDK 17 全量 270 项测试、JAR 打包和前端构建通过，0 失败/错误/跳过。
- 分页新增 12 项测试，覆盖固定一条、空页、缺省、超出末页、非连续编号、历史重复稳定排序、定位、跨用户/岗位隔离与非法参数。
- 浏览器隔离 H2 实测 3/1/9 三轮分别定位到对应页，每页 DOM 只有一个轮次；首/末页禁用、刷新回首页。
- 模拟第二页 503 后重试恢复第二页；新增同号仍拒绝且页数不增加。
- 保存与改号后的 ID 定位正确；桌面和 375px 窄屏实际翻页/编辑通过，无横向溢出。
- 未向用户 MySQL 写入测试业务数据，未改变域名/隧道；未新增依赖或 HTTP 文件，未提交/推送 Git。
