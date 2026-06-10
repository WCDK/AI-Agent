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
        :default-active="activeTab"
        background-color="transparent"
        text-color="#4b5563"
        active-text-color="#2563eb"
        @select="activeTab = $event"
      >
        <el-menu-item index="chat">
          <i class="el-icon-chat-dot-round"></i>
          <span slot="title">智能对话</span>
        </el-menu-item>
        <el-menu-item index="train">
          <i class="el-icon-cpu"></i>
          <span slot="title">意图训练</span>
        </el-menu-item>
        <el-menu-item index="document">
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

        <section v-if="activeTab === 'chat'" class="workspace chat-workspace">
          <el-card class="panel chat-panel" shadow="never">
            <div slot="header" class="panel-header">
              <div>
                <h3>对话</h3>
                <p>支持流式响应、语音播放和图片结果展示</p>
              </div>
              <div class="header-actions">
                <el-button
                  size="small"
                  icon="el-icon-delete"
                  :disabled="sending"
                  @click="clearConversation"
                >
                  清空
                </el-button>
                <el-button
                  v-if="sending"
                  size="small"
                  type="danger"
                  icon="el-icon-video-pause"
                  @click="stopChat"
                >
                  中断
                </el-button>
              </div>
            </div>

            <div class="meta-strip">
              <span>状态：{{ sending ? '思考中...' : '空闲' }}</span>
            </div>

            <div ref="log" class="chat-log" @scroll="handleChatScroll">
              <el-empty v-if="!chatLog.length" description="发送一条消息开始对话" />

              <article
                v-for="item in chatLog"
                :key="item.id"
                :class="['bubble', item.role]"
              >
                <div class="bubble-head">
                  <div class="bubble-meta">
                    <strong>{{ item.roleLabel }}</strong>
                    <span>{{ item.time }}</span>
                  </div>
                  <div class="bubble-actions">
                    <el-button
                      size="mini"
                      icon="el-icon-document-copy"
                      :disabled="!item.content && !item.thinking"
                      @click="copyMessage(item)"
                    >
                      {{ copiedMessageId === item.id ? '已复制' : '复制' }}
                    </el-button>
                    <el-button
                      v-if="item.role === 'assistant'"
                      size="mini"
                      :icon="speakingMessageId === item.id ? 'el-icon-video-pause' : 'el-icon-headset'"
                      :disabled="!item.content"
                      @click="playMessageAudio(item)"
                    >
                      {{ speakingMessageId === item.id ? '停止' : '播放' }}
                    </el-button>
                  </div>
                </div>

                <el-collapse
                  v-if="item.role === 'assistant' && item.showThinking && item.thinking.trim()"
                  class="thinking-collapse"
                >
                  <el-collapse-item title="思考中..." name="thinking">
                    <p class="thinking-text">{{ item.thinking }}</p>
                  </el-collapse-item>
                </el-collapse>

                <p v-if="item.content" class="bubble-text">{{ item.content }}</p>

                <div v-if="item.images.length" class="image-grid">
                  <figure
                    v-for="(image, index) in item.images"
                    :key="`${item.id}-${index}`"
                    class="image-card"
                  >
                    <img
                      class="generated-image"
                      :src="toImageDataUrl(image)"
                      :alt="image.revisedPrompt || '生成图片'"
                    />
                    <figcaption v-if="image.revisedPrompt" class="image-caption">
                      优化提示词：{{ image.revisedPrompt }}
                    </figcaption>
                  </figure>
                </div>
              </article>
            </div>

            <div class="composer">
              <div class="composer-row">
                <el-input
                  v-model="message"
                  type="textarea"
                  :autosize="{ minRows: 3, maxRows: 8 }"
                  resize="vertical"
                  placeholder="输入消息后按 Enter 发送，Shift+Enter 换行"
                  @keydown.enter.exact.native.prevent="sendMessage"
                />
                <div class="composer-actions">
                  <el-button
                    type="primary"
                    icon="el-icon-s-promotion"
                    :loading="sending"
                    :disabled="sending || !message.trim()"
                    @click="sendMessage"
                  >
                    发送
                  </el-button>
                  <el-button
                    v-if="sending"
                    type="danger"
                    icon="el-icon-video-pause"
                    @click="stopChat"
                  >
                    中断
                  </el-button>
                </div>
              </div>
            </div>
          </el-card>
        </section>

        <section v-if="activeTab === 'train'" class="workspace">
          <el-card class="panel" shadow="never">
            <div slot="header" class="panel-header">
              <div>
                <h3>训练 DL4J 意图模型</h3>
                <p>调整训练轮数并提交样本 JSON</p>
              </div>
              <el-button
                type="primary"
                icon="el-icon-cpu"
                :loading="training"
                :disabled="training"
                @click="trainModel"
              >
                开始训练
              </el-button>
            </div>

            <el-form label-position="top">
              <el-form-item label="训练轮数 epochs">
                <el-input-number v-model="trainingEpochs" :min="1" :step="50" />
              </el-form-item>
              <el-form-item label="训练样本 JSON">
                <el-input
                  v-model="trainingSamplesJson"
                  class="code-input"
                  type="textarea"
                  :rows="14"
                  resize="vertical"
                  placeholder='[{ "message": "你好", "intent": "CHAT" }]'
                />
              </el-form-item>
            </el-form>

            <pre v-if="trainingResult" class="result">{{ trainingResultText }}</pre>
          </el-card>
        </section>

        <section v-if="activeTab === 'document'" class="workspace">
          <el-card class="panel" shadow="never">
            <div slot="header" class="panel-header">
              <div>
                <h3>上传文档并创建资料库索引</h3>
                <p>支持常见文本、配置、代码和办公文档</p>
              </div>
              <el-button
                type="primary"
                icon="el-icon-upload"
                :loading="documentTraining"
                :disabled="documentTraining || !documentFile"
                @click="uploadDocumentAndTrain"
              >
                上传并创建索引
              </el-button>
            </div>

            <el-upload
              class="document-upload"
              drag
              action=""
              :auto-upload="false"
              :show-file-list="false"
              accept=".txt,.md,.json,.csv,.pdf,.docx,.java,.xml,.yaml,.yml,.properties,.log"
              :on-change="onDocumentUploadChange"
            >
              <i class="el-icon-upload"></i>
              <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
              <div slot="tip" class="el-upload__tip">
                当前文件：{{ documentFileName || '未选择文件' }}；存储目录：D:\dssource
              </div>
            </el-upload>

            <el-alert
              class="document-tip"
              title="上传后系统会保存原始文档、抽取文本，并创建资料库索引。聊天时会优先检索资料库内容辅助回答。"
              type="info"
              show-icon
              :closable="false"
            />

            <pre v-if="documentTrainingResult" class="result">{{ documentTrainingResultText }}</pre>
          </el-card>
        </section>
      </el-main>
    </el-container>
  </el-container>
</template>

<script src="./App.js"></script>

<style lang="less" src="./App.less"></style>
