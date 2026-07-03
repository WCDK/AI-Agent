import http from './api/http';

export default {
  name: 'App',
  data() {
    return {
      checkingHealth: false,
      error: '',
      health: null,
      healthPollTimer: null,
    };
  },
  computed: {
    pageTitle() {
      return (this.$route.meta && this.$route.meta.title) || '智能体控制台';
    },
    activeRoute() {
      return this.$route.path;
    },
    healthStatusText() {
      return this.health && this.health.status ? this.health.status : 'UNKNOWN';
    },
    healthBadgeClass() {
      return this.health && this.health.status === 'UP' ? 'status-up' : 'status-warn';
    },
  },
  watch: {
    $route() {
      this.error = '';
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
  },
  methods: {
    onMenuSelect(path) {
      if (path !== this.$route.path) {
        this.$router.push(path);
      }
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
    onPageError(message) {
      this.error = message || '';
    },
  },
};
