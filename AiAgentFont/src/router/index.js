import Vue from 'vue';
import Router from 'vue-router';
import ChatView from '../views/ChatView.vue';
import Txt2ImgView from '../views/Txt2ImgView.vue';
import TrainView from '../views/TrainView.vue';
import DocumentView from '../views/DocumentView.vue';

Vue.use(Router);

const router = new Router({
  mode: 'hash',
  routes: [
    {
      path: '/',
      redirect: '/chat',
    },
    {
      path: '/chat',
      name: 'chat',
      component: ChatView,
      meta: { title: '智能对话' },
    },
    {
      path: '/txt2img',
      name: 'txt2img',
      component: Txt2ImgView,
      meta: { title: '文生图' },
    },
    {
      path: '/train',
      name: 'train',
      component: TrainView,
      meta: { title: '意图模型训练' },
    },
    {
      path: '/document',
      name: 'document',
      component: DocumentView,
      meta: { title: '文档资料库训练' },
    },
    {
      path: '*',
      redirect: '/chat',
    },
  ],
});

export default router;
