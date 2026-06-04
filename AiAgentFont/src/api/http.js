import axios from 'axios';

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
});

http.interceptors.response.use(
  response => response,
  error => {
    const status = error.response && error.response.status;
    const message = error.response && error.response.data && error.response.data.message;

    if (message) {
      return Promise.reject(new Error(message));
    }

    if (status) {
      return Promise.reject(new Error(`请求失败，状态码：${status}`));
    }

    return Promise.reject(error);
  },
);

export default http;
