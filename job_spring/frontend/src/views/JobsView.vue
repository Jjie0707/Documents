<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <section aria-labelledby="jobs-heading">
    <div class="page-heading">
      <div>
        <p class="eyebrow">我的求职记录</p>
        <h1 id="jobs-heading">岗位投递</h1>
        <p class="page-description">记录岗位，跟进进度，留下每一轮面试的思考。</p>
      </div>
      <span class="outline-label">页面骨架预览</span>
    </div>

    <div class="workspace-panel">
      <div class="panel-topline">
        <h2>岗位记录</h2>
        <span class="muted">静态示例 · 不会保存</span>
      </div>

      <RouterLink to="/jobs/preview" class="job-card" aria-label="查看示例岗位的详情布局">
        <div class="job-card-content">
          <p class="company-name">示例公司 <span class="sample-label">示例</span></p>
          <h3>Java 后端开发工程师</h3>
          <p class="job-meta">示例城市 <span aria-hidden="true">·</span> 薪资待填写</p>
          <span class="card-hint">查看详情布局 <span aria-hidden="true">↗</span></span>
        </div>
        <div class="job-status">
          <span class="status-dot status-to-apply" aria-hidden="true"></span>
          <span>待投递</span>
        </div>
      </RouterLink>

      <div class="empty-placeholder">
        <span class="empty-icon" aria-hidden="true">＋</span>
        <h3>从一条岗位记录开始</h3>
        <p>这里将展示你的投递记录。岗位管理在阶段 3 接入。</p>
      </div>

      <div class="panel-bottomline">
        <p class="muted">当前仅展示草图布局，尚未读取真实岗位。</p>
        <button class="primary-button" disabled title="阶段 3 实现新增岗位">
          <span aria-hidden="true">＋</span> 添加岗位
        </button>
      </div>
    </div>
  </section>
</template>
