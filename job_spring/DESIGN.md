---
name: "岗位投递记录 / JOB TRACKER"
description: "以柔彩、清晰表单和文件夹角色陪伴持续整理的记录空间。"
colors:
  ink: "#2e3457"
  muted: "#68647b"
  surface: "#ffffff"
  ground: "#fbf8f2"
  line: "#e2dce9"
  line-strong: "#9b8da8"
  accent: "#7852a5"
  accent-deep: "#5e3d87"
  primary: "#b23c70"
  primary-hover: "#95305d"
  lavender: "#eee5f8"
  lavender-soft: "#f6f1fb"
  blush: "#fcecf2"
  mint: "#edf7f2"
  sky: "#edf4fb"
  butter: "#fff6d9"
  error: "#9d3d4d"
  success: "#30694e"
  status-waiting-text: "#746028"
typography:
  display:
    fontFamily: '"LXGW Marker Gothic", ui-rounded, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "2rem"
    fontWeight: 400
    lineHeight: 1.35
    letterSpacing: "-0.025em"
  headline:
    fontFamily: '"LXGW Marker Gothic", ui-rounded, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "1.125rem"
    fontWeight: 400
    lineHeight: 1.5
  brand:
    fontFamily: '"LXGW Marker Gothic", ui-rounded, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "1rem"
    fontWeight: 400
    letterSpacing: "-0.02em"
  title:
    fontFamily: 'ui-rounded, "SF Pro Rounded", -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "1rem"
    fontWeight: 700
    lineHeight: 1.6
  card-title:
    fontFamily: 'ui-rounded, "SF Pro Rounded", -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "1.1875rem"
    fontWeight: 700
    lineHeight: 1.5
    letterSpacing: "-0.015em"
  body:
    fontFamily: 'ui-rounded, "SF Pro Rounded", -apple-system, BlinkMacSystemFont, "PingFang SC", "Microsoft YaHei", sans-serif'
    fontSize: "0.9375rem"
    lineHeight: 1.9
  muted:
    fontSize: "0.875rem"
    lineHeight: 1.75
  label:
    fontSize: "0.875rem"
    fontWeight: 600
  button:
    fontSize: "0.875rem"
    fontWeight: 650
    lineHeight: 1.45
rounded:
  control: "10px"
  panel: "16px"
  inset: "12px"
  tab: "8px"
  pill: "999px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "20px"
  panel: "24px"
  roomy: "28px"
  wide: "32px"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.surface}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "11px 18px"
  button-primary-hover:
    backgroundColor: "{colors.primary-hover}"
  button-secondary:
    backgroundColor: "{colors.lavender-soft}"
    textColor: "{colors.accent-deep}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "11px 18px"
  button-danger:
    backgroundColor: "{colors.error}"
    textColor: "{colors.surface}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "11px 18px"
  button-danger-outline:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.error}"
    typography: "{typography.button}"
    rounded: "{rounded.control}"
    padding: "11px 18px"
  button-text:
    textColor: "{colors.accent-deep}"
    padding: "8px 4px"
  input:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "10px 12px"
  nav-tab:
    textColor: "{colors.muted}"
    rounded: "{rounded.tab}"
    padding: "9px 5px"
  nav-tab-selected:
    backgroundColor: "{colors.lavender}"
    textColor: "{colors.accent-deep}"
  status-chip:
    backgroundColor: "{colors.butter}"
    textColor: "{colors.status-waiting-text}"
    rounded: "{rounded.pill}"
    padding: "5px 10px"
  job-card:
    backgroundColor: "{colors.lavender-soft}"
    textColor: "{colors.ink}"
    rounded: "{rounded.panel}"
    padding: "24px"
  job-card-hover:
    backgroundColor: "{colors.lavender}"
---

# Design System: 岗位投递记录 / JOB TRACKER

## Overview

**Creative North Star: "柔彩记录空间"**

用户选定的“角色目录”方向落在奶白底、浅紫导航、柔和彩色卡面和亲近的文件夹角色上。界面保留熟悉的搜索、表单、状态文字和显式保存路径；个性围绕整理记录的任务展开。

正常内容页面保持紧凑且可扫读，角色出现在品牌、登录和初始空状态。页面标题以自托管中文字体形成识别度，岗位数据、说明和控件以平台字体保持清晰。本文记录完成后的实现；前置方向和页面叙事见 `.impeccable/surfaces/frontend-src-app-vue.md`，产品边界见 `PRODUCT.md`。

**Key Characteristics:**

- 奶白背景、浅紫框架与粉色主操作。
- 薄边框、圆润容器、平面柔彩分区。
- 标题有字形个性，业务内容保持清楚的阅读层级。
- 状态文字、键盘焦点与显式保存始终可辨。
- 原创淡紫文件夹角色作为克制的签名。

## Colors

前置 tokens 保留主样式表的原始色值；下文描述用途。侧车中的八阶色带仅用于色彩预览，不是新增的生产色阶。

