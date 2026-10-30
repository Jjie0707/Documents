# 阶段 5 · 5.3c：手机选图、照片方向与长图预览

2026-10-30，用户明确确认 Android 和 iOS 均可正常上传、拍照，**5.3c 验收通过**；不推断未逐项反馈的格式或权限场景。本轮 [5.4 草稿契约与校验](recognition-draft.md)已完成开发和自动验证，待本项验收。下方为 5.3c 的历史开发记录，其中待真机验收状态由本段更新。

## 本项范围

- 保留普通选图入口，由系统提供相册/文件选择；浏览器声明支持 input.capture 时，另外显示“拍照选图”。普通入口不加 capture，避免相机入口影响从相册选择。
- 拍照入口使用独立的 `accept="image/*" capture="environment"` 文件输入；具体是否打开相机、使用哪台相机、格式与权限交互由系统决定。用户取消或无法拍照时，可继续使用普通选图。没有页面内摄像头预览或 getUserMedia 权限申请。
- 明确提示当前不支持 HEIC/HEIF，建议导出 JPEG/PNG 或重新截图，不提供自动格式转换。系统可能已转换文件：明确 MIME 为 JPEG/PNG 时不会仅因旧 HEIC 扩展名拒绝，最终由 Java 按真实内容确认。
- 增加“展开预览/收起预览”，长图按可用宽度展示，并在最多 70vh 的区域内滚动；默认仍为缩略预览。替换/移除重置展开状态。
- 预览使用 `image-orientation: from-image`，由浏览器读取照片方向；不重编码、不压缩、不修改原文件。用户可核对文字方向和清晰度。

拍照的 capture 是提示，不保证所有设备都出现相同选择器或一定具有相机；见 [MDN capture](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Attributes/capture) 与 [capture 属性](https://developer.mozilla.org/en-US/docs/Web/API/HTMLInputElement/capture)。方向显示依据 [MDN image-orientation](https://developer.mozilla.org/en-US/docs/Web/CSS/Reference/Properties/image-orientation)。资料核对日期为 2026-10-30。

## 文件、数据流与后端学习点

本项业务代码只修改 `frontend/src/components/ScreenshotUploadPanel.vue`、`frontend/src/screenshot-input.js`，并扩充 `frontend/tests/screenshot-input.test.js`。Java、数据库设计、依赖、接口和岗位表单保存流程均未修改。

```text
相册/文件选择或系统拍照返回 File
  → selectFile（取消选择保留原图）
  → selectSingleScreenshot（单张、非空、5 MiB、HEIC/HEIF 提示）
  → receiveSelection（废弃旧响应、清理旧 Blob URL、建立新预览）
  → 用户查看方向/长图后点击检查
  → FormData(image) + 登录会话/CSRF
  → ImageValidationController.validate
  → ImageValidationService.validate → inspect
  → 元数据或固定错误 → 页面显示
```

后端继续复用 [5.3a 的 Java 实现](screenshot-upload.md)：

1. `ImageValidationController.validate` 检查文件部分只有一个 image，再调用 Service。手机、电脑最终提交同一种 multipart 请求，因此不需要按平台新增接口。
2. `ImageValidationService.validate` 用 `readNBytes(MAX_BYTES + 1)` 限制真实读取量，声明大小和实际数据都要符合上限。
3. `inspect` 通过 ImageIO 判断实际 PNG/JPEG、原始宽高与总像素，再检查结尾和完整解码。`(long) width * height` 防止乘法溢出；先检查尺寸，再分配完整像素缓冲区。
4. 响应中的宽高是文件原始像素，并非照片方向调整后的显示宽高。例如文件为 640 × 320、EXIF 方向为 6，浏览器可显示为 320 × 640，而 Java 返回 640 × 320。两者都应保留含义，不能把不同宽高误报为内容损坏。

前端格式提示依据文件声明，只用于快速反馈；后端校验仍为最终规则。5 MiB、单边 10000、总像素 1200 万和 60 秒有限上传超时保持。错误不阻止手动岗位保存，检查不会自动填表、创建岗位或调用模型。后续 5.7 必须另行验证服务商对原文件方向信息的处理，当前预览验证不代表模型已能正确处理所有照片方向。

## 本轮验证结果

- 自动验证：前端 **20 项通过**（原 17 项 + 3 项 HEIC/HEIF 兼容回归），生产构建和 `git diff --check` 通过。新增测试覆盖 HEIC/HEIF MIME、序列类型与大小写、缺少具体 MIME 时的扩展名提示、系统转换后保留旧扩展名；规则同时用于普通选择、拖拽和图片粘贴。
- 隔离网页使用内置浏览器、127.0.0.1 临时服务、H2 内存库和虚构账号/图片。普通选择 JPEG、重新选择同一文件、格式错误后恢复、保留手动字段、HEIC 错误时仍能手动保存虚构岗位均通过。
- 800 × 9000 的虚构 PNG 长图通过真实 Java 校验；展开后内容高于预览容器，可在区域内滚动。375px 布局无横向溢出。
- 合成的 EXIF 方向 6 JPEG 预览为竖向 320 × 640，顶部/底部文字方向正确，Java 返回原始 640 × 320。替换后展开状态复位。
- 以 HEIC 名称/MIME 声明的合成样本在前端提示转换，未作为真实手机 HEIC 解码测试。5 MiB + 1 字节在前端拒绝；270 × 10001 和 2000 × 7000 的真实 PNG 经后端分别因单边/总像素超限被拒绝。
- 桌面内置浏览器没有声明 capture 支持，页面保留普通入口，未伪装成手机完成相机测试。工具不支持以空文件列表模拟取消；真机取消、拒绝权限及返回选择器仍待验收。
- 本轮未修改 Java，未重跑 Maven 或 JAR；**495 项后端测试/JAR 属于 5.3a 历史验证**，不能与 20 项前端测试合计。

隔离页面和服务已关闭，视口恢复。用户 8082/5173 服务未重启，无用户 MySQL 操作、生产 SQL、私密配置/域名/隧道修改、模型调用或 Git 提交/推送/合并。开始时为 main、cf36f0e，工作区干净；本项改动保留待用户处理。

## 真机验收（待用户完成）

1. 刷新现有域名页面，进入“添加岗位”。本项只改前端，无需重启后端、执行 SQL 或配置模型密钥。
2. iOS Safari、Android Chrome：分别从相册/文件选择 PNG/JPEG 截图，预览后点击检查，确认格式与尺寸；取消选择后原图与手动字段应保留。
3. 若显示“拍照选图”，拍摄无隐私的虚构文字样例；确认方向、预览与检查。取消或拒绝相机权限后，普通选图仍可用。没有此按钮时使用普通入口，并记录设备和浏览器。
4. HEIC/HEIF 若保持原格式，应提示转换；若系统自动转为 JPEG/PNG，可正常检查。不能只改扩展名；实际格式仍由后端校验。
5. 选择长截图，展开、滚动、收起并核对文字清晰度；横拍/竖拍核对方向。超大文件按提示裁去无关内容后重试。
6. 替换/移除、重复选同一文件、检查中换图，并确认手动字段保持。只有主动保存才创建岗位。

请按实际设备反馈，未测试的设备继续记为待验收；窄屏截图不替代真机相册、权限和相机验证。本项验收后，下一项为 5.4 识别草稿契约与结果校验。
