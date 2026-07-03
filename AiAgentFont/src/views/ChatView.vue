<template>
  <section class="workspace chat-workspace">
    <el-card class="panel chat-panel" shadow="never">
      <div slot="header" class="panel-header">
        <div>
          <h3>对话</h3>
          <p>支持流式响应、语音播放和图片结果展示</p>
        </div>
        <div class="header-actions">
          <el-button size="small" icon="el-icon-delete" :disabled="sending" @click="clearConversation">
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

        <article v-for="item in chatLog" :key="item.id" :class="['bubble', item.role]">
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
</template>

<script>
import http from '../api/http';

export default {
  name: 'ChatView',
  data() {
    return {
      sending: false,
      sessionId: '',
      message: '',
      chatLog: [],
      currentAssistantEntry: null,
      chatAbortController: null,
      copiedMessageId: null,
      copyResetTimer: null,
      speakingMessageId: null,
      audioElement: null,
      audioObjectUrl: null,
      autoScrollToBottom: true,
      nextId: 1,
    };
  },
  watch: {
    chatLog() {
      this.scrollToBottom();
    },
  },
  beforeDestroy() {
    if (this.chatAbortController) {
      this.chatAbortController.abort();
      this.chatAbortController = null;
    }
    if (this.copyResetTimer) {
      window.clearTimeout(this.copyResetTimer);
      this.copyResetTimer = null;
    }
    this.stopSpeech();
  },
  methods: {
    setError(message) {
      this.$emit('error', message);
    },
    nowLabel() {
      return new Date().toLocaleTimeString('zh-CN', {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });
    },
    appendMessage(role, roleLabel, content, images = []) {
      const item = {
        id: this.nextId++,
        role,
        roleLabel,
        content,
        images,
        audioB64: '',
        thinking: '',
        showThinking: false,
        hasVisibleContent: false,
        time: this.nowLabel(),
      };
      this.chatLog.push(item);
      return item;
    },
    updateMessage(entry, { content, images, audioB64, thinking, showThinking, hasVisibleContent } = {}) {
      if (typeof content === 'string') {
        entry.content = content;
      }
      if (Array.isArray(images)) {
        entry.images = images;
      }
      if (typeof audioB64 === 'string') {
        entry.audioB64 = audioB64;
      }
      if (typeof thinking === 'string') {
        entry.thinking = thinking;
      }
      if (typeof showThinking === 'boolean') {
        entry.showThinking = showThinking;
      }
      if (typeof hasVisibleContent === 'boolean') {
        entry.hasVisibleContent = hasVisibleContent;
      }
      this.chatLog = this.chatLog.slice();
      this.scrollToBottom();
    },
    appendToMessage(entry, content) {
      const nextContent = entry.content + content;
      const hasVisibleContent = /\S/.test(nextContent);
      this.updateMessage(entry, {
        content: nextContent,
        hasVisibleContent,
        showThinking: hasVisibleContent ? false : entry.showThinking,
      });
    },
    appendThinkingToMessage(entry, content) {
      this.updateMessage(entry, {
        thinking: entry.thinking + content,
        showThinking: !entry.hasVisibleContent && !entry.images.length,
      });
    },
    hideThinkingWhenFinalContentReady(entry) {
      if (entry.showThinking) {
        this.updateMessage(entry, { showThinking: false });
      }
    },
    isChatLogAtBottom(el = this.$refs.log) {
      if (!el) {
        return true;
      }
      return el.scrollHeight - el.scrollTop - el.clientHeight <= 8;
    },
    handleChatScroll() {
      this.autoScrollToBottom = this.isChatLogAtBottom();
    },
    scrollToBottom({ force = false } = {}) {
      this.$nextTick(() => {
        const el = this.$refs.log;
        if (el && (force || this.autoScrollToBottom)) {
          el.scrollTop = el.scrollHeight;
          this.autoScrollToBottom = true;
        }
      });
    },
    clearConversation() {
      if (this.sending) {
        return;
      }
      this.chatLog = [];
      this.message = '';
      this.sessionId = '';
      this.currentAssistantEntry = null;
      this.autoScrollToBottom = true;
      this.stopSpeech();
      this.setError('');
    },
    stopChat() {
      if (this.chatAbortController) {
        this.chatAbortController.abort();
      }
    },
    async copyMessage(item) {
      if (!item) {
        return;
      }

      const content = item.content || item.thinking || '';
      if (!content.trim()) {
        return;
      }

      try {
        await this.copyText(content);
        this.copiedMessageId = item.id;
        if (this.copyResetTimer) {
          window.clearTimeout(this.copyResetTimer);
        }
        this.copyResetTimer = window.setTimeout(() => {
          this.copiedMessageId = null;
          this.copyResetTimer = null;
        }, 1500);
      } catch (error) {
        this.setError(error.message);
      }
    },
    async copyText(content) {
      if (navigator.clipboard && window.isSecureContext) {
        await navigator.clipboard.writeText(content);
        return;
      }

      const textarea = document.createElement('textarea');
      textarea.value = content;
      textarea.setAttribute('readonly', '');
      textarea.style.position = 'fixed';
      textarea.style.left = '-9999px';
      document.body.appendChild(textarea);
      textarea.select();
      const copied = document.execCommand('copy');
      document.body.removeChild(textarea);

      if (!copied) {
        throw new Error('复制失败。');
      }
    },
    async playMessageAudio(item) {
      if (!item || !item.content || !item.content.trim()) {
        return;
      }
      if (this.speakingMessageId === item.id) {
        this.stopSpeech();
        return;
      }

      this.stopSpeech();
      this.setError('');
      this.speakingMessageId = item.id;

      try {
        if (item.audioB64) {
          await this.playBase64Audio(item.id, item.audioB64);
          return;
        }

        const { data } = await http.post(
          '/agent/tts',
          { text: item.content },
          {
            responseType: 'blob',
            timeout: 0,
          },
        );

        if (this.speakingMessageId !== item.id) {
          return;
        }

        await this.playBlobAudio(item.id, data);
      } catch (error) {
        if (this.speakingMessageId === item.id) {
          this.setError(error.message);
          this.stopSpeech();
        }
      }
    },
    async playBase64Audio(messageId, audioB64) {
      const bytes = Uint8Array.from(atob(audioB64), char => char.charCodeAt(0));
      await this.playBlobAudio(messageId, new Blob([bytes], { type: 'audio/mpeg' }));
    },
    async playBlobAudio(messageId, blob) {
      if (this.speakingMessageId !== messageId) {
        return;
      }

      const objectUrl = URL.createObjectURL(blob);
      const audio = new Audio(objectUrl);
      this.audioElement = audio;
      this.audioObjectUrl = objectUrl;

      audio.onended = () => {
        if (this.speakingMessageId === messageId) {
          this.stopSpeech();
        }
      };
      audio.onerror = () => {
        if (this.speakingMessageId === messageId) {
          this.setError('语音播放失败。');
          this.stopSpeech();
        }
      };

      await audio.play();
    },
    stopSpeech() {
      if (this.audioElement) {
        this.audioElement.pause();
        this.audioElement.src = '';
        this.audioElement = null;
      }
      if (this.audioObjectUrl) {
        URL.revokeObjectURL(this.audioObjectUrl);
        this.audioObjectUrl = null;
      }
      this.speakingMessageId = null;
    },
    toImageDataUrl(image) {
      return `data:image/png;base64,${image.b64Json}`;
    },
    async sendMessage() {
      const userMessage = this.message.trim();
      if (!userMessage || this.sending) {
        return;
      }

      this.sending = true;
      this.setError('');
      this.message = '';
      this.chatAbortController = new AbortController();
      this.stopSpeech();

      this.appendMessage('user', '我', userMessage);
      const assistantEntry = this.appendMessage('assistant', '助手', '', []);
      this.currentAssistantEntry = assistantEntry;

      try {
        const { data: stream } = await http.post(
          '/agent/chat',
          {
            sessionId: this.sessionId || null,
            message: userMessage,
          },
          {
            adapter: 'fetch',
            responseType: 'stream',
            timeout: 0,
            headers: {
              Accept: 'text/event-stream',
            },
            signal: this.chatAbortController.signal,
          },
        );

        if (!stream || typeof stream.getReader !== 'function') {
          throw new Error('聊天流不可用。');
        }

        await this.readSseStream(stream, (eventName, payload) => {
          if (payload.sessionId && !this.sessionId) {
            this.sessionId = payload.sessionId;
          }

          if (eventName === 'meta') {
            this.updateMessage(assistantEntry, {
              content: '',
              thinking: '',
              showThinking: false,
              hasVisibleContent: false,
            });
            return;
          }

          if (eventName === 'thinking') {
            if (payload.content) {
              this.appendThinkingToMessage(assistantEntry, payload.content);
            }
            return;
          }

          if (eventName === 'delta') {
            const nextImages = Array.isArray(payload.images) ? payload.images : assistantEntry.images;

            if (payload.content) {
              this.appendToMessage(assistantEntry, payload.content);
            }

            if (nextImages !== assistantEntry.images) {
              this.updateMessage(assistantEntry, { images: nextImages });
            }

            if (assistantEntry.hasVisibleContent || nextImages.length) {
              this.hideThinkingWhenFinalContentReady(assistantEntry);
            }
            return;
          }

          if (
            eventName === 'done'
            && !assistantEntry.content
            && !assistantEntry.images.length
            && !assistantEntry.thinking
          ) {
            this.updateMessage(assistantEntry, { content: '（空响应）' });
            return;
          }

          if (eventName === 'audio') {
            if (payload.content) {
              this.updateMessage(assistantEntry, { audioB64: payload.content });
              this.stopSpeech();
              this.speakingMessageId = assistantEntry.id;
              this.playBase64Audio(assistantEntry.id, payload.content).catch(error => {
                if (this.speakingMessageId === assistantEntry.id) {
                  this.setError(error.message);
                  this.stopSpeech();
                }
              });
            }
            return;
          }

          if (eventName === 'audio-error') {
            return;
          }

          if (eventName === 'error') {
            throw new Error(payload.content || '服务端返回错误。');
          }
        });
      } catch (error) {
        const aborted = error.name === 'AbortError' || error.name === 'CanceledError' || error.code === 'ERR_CANCELED';
        if (aborted && this.currentAssistantEntry && !this.currentAssistantEntry.content && !this.currentAssistantEntry.images.length) {
          this.updateMessage(this.currentAssistantEntry, {
            content: '已中断',
            showThinking: false,
          });
        } else if (!aborted) {
          this.setError(error.message);
          if (this.currentAssistantEntry && !this.currentAssistantEntry.content && !this.currentAssistantEntry.images.length) {
            this.updateMessage(this.currentAssistantEntry, {
              content: '请求失败',
              showThinking: false,
            });
          }
        }
      } finally {
        this.sending = false;
        this.chatAbortController = null;
        this.currentAssistantEntry = null;
      }
    },
    async readSseStream(body, onEvent) {
      const reader = body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
        const { value, done } = await reader.read();
        if (done) {
          break;
        }

        buffer += decoder.decode(value, { stream: true });
        buffer = this.consumeCompleteSseFrames(buffer, onEvent);
      }

      buffer += decoder.decode();
      if (buffer.trim()) {
        this.consumeSseFrame(buffer, onEvent);
      }
    },
    consumeCompleteSseFrames(buffer, onEvent) {
      const separatorPattern = /\r\n\r\n|\n\n|\r\r/;
      let match = buffer.match(separatorPattern);

      while (match && typeof match.index === 'number') {
        const frame = buffer.slice(0, match.index);
        buffer = buffer.slice(match.index + match[0].length);
        this.consumeSseFrame(frame, onEvent);
        match = buffer.match(separatorPattern);
      }

      return buffer;
    },
    consumeSseFrame(frame, onEvent) {
      const lines = frame.split(/\r?\n|\r/);
      let eventName = 'message';
      const dataLines = [];

      for (const line of lines) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim();
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart());
        }
      }

      if (!dataLines.length) {
        return;
      }

      const rawData = dataLines.join('\n');
      let payload = {};
      try {
        payload = JSON.parse(rawData);
      } catch (error) {
        payload = { content: rawData };
      }

      onEvent(eventName, payload);
    },
  },
};
</script>
