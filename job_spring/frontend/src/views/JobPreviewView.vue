<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <section aria-labelledby="detail-heading">
    <RouterLink to="/" class="back-link"><span aria-hidden="true">←</span> 返回岗位列表</RouterLink>
    <div class="page-heading">
      <div>
        <p class="eyebrow">示例公司 · 静态示例</p>
        <h1 id="detail-heading">Java 后端开发工程师</h1>
        <p class="page-description">岗位详情布局预览，以下信息不会保存到数据库。</p>
      </div>
      <span class="outline-label">页面骨架预览</span>
    </div>

    <div class="detail-layout">
      <article class="info-panel">
        <div class="panel-topline"><h2>岗位信息</h2><span class="sample-label">示例</span></div>
        <dl class="job-facts">
          <div><dt>公司</dt><dd>示例公司</dd></div>
          <div><dt>城市</dt><dd>示例城市</dd></div>
          <div><dt>薪资</dt><dd class="muted">待填写</dd></div>
          <div><dt>来源平台</dt><dd class="muted">待填写</dd></div>
          <div><dt>学历要求</dt><dd class="muted">待填写</dd></div>
          <div><dt>截止日期</dt><dd class="muted">待填写</dd></div>
        </dl>
        <div class="description-block"><h3>岗位职责</h3><p>这里将展示录入的岗位职责。</p></div>
        <div class="description-block"><h3>任职要求</h3><p>这里将展示录入的任职要求。</p></div>
      </article>

      <aside class="detail-sidebar" aria-label="岗位相关操作">
        <section class="action-panel" aria-labelledby="interview-heading">
          <span class="section-number" aria-hidden="true">01 / 面试</span>
          <h2 id="interview-heading">面试记录</h2>
          <p>按轮次整理问题、回答要点和复盘。</p>
          <button class="secondary-button" disabled>查看面试记录 <span aria-hidden="true">↗</span></button>
          <small class="muted">阶段 4 开放</small>
        </section>
        <section class="action-panel" aria-labelledby="status-heading">
          <span class="section-number" aria-hidden="true">02 / 进度</span>
          <h2 id="status-heading">投递状态</h2>
          <label for="job-status" class="field-label">当前状态</label>
          <select id="job-status" disabled aria-describedby="status-help">
            <option>待投递</option><option>已投递</option><option>笔试中</option>
            <option>面试中</option><option>已获 Offer</option><option>未通过</option><option>已放弃</option>
          </select>
          <small id="status-help" class="muted">阶段 3 接入状态修改</small>
        </section>
      </aside>
    </div>
  </section>
</template>
