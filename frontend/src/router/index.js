import AcceptWorkOrder from '@/components/AcceptWorkOrder.vue';
import AllWorkOrder from '@/components/AllWorkOrder.vue';
import DetailWorkorder from '@/components/DetailWorkorder.vue';
import DispatchWorkOrder from '@/components/DispatchWorkOrder.vue';
import RenewWorkorder from '@/components/RenewWorkorder.vue';
import DownstreamMerchant from '@/components/DownstreamMerchant.vue';
import DownstreamUser from '@/components/DownstreamUser.vue';
import EditBaseWorkorder from '@/components/EditBaseWorkorder.vue';
import EditDownstreamMerchant from '@/components/EditDownstreamMerchant.vue';
import EditDownstreamUser from '@/components/EditDownstreamUser.vue';
import EditSystemUser from '@/components/EditSystemUser.vue';
import EditUpstream from '@/components/EditUpstream.vue';
import PersonalCenter from '@/components/PersonalCenter.vue';
import UpStream from '@/components/UpStream.vue';
import UserApproval from '@/components/UserApproval.vue';
import UserManagement from '@/components/UserManagement.vue';
import HomePage from '@/page/HomePage.vue';
import LoginPage from '@/page/LoginPage.vue';
import Storage from '@/utils/storage';

import { createRouter, createWebHistory } from 'vue-router';

const routes = [
  {
    path: '/',
    redirect: '/login',
  },
  {
    path: '/login',
    component: LoginPage,
    meta: {
      requiresAuth: false
    },
  },
  {
    path: '/home',
    component: HomePage,
    meta: {
      requiresAuth: true
    },
    children: [
      {
        path: '',
        redirect: '/home/allWorkorder',
      },
      {
        path: 'upstream',
        component: UpStream
      },
      {
        path: 'downstreamMerchant',
        component: DownstreamMerchant
      },
      {
        path: 'downstreamUser',
        component: DownstreamUser
      },
      {
        path: 'editUpstream',
        component: EditUpstream
      },
      {
        path: 'editDownstreamMerchant',
        component: EditDownstreamMerchant
      },
      {
        path: 'editDownstreamUser',
        component: EditDownstreamUser
      },
      {
        path: 'acceptWorkOrder',
        component: AcceptWorkOrder
      },
      {
        path: 'dispatchWorkOrder',
        component: DispatchWorkOrder
      },
      {
        path: 'editWorkorder',
        component: EditBaseWorkorder
      },
      {
        path: 'detailWorkorder',
        component: DetailWorkorder
      },
      {
        path: 'personalCenter',
        component: PersonalCenter
      },
      {
        path: 'userManagement',
        component: UserManagement
      },
      {
        path: 'allWorkorder',
        component: AllWorkOrder
      },
      {
        path: 'renewWorkorder',
        component: RenewWorkorder
      },
      {
        path: 'editSystemUser',
        component: EditSystemUser
      },
      {
        path: 'userApproval',
        component: UserApproval
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

// 2. 添加全局前置路由守卫
router.beforeEach((to, from, next) => {
  const isLogin = Storage.get('token') !== null && Storage.get('token') !== '' && Storage.get('token') !== undefined;
  if (to.meta.requiresAuth){
    if (isLogin) {
      next();
    } else {
      next('/login');
    }
  }
  else{
    if (to.path === '/login' && isLogin) {
      next('/home');
    } else {
      next();
    }
  }
});

export default router;
