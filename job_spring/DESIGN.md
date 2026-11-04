---
name: "岗位投递记录 / JOB TRACKER"
description: "以左侧栏、表格行和克制蓝色主操作构成的求职记录工作台。"
colors:
  ink: "#24314a"
  muted-strong: "#475467"
  muted: "#667085"
  subtle: "#98a2b3"
  surface: "#ffffff"
  bg-soft: "#f8fafc"
  sidebar: "#f2f4f7"
  line: "#e6eaf0"
  line-strong: "#d8dee8"
  primary: "#2563eb"
  primary-hover: "#1d4ed8"
  primary-soft: "#edf3ff"
  primary-text: "#1e54c8"
  success: "#28764b"
  success-soft: "#e8f6ee"
  error: "#b42318"
  error-soft: "#fef3f2"
  warning: "#926214"
  warning-soft: "#fff5e4"
typography:
  display:
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "30px"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "-0.025em"
  headline:
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "18px"
    fontWeight: 650
    lineHeight: 1.45
  title:
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "16px"
    fontWeight: 600
    lineHeight: 1.5
  body:
    fontFamily: 'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "15px"
    lineHeight: 1.6
  muted:
    fontSize: "14px"
    lineHeight: 1.65
  label:
    fontSize: "14px"
    fontWeight: 550
  button:
    fontSize: "14px"
    fontWeight: 550
    lineHeight: 1.4
rounded:
  control: "8px"
  input: "7px"
  panel: "12px"
  pill: "6px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  panel: "24px"
  roomy: "28px"
  wide: "48px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.muted-strong}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    height: "44px"
  button-danger:
    backgroundColor: "{colors.error}"
    textColor: "{colors.surface}"
    rounded: "{rounded.control}"
    height: "44px"
  button-danger-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.error}"
    rounded: "{rounded.control}"
    height: "44px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.input}"
    height: "44px"
  nav-item:
    textColor: "{colors.muted-strong}"
    rounded: "{rounded.control}"
    height: "46px"
  nav-item-active:
    backgroundColor: "{colors.primary-soft}"
    textColor: "{colors.primary-text}"
  status-chip:
    rounded: "{rounded.pill}"
    padding: "5px 10px"
  table-row:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.panel}"
    height: "84px"
  table-row-hover:
    backgroundColor: "#fbfcff"
---

# Design System: 岗位投递记录 / JOB TRACKER

## Overview

**Creative North Star: "清晰的工作台"**

2026-11-04 用户确认从"角色目录"（柔彩、圆角、文件夹角色）整体换向到"现代工具界面"：左侧固定栏、白色主区、表格行列表、单一蓝色主操作。目标是让"打开页面三秒内看清哪些岗位在推进"，视觉个性让位于信息密度和扫描效率。

本次只改视觉层：接口、路由、校验、确认与结果不确定时的保护逻辑全部沿用，未新增任何业务能力。改动集中在 `frontend/src/style.css`、`frontend/src/styles/layout.css`、`App.vue`、`JobsView.vue`、`AuthView.vue`、`NotFoundView.vue`。

**Key Characteristics:**

- 浅灰侧栏 + 白色主区，两段式结构，无卡片阴影堆叠。
- 岗位列表以表格行呈现，不是卡片墙。
- 单一蓝色主操作；状态用低饱和胶囊色区分。
- 公司以首字母方块标识，按行序四色循环，不承载状态含义。
- 无插画、无入场动画，交互反馈只改背景色和边框色。

## Colors

### Primary

- **工具蓝**（`primary`、`primary-hover`）：添加、搜索、登录、保存等唯一主操作；配白字，悬停加深。
- **浅蓝底**（`primary-soft`、`primary-text`）：侧栏选中项、知识主题标签、聚焦光晕。

### Semantic

- **成功绿**（`success`、`success-soft`）：保存成功、额度充足。
- **错误红**（`error`、`error-soft`）：校验失败、危险操作、删除确认区。
- **警示黄**（`warning`、`warning-soft`）：面试中状态、待处理提示。

### Neutral

- **侧栏灰**（`sidebar`）：左侧固定导航底色。
- **浅底**（`bg-soft`）：筛选区、表头、空状态、折叠内容块。
- **正文墨蓝**（`ink`）与**次级灰**（`muted-strong`、`muted`、`subtle`）四级文字层次。
- **边线**（`line`、`line-strong`）：分隔与容器；输入框用较深的一档。

### 状态色映射

状态含义由可读文字承载，颜色只做辅助。七种状态固定映射，见 `frontend/src/style.css`：

| 状态 | 底色 | 文字 |
|---|---|---|
| 待投递 | `#f0f2f5` | `#5c6879` |
| 已投递 | `#edf3ff` | `#285ab4` |
| 笔试中 | `#f1edfa` | `#6b4fa0` |
| 面试中 | `#fff5e4` | `#926214` |
| 已获 Offer | `#e8f6ee` | `#28764b` |
| 未通过 | `#fef3f2` | `#b42318` |
| 已放弃 | `#eef0f3` | `#667085` |

公司首字母方块的四种底色（蓝、青、沙、紫）仅用于区分相邻行，不得用于表达状态。

## Typography

**字体栈：** `Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif`。

不加载外部字体。原 `LXGW Marker Gothic` 手写体已停用（字体文件仍保留在 `frontend/public/fonts/`，不再被引用），因为马克笔字形与工具界面的信息密度冲突。