### Primary

- **莓粉主操作**（`primary`、`primary-hover`）：添加、搜索、登录及保存等明确操作；配白字，悬停加深。

### Secondary

- **墨紫交互**（`accent`、`accent-deep`）：图标、可操作文字、选中栏目、焦点轮廓与边框反馈。
- **柔紫纸面**（`lavender`、`lavender-soft`）：导航、次要操作、选中态与默认岗位卡。

### Tertiary

- **浅粉、薄荷、天蓝**（`blush`、`mint`、`sky`）：岗位卡按顺序循环的装饰底色；薄荷也承载成功与额度区域。
- **奶油黄**（`butter`）：默认“待投递”状态底色，搭配 `status-waiting-text`。
- **结果提示**（`error`、`success`）：失败/危险操作与成功提示。状态胶囊另有已投递、笔试中、面试中、已获 Offer、未通过、已放弃的固定配色，精确映射沿用 `frontend/src/style.css` 与 `frontend/src/job-status.js`，不由岗位卡底色推断。

### Neutral

- **奶白底纸**（`ground`）与**白色面板**（`surface`）：页面与输入、搜索、详情容器分层。
- **深靛蓝正文**（`ink`）与**灰紫说明**（`muted`）：正文、标签及次级信息。
- **浅灰紫边线**（`line`）与**清晰灰紫边线**（`line-strong`）：分隔/容器，以及输入框/滚动条滑块。

**The Text Carries Status Rule.** 状态含义由可读文字承载；圆点与底色补充识别，岗位卡的柔彩循环仅作装饰。

## Typography

**Display Font:** LXGW Marker Gothic / 霞鹜漫黑，后接圆润平台中文 sans 回退栈。仅加载实际存在的常规字重（400）；关闭字体合成。

**Body Font:** `ui-rounded`、SF Pro Rounded 与平台中西文 sans 栈。数据、岗位卡标题、表单与按钮使用此栈。

标题的亲近字形与正文的清楚节奏形成分工。完整字体从官方 Google Fonts 仓库取得，转换后自托管于 `frontend/public/fonts/lxgw-marker-gothic.woff`，采用 `font-display: swap`；来源及 OFL 许可随字体保存，不请求外部字体服务。

- **Display**：页面 h1 使用 `display`；窄屏缩至（1.625rem）。登录引导另用（2.25rem / 1.5），900px 以下（1.875rem），700px 以下（1.5rem）；登录面板标题（1.75rem）。
- **Headline**：区块 h2 使用 `headline`，保持常规字重。
- **Title**：内容 h3 使用 `title`；岗位卡采用 `card-title`，优先识别岗位名称。
- **Body**：长描述使用 `body`，最大行长（72ch），保留换行并允许任意长词折行。页面简介为（0.9375rem / 1.8）。
- **Label / helper**：主要标签使用 `label`；常见说明使用 `muted`，小提示（0.8125rem），状态标签（0.75rem）。输入文字（16px）避免手机输入时缩放。

**The Titles and Records Rule.** 自托管字体用于品牌与 h1/h2；h3、岗位数据、正文、标签和表单保留平台字体，不把标题字形扩散到信息密集区。

## Layout

外层居中容器上限（1220px，包含左右各 32px 内边距）；头部至少（88px）高。主内容上下留白（40px / 48px），列表双列、列间距（16px）。搜索在列表上方，状态胶囊固定在岗位卡内部右上，岗位名称、元数据与详情入口依次展开。卡内文本可以折行，不做固定高度裁切。

新建、编辑和面试回顾内容上限（928px）；常规表单双列，间距（28px 24px）。详情以（1.35fr / minmax(360px, 1fr)）分为资料和操作两列。登录为引导区加最大（420px）的表单区，整体上限（1040px）。这些是按任务使用的布局，不要求所有页面复刻同一种构图。

- **960px 以下**：详情顺序堆叠。
- **900px 以下**：登录双列收紧，表单列上限（380px）。
- **700px 以下**：外侧内边距（16px）；列表、搜索、主表单与登录单列，面板（20px）内边距，岗位卡间距（12px），主内容留白（28px / 36px）。登录角色收至（88px），在引导右侧。
- **500px 以下**：识别草稿的标签/值从两列转为顺序单列。
- **380px 以下**：外侧内边距（12px），面板和岗位卡内边距（16px）。

常见内部节奏取自 `spacing`；大块面板使用（28px），操作/搜索/桌面卡多用（24px）。长账号在桌面最多（180px），手机（72px），最窄屏（56px）并省略，完整内容保留在 title；品牌与退出文字不换行，手机隐藏头像和账号副说明。所有网格内容列允许缩小，长公司名、岗位名和提示可折行。页面最低宽度为（280px）。

## Elevation & Depth

界面没有投影、毛玻璃或悬浮位移；深度由奶白底、白面板、柔彩内层和细边框表达。角色图片内的轻微绘制明暗属于插画材质，不是界面阴影。

