# 阶段 5.8：识别草稿预填、检查与保存

2026-11-01：用户授权将 5.8 和 5.9 一起完成；本项已实现；用户随后明确验收完毕，手机与电脑均可成功识别并填入，阶段五验收通过。回归证据和真机待验项目见 [阶段五验收记录](stage5-acceptance.md)。

## 使用流程与边界

1. 新增岗位页选择截图，点击“识别岗位”。识别只生成当前页面的临时草稿，不创建岗位。
2. 点击“填入岗位表单”。空白字段可以填入；已填写且与草稿不同的字段会触发确认，并列出冲突字段名称。
3. 可选“仅补充空白字段”“覆盖冲突字段并填入”或“取消填入”。确认期间截图操作和表单字段禁用，避免确认时的内容发生变化；取消后焦点返回填入按钮，Esc 也可取消。
4. 检查和修改表单，补齐必填公司名、岗位名，再点击“保存岗位”。成功后进入详情页，初始状态仍为“待投递”。

模型缺失、null、空白或非文本字段不参与填入，始终不会清空已有手动输入。相同内容不算冲突，也不改写原输入的首尾空白。多行职责、要求保留内部换行。只允许十个岗位内容字段进入补丁；主键、归属、状态和审计信息不来自草稿。

替换/移除截图、清除草稿或再次识别会清理截图组件中的旧草稿，已经填入表单的内容仍保留。再次识别后的填入同样需要主动点击和冲突确认。取消填入完全不修改表单；取消新增返回列表，不保存。刷新或离开页面会丢失未保存的表单/草稿，没有草稿表、草稿持久化 API 或自动保存。

保存中禁用表单、截图和保存操作。网络异常、超时、响应 JSON 损坏或成功响应缺少有效 ID 时，服务端可能已经写入：保留输入、显示结果不确定提示并禁用本页再次保存，请先返回列表核对；确认没有记录后再重新添加。这是页面防重复措施，没有新增服务端幂等键，不能保证刷新/多个标签页提交永不重复。

## 文件与必要的前端数据流

| 文件 | 职责 |
| --- | --- |
| `frontend/src/components/RecognitionDraftPanel.vue` | 只读展示十字段草稿与下一步提示，使用文本插值，HTML 内容不会作为标签执行。 |
| `frontend/src/components/ScreenshotUploadPanel.vue` | 保持图片、识别结果和额度状态；点击按钮发出 `apply-draft`，不直接调用岗位保存接口。 |
| `frontend/src/recognition-prefill.js` | `snapshotRecognition` 获取独立白名单快照；`recognitionConflicts` 找出差异；`recognitionPatch` 只生成补丁，不直接修改表单。 |
| `frontend/src/views/JobCreateView.vue` | `requestPrefill` 决定是否确认，`applyPrefill` 合并补丁，`cancelPrefill` 取消；`submit` 继续提交现有岗位 API。 |
| `frontend/tests/recognition-prefill.test.js` | 验证部分识别、只补空白/覆盖、取消前不突变、重识别保留人工修改、十字段与多行内容。 |

有冲突时暂存的是独立快照，不是截图组件可变化的引用；实际合并前没有字段修改，因此取消不需要回滚。合并后只清除本次更新字段的旧校验提示；其他错误和保存不确定状态仍保留。前端预填不做额外识别，也不消耗额度。

## 后端执行顺序与学习重点

生产 Java、本次数据库结构与 API 无需改动；本轮新增两项跨接口集成测试，确认识别和原保存流程可以安全组合。

### 识别阶段

`VisionExtractionController.extract` → `VisionExtractionService.extract` → 图片校验和 Redis 原子预占 → `GlmVisionClient.extract` → `RecognitionResultService.parseAndValidate` → 十字段 `JobRecognitionDraft`。

识别接口要求登录和 CSRF；服务端校验单图真实格式、体积、像素，Redis 同时检查 IP 30/日和全站 300/日，再调用模型一次。模型字符串必须经过严格 JSON、字段白名单、类型、长度、网址、日期和非空检查；通过语法/类型校验仍可能语义识别错误，必须人工核对。识别服务不调用 `JobMapper.insert`。

### 用户保存阶段

`POST /api/jobs` → Spring Security → `CreateJobRequest` 构造及 Bean Validation → `JobCreateController.create` → `JobCreateService.createForUser` → `JobMapper.insert` → `JobResponse` → HTTP 201 与详情地址。

- `CreateJobRequest` 是输入 DTO。紧凑构造器先用 `strip()` 去首尾空白，选填空白转 null；`@NotBlank` 保证公司名/岗位名，`@CodePointLength` 计算 Unicode 码点；自定义 URL/日期规则保证最终手动修改也受约束。
- `JobCreateController.create` 的 `@Valid` 在进入业务前完成校验，`@AuthenticationPrincipal` 取会话用户，不信任请求中的 `userId`。控制器负责 HTTP 201、Location 和 no-store，业务逻辑留在 service。
- `JobCreateService.createForUser` 在 `@Transactional` 方法中创建实体、逐项拷贝十字段，显式 `job.setUserId(currentUserId)`、`job.setStatus(JobStatus.TO_APPLY.name())`，再 `mapper.insert(job)`；数据库生成 ID 并由 MyBatis-Plus 回填。
- `JobMapper` 继续负责持久化；识别调用耗时不会占用创建岗位的数据库事务。Redis/模型失败也不参与手动保存流程，所以手动录入可以独立工作。
- 最终表单和模型响应是两个不同边界：草稿可缺字段，最终岗位必须完整；前端校验改善操作体验，后端校验负责真实约束。保存后详情读取继续按当前用户隔离。

`VisionExtractionIntegrationTests` 新增“部分草稿 → 补齐/人工修改 → CSRF 检查 → 保存 → 本人可读/他人 404”和“Redis 失败 → 不调用模型 → 手动创建成功”两项测试；使用独立 H2、事务回滚、虚构账户及受控模型/Redis，没有创建测试业务 API。

## 本机网页验收

本次生产改动只涉及前端，不需要数据库迁移；原后端、Redis 和模型配置正常运行即可。Vite 开发页刷新加载；静态部署应使用新的 `frontend/dist`。

1. 登录并进入“添加岗位”，先手动填写公司名/城市，再识别一张招聘截图。确认识别完成后表单尚未改变。
2. 点击“填入岗位表单”，先取消，再选择仅补空白；检查原有字段保留。再点填入并确认覆盖，检查只覆盖列出的差异字段。
3. 修改岗位名或职责，清除草稿/移除截图，确认手动修改仍在；再识别也不会自动覆盖。
4. 补齐必填项并保存，详情页应显示人工修正后的内容、正确日期/换行和“待投递”，返回列表只新增一条。
5. 在自有域名和手机上各走一次完整流程，重点检查确认按钮、滚动、返回和保存。这部分本轮没有代替真实设备测试。

阶段五开发、隔离回归及用户验收均已完成；阶段六尚未实施，后续按[交接说明](stage5-handoff.md)在新对话中先核对和规划。
