import Storage from '@/utils/storage';

import { createRouter, createWebHistory } from 'vue-router';

const HomePage = () => import('@/page/HomePage.vue');
const LoginPage = () => import('@/page/LoginPage.vue');
const AcceptWorkOrder = () => import('@/components/AcceptWorkOrder.vue');
const AllWorkOrder = () => import('@/components/AllWorkOrder.vue');
const DetailWorkorder = () => import('@/components/DetailWorkorder.vue');
const DispatchWorkOrder = () => import('@/components/DispatchWorkOrder.vue');
const RenewWorkorder = () => import('@/components/RenewWorkorder.vue');
const DownstreamMerchant = () => import('@/components/DownstreamMerchant.vue');
const DownstreamUser = () => import('@/components/DownstreamUser.vue');
const EditBaseWorkorder = () => import('@/components/EditBaseWorkorder.vue');
const EditDownstreamMerchant = () => import('@/components/EditDownstreamMerchant.vue');
const EditDownstreamUser = () => import('@/components/EditDownstreamUser.vue');
const EditSystemUser = () => import('@/components/EditSystemUser.vue');
const EditUpstream = () => import('@/components/EditUpstream.vue');
const PersonalCenter = () => import('@/components/PersonalCenter.vue');
const UpStream = () => import('@/components/UpStream.vue');
const UserApproval = () => import('@/components/UserApproval.vue');
const UserManagement = () => import('@/components/UserManagement.vue');

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
