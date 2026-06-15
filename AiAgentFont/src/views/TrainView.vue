<template>
  <section class="workspace">
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
</template>

<script>
import http from '../api/http';

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
  name: 'TrainView',
  data() {
    return {
      training: false,
      trainingEpochs: 250,
      trainingOutputDirectory: '',
      trainingSamplesJson: JSON.stringify(defaultTrainingSamples, null, 2),
      trainingResult: null,
    };
  },
  computed: {
    trainingResultText() {
      return JSON.stringify(this.trainingResult, null, 2);
    },
  },
  methods: {
    setError(message) {
      this.$emit('error', message);
    },
    async trainModel() {
      this.training = true;
      this.setError('');
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
        this.setError(error.message);
      } finally {
        this.training = false;
      }
    },
  },
};
</script>
