<template>
  <section class="workspace">
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
          当前文件：{{ documentFileName || '未选择文件' }}；
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
</template>

<script>
import http from '../api/http';

export default {
  name: 'DocumentView',
  data() {
    return {
      documentTraining: false,
      documentFile: null,
      documentFileName: '',
      documentTrainingResult: null,
    };
  },
  computed: {
    documentTrainingResultText() {
      return this.documentTrainingResult
        ? JSON.stringify(this.documentTrainingResult, null, 2)
        : '';
    },
  },
  methods: {
    setError(message) {
      this.$emit('error', message);
    },
    onDocumentUploadChange(file) {
      const rawFile = file && file.raw ? file.raw : null;
      this.documentFile = rawFile;
      this.documentFileName = rawFile ? rawFile.name : '';
    },
    async uploadDocumentAndTrain() {
      if (!this.documentFile || this.documentTraining) {
        return;
      }

      this.documentTraining = true;
      this.setError('');
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
        this.setError(error.message);
      } finally {
        this.documentTraining = false;
      }
    },
  },
};
</script>
