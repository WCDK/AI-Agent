<template>
  <section class="workspace txt2img-workspace">
    <el-card class="panel txt2img-panel" shadow="never">
      <div slot="header" class="panel-header">
        <div>
          <h3>文生图</h3>
          <p>根据文字生成图片</p>
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
        <el-form-item label="正向关键词">
          <el-input
            v-model="positivePrompt"
            type="textarea"
            :autosize="{ minRows: 7, maxRows: 12 }"
            resize="vertical"
            placeholder="杰作，最佳质量，夏日正午树荫下慵懒的橘猫在睡觉"
          />
        </el-form-item>

        <el-form-item label="负向关键词">
          <el-input
            v-model="negativePrompt"
            type="textarea"
            :autosize="{ minRows: 5, maxRows: 10 }"
            resize="vertical"
            placeholder="低质量，低分辨率，模糊，JPEG伪影，文字，水印，标志，签名"
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
      negativePrompt: '',
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
      const value = typeof image === 'string'
        ? image
        : image.b64Json || image.b64_json || image.image || image.data || '';
      return value.startsWith('data:image/') ? value : `data:image/png;base64,${value}`;
    },
    normalizeGeneratedImages(data) {
      const source = Array.isArray(data)
        ? data
        : data && Array.isArray(data.images)
          ? data.images
          : data && Array.isArray(data.data)
            ? data.data
            : data
              ? [data]
              : [];

      return source
        .map(item => {
          if (typeof item === 'string') {
            return { b64Json: item, revisedPrompt: this.positivePrompt.trim() };
          }

          if (!item || typeof item !== 'object') {
            return null;
          }

          const b64Json = item.b64Json || item.b64_json || item.image || item.data || '';
          if (!b64Json) {
            return null;
          }

          return {
            ...item,
            b64Json,
            revisedPrompt: item.revisedPrompt || item.revised_prompt || item.prompt || this.positivePrompt.trim(),
          };
        })
        .filter(Boolean);
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
        this.images = this.normalizeGeneratedImages(data);
        if (!this.images.length) {
          this.setError('后端已返回响应，但未找到可展示的图片数据。');
        }
      } catch (error) {
        this.setError(error.message);
      } finally {
        this.generating = false;
      }
    },
  },
};
</script>
