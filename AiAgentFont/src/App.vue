<template>
  <el-container class="app">
    <el-aside class="sidebar" width="248px">
      <div class="brand">
        <div class="brand-mark">AI</div>
        <div>
          <h1>AiAgent</h1>
          <p>智能体控制台</p>
        </div>
      </div>

      <el-menu
        class="nav-menu"
        :default-active="activeRoute"
        background-color="transparent"
        text-color="#4b5563"
        active-text-color="#2563eb"
        @select="onMenuSelect"
      >
        <el-menu-item index="/chat">
          <i class="el-icon-chat-dot-round"></i>
          <span slot="title">智能对话</span>
        </el-menu-item>
        <el-menu-item index="/txt2img">
          <i class="el-icon-picture-outline"></i>
          <span slot="title">文生图</span>
        </el-menu-item>
        <el-menu-item index="/train">
          <i class="el-icon-cpu"></i>
          <span slot="title">意图训练</span>
        </el-menu-item>
        <el-menu-item index="/document">
          <i class="el-icon-document-add"></i>
          <span slot="title">文档训练</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-status">
        <span :class="['status-dot', healthBadgeClass]"></span>
        <div>
          <p>服务状态</p>
          <strong>{{ healthStatusText }}</strong>
        </div>
        <el-button
          circle
          size="mini"
          icon="el-icon-refresh"
          :loading="checkingHealth"
          @click="checkHealth"
        />
      </div>
    </el-aside>

    <el-container>
      <el-header class="topbar" height="72px">
        <div>
          <p class="eyebrow">AI Agent Workspace</p>
          <h2>{{ pageTitle }}</h2>
        </div>
        <el-tag :type="healthBadgeClass === 'status-up' ? 'success' : 'warning'" effect="plain">
          {{ healthStatusText }}
        </el-tag>
      </el-header>

      <el-main class="main-panel">
        <el-alert
          v-if="error"
          class="error-alert"
          :title="error"
          type="error"
          show-icon
          :closable="false"
        />
        <router-view @error="onPageError" />
      </el-main>
    </el-container>
  </el-container>
</template>

<script src="./App.js"></script>

<style lang="less" src="./App.less"></style>