**The Tonal Layers Rule.** 容器依靠色面与边线分层；交互反馈改变底色和边框，不给普通卡片增加浮起效果。

按钮、输入和岗位卡的状态过渡为（180ms，`cubic-bezier(.16, 1, .3, 1)`），不做页面入场动画。骨架加载以（1.4s，ease-in-out）交替柔紫底色；减少动态效果时关闭动画与过渡。侧车保存这些 schema 外的运动和断点值。

## Shapes

主要容器使用 `panel` 圆角，输入与常规按钮使用 `control`；内部确认区、截图选择区、额度条和栏目轨道使用 `inset`，栏目按钮使用 `tab`。状态胶囊使用 `pill`。角色的柔和轮廓是签名，业务区域仍是边界清楚的矩形。

边线通常（1px），输入框用更清楚的边线色；上传区域以虚线表达接收范围。头像与状态圆点为圆形。滚动条轨道沿用奶白底、滑块沿用清晰灰紫，悬停为墨紫；WebKit 滚动条（12px）宽、滑块（3px）轨道色内边线，同时提供标准 `scrollbar-color`。

## Components

### Buttons

圆润、明确，常规按钮最小高度（44px），搜索操作与输入对齐为（46px）。主、次、危险按钮使用前置组件 tokens；次按钮带浅边线，悬停转柔紫并加深边线。主按钮按下进一步变深，次按钮按下转深柔紫。危险操作分为深红实心确认按钮与白底红字描边入口；文本按钮保留下划线与（44px）触控高度。

禁用按钮透明度（0.55），使用不可操作光标，不触发专属 hover 反馈。所有可操作控件保留（3px）墨紫焦点轮廓与（4px）外偏移；跳至主要内容链接只在聚焦时显示。不要用 hover 替代键盘焦点。

### Inputs / Fields

白底、清晰细边框，最小高度（46px）；悬停/聚焦转墨紫边线。错误字段由 `aria-invalid` 指示，配红色边线、近白粉底和字段错误文字；禁用字段灰紫底、保留文字可读性。标签始终可见，帮助与错误通过描述关系关联；textarea 可垂直调整，主岗位表单最小高（144px），面试表单（120px）。紧凑的每页条数 select 是（38px）高的明确例外。

### Navigation

浅紫品牌头部承载首页入口和账号。详情栏目采用白色圆角轨道上的三等分按钮，最小高（72px），图标在文字上方；选中项柔紫底与深紫字，hover 使用更浅柔紫，选中项 hover 保持选中色。保留实际 tab 的 ARIA 关系、方向键和焦点行为，不仅绘制外观。返回链接与分页继续使用清楚的文字和现有按钮。

### Chips

状态胶囊以小圆点加文字组成，不作为可点击按钮，也不加无意义 hover。所有七种状态都沿用既有文字映射；未知状态显示“未知状态”。知识主题是独立的浅紫小标签，不能和岗位状态混用。

### Cards / Containers

岗位卡是整块可聚焦链接，四种柔彩按顺序循环；hover 与键盘聚焦同时转为柔紫底、墨紫边框，并保留全局焦点轮廓。公司、岗位、城市/薪资、详情入口组成稳定层级。普通搜索、资料和操作面板为白底薄边框；错误、等待、成功和空结果各有准确文字，不靠插画表达结果。

### Folder Companion

淡紫文件夹抱着薄荷便签是唯一签名插画；品牌小图（桌面 54px / 手机 44px）、登录（桌面 208px / 手机 88px）和初始空状态（112px）使用同一透明 PNG。它是装饰性陪伴，图片 alt 为空，必要信息由邻近文字提供，不在正常记录卡里重复。资产为 `frontend/src/assets/record-pal.png`，提示词见 `.impeccable/record-pal.prompt.txt`；发布 PNG 内已嵌入生成来源。

## Do's and Don'ts

### Do:

- **Do** 延续奶白底、柔彩色面和清晰薄边框，用粉色标识主操作。
- **Do** 让岗位标题、状态文字与当前任务先被读到，角色仅陪伴品牌、登录和初始空状态。
- **Do** 保留键盘焦点、可见标签、帮助与错误文字，以及减少动态效果支持。
- **Do** 让长内容折行，按既有断点堆叠，保护品牌和退出入口。
- **Do** 清楚区分识别草稿、最终保存、危险确认及结果未知状态，沿用原有操作含义。

### Don't:

- **Don't** 把循环卡面颜色当作投递状态，或仅用圆点传达结果。
- **Don't** 给正常任务区增加重复角色、入场动画、阴影浮起或占据操作空间的装饰。
- **Don't** 将标题字体用于数据与表单，合成不存在的标题粗体，或引入外部字体请求。
- **Don't** 为统一高度裁切公司名、岗位名、错误说明或面试记录。
- **Don't** 用颜色变化伪装保存成功、绕过确认，或改变已有 API 与业务能力。
