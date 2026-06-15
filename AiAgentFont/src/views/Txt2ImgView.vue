<template>
  <section class="workspace txt2img-workspace">
    <el-card class="panel txt2img-panel" shadow="never">
      <div slot="header" class="panel-header">
        <div>
          <h3>Txt2Img</h3>
          <p>Stable Diffusion WebUI txt2img</p>
        </div>
        <el-button
          type="primary"
          icon="el-icon-picture-outline"
          :loading="generating"
          :disabled="generating || !positivePrompt.trim()"
          @click="generate"
        >
          Generate
        </el-button>
      </div>

      <el-form class="txt2img-form" label-position="top">
        <el-form-item label="Positive prompt">
          <el-input
            v-model="positivePrompt"
            type="textarea"
            :autosize="{ minRows: 7, maxRows: 12 }"
            resize="vertical"
            placeholder="masterpiece, best quality, lazy orange cat sleeping under tree shade at summer noon"
          />
        </el-form-item>

        <el-form-item label="Negative prompt">
          <el-input
            v-model="negativePrompt"
            type="textarea"
            :autosize="{ minRows: 5, maxRows: 10 }"
            resize="vertical"
            placeholder="worst quality, low quality, blurry, watermark, text"
          />
        </el-form-item>

        <el-form-item label="add_detail">
          <el-slider
            v-model="addDetailWeight"
            :min="0"
            :max="2"
            :step="0.05"
            show-input
            input-size="mini"
          />
        </el-form-item>

        <el-form-item label="epi_noiseoffset2">
          <el-slider
            v-model="epiNoiseOffsetWeight"
            :min="0"
            :max="2"
            :step="0.05"
            show-input
            input-size="mini"
          />
          <el-input class="lora-prompt" :value="loraPrompt" readonly />
        </el-form-item>
      </el-form>

      <div v-if="images.length" class="image-grid txt2img-results">
        <figure
          v-for="(image, index) in images"
          :key="`txt2img-${index}`"
          class="image-card"
        >
          <img
            class="generated-image"
            :src="toImageDataUrl(image)"
            :alt="image.revisedPrompt || 'Generated image'"
          />
          <figcaption v-if="image.revisedPrompt" class="image-caption">
            {{ image.revisedPrompt }}
          </figcaption>
        </figure>
      </div>
    </el-card>
  </section>
</template>

<script>
import http from '../api/http';

export default {
  name: 'Txt2ImgView',
  data() {
    return {
      generating: false,
      positivePrompt: '',
      negativePrompt: 'worst quality, low quality, lowres, blurry, jpeg artifacts, text, watermark, logo, signature',
      addDetailWeight: 0.7,
      epiNoiseOffsetWeight: 0.6,
      images: [],
    };
  },
  computed: {
    loraPrompt() {
      return [
        `<lora:add_detail:${Number(this.addDetailWeight).toFixed(2)}>`,
        `<lora:epi_noiseoffset2:${Number(this.epiNoiseOffsetWeight).toFixed(2)}>`,
      ].join(', ');
    },
  },
  methods: {
    setError(message) {
      this.$emit('error', message);
    },
    toImageDataUrl(image) {
      return `data:image/png;base64,${image.b64Json}`;
    },
    async generate() {
      const positivePrompt = this.positivePrompt.trim();
      if (!positivePrompt || this.generating) {
        return;
      }

      this.generating = true;
      this.setError('');
      this.images = [];

      try {
        const { data } = await http.post(
          '/agent/txt2img',
          {
            positivePrompt,
            negativePrompt: this.negativePrompt.trim(),
            loraPrompt: this.loraPrompt.trim(),
          },
          {
            timeout: 0,
          },
        );
        this.images = Array.isArray(data) ? data : [];
      } catch (error) {
        this.setError(error.message);
      } finally {
        this.generating = false;
      }
    },
  },
};
</script>
