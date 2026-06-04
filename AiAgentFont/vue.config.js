module.exports = {
  devServer: {
    port: 28082,
    proxy: {
      '/api': {
        target: 'http://localhost:28081',
        changeOrigin: true,
      },
    },
  },
};
