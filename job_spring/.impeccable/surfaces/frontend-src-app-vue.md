---
version: 1
slug: "frontend-src-app-vue"
primary_target: "frontend/src/App.vue"
related_targets: ["frontend/src/views/JobsView.vue","frontend/src/views/AuthView.vue","frontend/src/views/JobDetailView.vue","frontend/src/views/JobCreateView.vue","frontend/src/views/JobEditView.vue","frontend/src/views/InterviewReviewView.vue"]
---

# 全站视觉更新

Mode: Operate。范围包含现有登录、注册、岗位列表、新增、编辑、详情、面试回顾及所有状态；只调整前端展示。2026-11-02 用户在选择页明确选择“角色目录”，采用 code-led，不需要另一轮方案选择。

## Direction contract

THESIS: 用“柔彩记录空间”让持续整理求职信息更亲近；保留熟悉的网页表单、筛选与显式保存路径，拒绝装饰占据任务区域。

OWN-WORLD: 奶白底、浅紫导航、粉色主操作与薄荷/天蓝分区，深靛蓝正文。圆润 sans 和清晰字重；12–16px 容器圆角，控件柔和但边界可见。原创的淡紫文件夹角色作为唯一签名插画，出现在品牌、登录和空状态，正常列表让内容优先。

STORY: 登录后先看岗位与已保存状态；可搜索、进入详情、维护面试轮次和复盘。新增岗位先呈现截图辅助与手动表单，额度/隐私/草稿/最终保存关系保持明确。

FIRST VIEWPORT: 顶部柔紫品牌导航与账号；白底任务标题旁直接放“添加岗位”；搜索与状态筛选在内容上方，桌面双列紧凑岗位卡，状态文字固定在卡内右上，手机单列。详情保持岗位信息和操作区两栏，窄屏顺序堆叠。签名交互为岗位条目悬停/键盘聚焦时淡紫底与轮廓一起响应；150–200ms，无入场动画，尊重减少动态效果。

FORM: 用户选中 challenger pop-culture-shelf-character-goods-catalog，覆盖原候选判断。seed:73c2871ef30298f8。只将所选参考的字体、色彩、密度和角色签名转译到工作界面，实际布局按任务决定。

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance

## 验证边界

只用虚构内存接口替身进行网页检查；不启动连接正式数据库的 Java，不调用真实模型或额度。前端自动测试和构建、隔离网页检查、用户真实环境验收分别记录。已有私有模型、数据库与 Redis 配置不改。
