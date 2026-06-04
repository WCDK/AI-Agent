<template>
  <main class="app">
    <section class="shell">
      <header class="hero">
        <p class="eyebrow">AiAgentFont</p>
        <h1>AiAgentTest 控制台</h1>
        <p class="intro">统一聊天入口、健康检查、意图模型训练与文档上传训练。</p>
      </header>

      <section class="card status-card">
        <div class="card-header">
          <h2>服务状态</h2>
          <div class="actions">
            <button :disabled="checkingHealth" @click="checkHealth">
              {{ checkingHealth ? '检查中...' : '检查健康' }}
            </button>
          </div>
        </div>
        <div class="status-line">
          <span :class="['status-badge', healthBadgeClass]">{{ healthStatusText }}</span>
        </div>
      </section>

      <section class="tabs">
        <button :class="{ active: activeTab === 'chat' }" @click="activeTab = 'chat'">聊天</button>
        <button :class="{ active: activeTab === 'train' }" @click="activeTab = 'train'">意图训练</button>
        <button :class="{ active: activeTab === 'document' }" @click="activeTab = 'document'">文档训练</button>
      </section>

      <section v-if="activeTab === 'chat'" class="card chat-card">
        <div class="card-header">
          <h2>对话</h2>
          <div class="actions">
            <button :disabled="sending || !message.trim()" @click="sendMessage">
              {{ sending ? '思考中...' : '发送消息' }}
            </button>
            <button v-if="sending" class="secondary" @click="stopChat">
              中断
            </button>
            <button class="secondary" :disabled="sending" @click="clearConversation">
              清空会话
            </button>
          </div>
        </div>

        <div class="meta-row">
          <span>Session ID：{{ sessionId || '自动创建' }}</span>
          <span>状态：{{ sending ? '流式响应中' : '空闲' }}</span>
        </div>

        <div ref="log" class="chat-log" @scroll="handleChatScroll">
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
              <button
                class="copy-btn"
                :disabled="!item.content && !item.thinking"
                @click="copyMessage(item)"
              >
                {{ copiedMessageId === item.id ? '已复制' : '复制' }}
              </button>
              <button
                v-if="item.role === 'assistant'"
                class="copy-btn"
                :disabled="!item.content"
                @click="playMessageAudio(item)"
              >
                {{ speakingMessageId === item.id ? '停止' : '播放' }}
              </button>
            </div>

            <section
              v-if="item.role === 'assistant' && item.showThinking && item.thinking.trim()"
              class="thinking-panel"
            >
              <p class="thinking-title">思考内容</p>
              <p class="thinking-text">{{ item.thinking }}</p>
            </section>

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
          <label class="field">
            <span>Session ID</span>
            <input v-model.trim="sessionId" type="text" placeholder="留空自动创建" />
          </label>

          <label class="field">
            <span>消息</span>
            <textarea
              v-model="message"
              rows="4"
              placeholder="输入消息后按 Enter 发送，Shift+Enter 换行"
              @keydown.enter.exact.prevent="sendMessage"
            ></textarea>
          </label>

          <button class="send-btn" :disabled="sending || !message.trim()" @click="sendMessage">
            发送
          </button>
          <button v-if="sending" class="stop-btn" @click="stopChat">
            中断
          </button>
        </div>
      </section>

      <section v-if="activeTab === 'train'" class="card train-card">
        <div class="card-header">
          <h2>训练 DL4J 意图模型</h2>
          <div class="actions">
            <button :disabled="training" @click="trainModel">
              {{ training ? '训练中...' : '开始训练' }}
            </button>
          </div>
        </div>

        <div class="train-grid">
          <label class="field">
            <span>训练轮数 epochs</span>
            <input v-model.number="trainingEpochs" type="number" min="1" />
          </label>
        </div>

        <label class="field">
          <span>训练样本 JSON</span>
          <textarea
            v-model="trainingSamplesJson"
            class="samples-input"
            rows="12"
            placeholder="[{ message: '你好', intent: 'CHAT' }]"
          ></textarea>
        </label>

        <pre v-if="trainingResult" class="result">{{ trainingResultText }}</pre>
      </section>

      <section v-if="activeTab === 'document'" class="card document-card">
        <div class="card-header">
          <h2>上传文档并创建资料库索引</h2>
          <div class="actions">
            <button :disabled="documentTraining || !documentFile" @click="uploadDocumentAndTrain">
              {{ documentTraining ? '上传中...' : '上传并创建索引' }}
            </button>
          </div>
        </div>

        <div class="document-panel">
          <label class="field">
            <span>选择文档</span>
            <input
              class="file-input"
              type="file"
              accept=".txt,.md,.json,.csv,.pdf,.docx,.java,.xml,.yaml,.yml,.properties,.log"
              @change="onDocumentSelected"
            />
          </label>

          <div class="meta-row">
            <span>当前文件：{{ documentFileName || '未选择文件' }}</span>
            <span>存储目录：D:\dssource</span>
          </div>

          <p class="document-tip">
            上传后系统会保存原始文档、抽取文本，并创建资料库索引。聊天时会优先检索资料库内容辅助回答。
          </p>
        </div>

        <pre v-if="documentTrainingResult" class="result">{{ documentTrainingResultText }}</pre>
      </section>

      <p v-if="error" class="error">{{ error }}</p>
    </section>
  </main>
</template>

<script src="./App.js"></script>

<style lang="less" src="./App.less"></style>