- **Display**：页面 h1 用 30px / 700 / -0.025em；900px 以下 26px，700px 以下 23px。
- **Headline**：区块 h2 用 18px / 650。
- **Title**：岗位名、面板标题用 16px / 600。
- **Body**：正文 15px / 1.6；说明文字 14px / 1.65。
- **Label**：表单标签 14px / 550，颜色用 `muted-strong` 而非纯黑。
- **数字**：薪资用 `font-variant-numeric: tabular-nums`，保证表格列对齐。

## Layout

- **骨架**：`grid-template-columns: 244px minmax(0, 1fr)`；侧栏 `position: sticky`、高 `100dvh`。
- **主区**：`#main-content` 最大宽 1200px，内边距 40px / 48px；1080px 以下 32px。
- **列表**：表格行，五列 `minmax(0,1fr) 96px 148px 124px 24px`，行高 84px，表头 46px。
- **详情**：`1.4fr / minmax(340px, 1fr)`，960px 以下堆叠。
- **表单**：最大宽 920px，双列，间距 20px。
- **登录**：单卡片，最大宽 408px，垂直居中。

### 断点

- **1080px**：主区内边距收至 32px，表格列宽收紧。
- **960px**：详情两栏堆叠。
- **900px**：侧栏转为顶部横条（sticky），导航隐藏，列表从表格行转为两列卡片（岗位/状态一行，城市/薪资一行）。
- **700px**：内边距 16px，表单单列，筛选区纵向堆叠。
- **380px**：内边距 12px，账号名截断更早。

## Elevation & Depth

界面基本无阴影。深度靠侧栏灰底与主区白底的分区、以及 1px 边线表达。

例外两处：主按钮 `0 1px 2px rgba(37,99,235,.18)`，登录卡片 `0 1px 2px + 0 12px 32px -12px` 的轻浮起。侧栏栏目切换的选中项用 `0 1px 2px` 微阴影表达"白纸贴在灰底上"。

**The Flat Surface Rule.** 除上述三处，不给普通容器加阴影或悬浮位移；交互反馈只改背景和边框。

## Shapes

- 按钮、导航项：8px。
- 输入框、下拉：7px。
- 面板、表格容器、筛选区：12px。
- 状态胶囊：6px（不是全圆角，与表格的方正感一致）。
- 首字母方块：10px。
- 头像：圆形。

滚动条 10px，滑块 `#cfd6e2`，悬停加深。

## Components

### 按钮

最小高度 44px，横向内边距 18px。主按钮蓝底白字带极轻投影；次按钮白底 + `line-strong` 边线，悬停转浅灰底；危险按钮红底白字；危险描边按钮白底红字、边线 `#f0c9c4`。文本按钮无下划线，悬停才出现。

禁用统一 `opacity: .5`。所有控件保留 2px 蓝色焦点轮廓。

### 输入框

白底、44px 高、7px 圆角、`line-strong` 边线；悬停边线转 `subtle`，聚焦转 `primary` 并带 2px 浅蓝光晕。错误态由 `aria-invalid` 驱动，边线转红、底色转 `#fffbfa`。

筛选区和问题搜索区使用 `bg-soft` 底色，让白底输入框在浅灰上有足够对比。

### 侧栏

244px 固定栏，浅灰底，右侧 1px 边线。品牌区为蓝色圆角方块 + 图标 + 名称 + 英文副标。导航项 46px 高，选中态浅蓝底 + 深蓝字 + 图标变蓝。

底部依次是连接状态（12px 小字）和账号区（头像 + 用户名 + 退出）。900px 以下整条转为顶部横条，导航隐藏，连接状态隐藏。

### 表格行

整行是链接。列：岗位/公司（首字母方块 + 岗位名 + 公司名）、城市、薪资、状态胶囊、右箭头。悬停整行转 `#fbfcff`，箭头右移 2px 并转蓝。

表头 `bg-soft` 底、13px 灰字，仅作视觉引导，`aria-hidden`。

### 状态胶囊

`::before` 伪元素画 6px 圆点，颜色取 `currentColor`，文字与圆点同色。不可点击、不加 hover。

### 空状态

`bg-soft` 底 + 虚线边框 + 居中图标 + 标题 + 说明。不再使用插画角色。

### 图标

`AppIcon.vue` 内联 SVG，24×24 viewBox，`stroke-width: 1.7`，`stroke: currentColor`。新增 `list` 图标用于侧栏导航。

## Do's and Don'ts

### Do:

- **Do** 保持侧栏灰 + 主区白的两段式结构，让内容区永远是视觉焦点。
- **Do** 用表格行承载列表，信息按"岗位 → 公司 → 城市 → 薪资 → 状态"横向扫描。
- **Do** 让蓝色只出现在主操作、选中态和聚焦上，其余用中性色。
- **Do** 保留键盘焦点、可见标签、错误文字和 `prefers-reduced-motion` 支持。
- **Do** 让长公司名、岗位名折行，不裁切。

### Don't:

- **Don't** 用首字母方块的颜色表达投递状态。
- **Don't** 给表格行加阴影、放大或入场动画。
- **Don't** 引入外部字体请求，或把显示字体用于表格数据。
- **Don't** 为统一高度裁切公司名、岗位名或错误说明。
- **Don't** 用颜色变化伪装保存成功，或改变已有接口与业务能力。
