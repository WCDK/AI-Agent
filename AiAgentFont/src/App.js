import http from './api/http';

const defaultTrainingSamples = [
  { message: '你好，随便聊聊', intent: 'CHAT' },
  { message: '早上好', intent: 'CHAT' },
  { message: '什么是规则引擎？', intent: 'ANSWER_QUESTION' },
  { message: 'Spring Boot 是怎么工作的？', intent: 'ANSWER_QUESTION' },
  { message: '实现一个用户登录接口', intent: 'EXECUTE_TASK' },
  { message: '修复失败的单元测试', intent: 'EXECUTE_TASK' },
  { message: '请画一只坐在月球上的橘猫', intent: 'DRAW_IMAGE' },
  { message: '生成一张山间湖泊图片', intent: 'DRAW_IMAGE' },
];

export default {
  name: 'App',
  data() {
    return {
      activeTab: 'chat',
      checkingHealth: false,
      sending: false,
      training: false,
      documentTraining: false,
      error: '',
      health: null,
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
      healthPollTimer: null,
      trainingEpochs: 250,
      trainingOutputDirectory: '',
      trainingSamplesJson: JSON.stringify(defaultTrainingSamples, null, 2),
      trainingResult: null,
      documentFile: null,
      documentFileName: '',
      documentTrainingResult: null,
    };
  },
  computed: {
    pageTitle() {
      const titles = {
        chat: '智能对话',
        train: '意图模型训练',
        document: '文档资料库训练',
      };
      return titles[this.activeTab] || '智能体控制台';
    },
    healthStatusText() {
      return this.health && this.health.status ? this.health.status : 'UNKNOWN';
    },
    healthBadgeClass() {
      return this.health && this.health.status === 'UP' ? 'status-up' : 'status-warn';
    },
    trainingResultText() {
      return JSON.stringify(this.trainingResult, null, 2);
    },
    documentTrainingResultText() {
      return this.documentTrainingResult
        ? JSON.stringify(this.documentTrainingResult, null, 2)
        : '';
    },
  },
  watch: {
    chatLog() {
      this.scrollToBottom();
    },
  },
  mounted() {
    this.checkHealth();
    this.healthPollTimer = window.setInterval(() => {
      this.checkHealth();
    }, 60000);
  },
  beforeDestroy() {
    if (this.healthPollTimer) {
      window.clearInterval(this.healthPollTimer);
      this.healthPollTimer = null;
    }
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
      const previous = entry.content;
      const nextContent = previous + content;
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
      this.error = '';
      this.sessionId = '';
      this.currentAssistantEntry = null;
      this.autoScrollToBottom = true;
      this.stopSpeech();
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
        const aborted = error.name === 'AbortError' || error.name === 'CanceledError' || error.code === 'ERR_CANCELED';
        if (aborted && this.currentAssistantEntry && !this.currentAssistantEntry.content && !this.currentAssistantEntry.images.length) {
          this.updateMessage(this.currentAssistantEntry, {
            content: '已中断',
            showThinking: false,
          });
        }
        if (aborted) {
          return;
        }
        this.error = error.message;
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
      this.error = '';
      this.speakingMessageId = item.id;

      try {
        if (item.audioB64) {
          await this.playBase64Audio(item.id, item.audioB64);
          return;
        }

        const { data } = await http.post(
          '/agent/tts',
          {
            text: item.content,
          },
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
          this.error = error.message;
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
          this.error = '语音播放失败。';
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
    onDocumentSelected(event) {
      const [file] = event.target.files || [];
      this.documentFile = file || null;
      this.documentFileName = file ? file.name : '';
    },
    onDocumentUploadChange(file) {
      const rawFile = file && file.raw ? file.raw : null;
      this.documentFile = rawFile;
      this.documentFileName = rawFile ? rawFile.name : '';
    },
    async checkHealth() {
      this.checkingHealth = true;
      this.error = '';
      try {
        const { data } = await http.get('/agent/health');
        this.health = data;
      } catch (error) {
        this.error = error.message;
      } finally {
        this.checkingHealth = false;
      }
    },
    async trainModel() {
      this.training = true;
      this.error = '';
      this.trainingResult = null;

      try {
        const samples = JSON.parse(this.trainingSamplesJson);
        const { data } = await http.post(
          '/agent/train',
          {
            epochs: this.trainingEpochs,
            outputDirectory: this.trainingOutputDirectory,
            samples,
          },
          {
            timeout: 0,
          },
        );

        this.trainingResult = data;
      } catch (error) {
        this.error = error.message;
      } finally {
        this.training = false;
      }
    },
    async uploadDocumentAndTrain() {
      if (!this.documentFile || this.documentTraining) {
        return;
      }

      this.documentTraining = true;
      this.error = '';
      this.documentTrainingResult = null;

      try {
        const formData = new FormData();
        formData.append('file', this.documentFile);

        const { data } = await http.post('/agent/documents/train', formData, {
          timeout: 0,
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });

        this.documentTrainingResult = data;
      } catch (error) {
        this.error = error.message;
      } finally {
        this.documentTraining = false;
      }
    },
    async sendMessage() {
      const userMessage = this.message.trim();
      if (!userMessage || this.sending) {
        return;
      }

      this.sending = true;
      this.error = '';
      this.message = '';
      this.chatAbortController = new AbortController();
      this.stopSpeech();

      this.appendMessage('user', '鐢ㄦ埛', userMessage);
      const assistantEntry = this.appendMessage('assistant', '鍔╂墜', '', []);
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
                  this.error = error.message;
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
        this.error = error.message;
        if (this.currentAssistantEntry && !this.currentAssistantEntry.content && !this.currentAssistantEntry.images.length) {
          this.updateMessage(this.currentAssistantEntry, {
            content: '璇锋眰澶辫触',
            showThinking: false,
          });
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

