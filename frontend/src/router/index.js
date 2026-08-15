import Storage from '@/utils/storage';

import { createRouter, createWebHistory } from 'vue-router';

/**
 * 以下路由组件使用动态导入，只有用户进入对应业务页面时才下载代码，降低登录页首屏体积。
 * 每个函数返回 Vue Router 可识别的组件 Promise，不执行额外业务状态初始化。
 */
const HomePage = () => import('@/page/HomePage.vue');
/**
 * 加载车险账号登录、注册和密码找回页面。
 */
const LoginPage = () => import('@/page/LoginPage.vue');
/**
 * 加载 SaaS 门户进入车险系统的一次性 code 回调页面。
 */
const SsoCallbackPage = () => import('@/page/SsoCallbackPage.vue');
/**
 * 加载工单接单及重新分配工作台。
 */
const AcceptWorkOrder = () => import('@/components/AcceptWorkOrder.vue');
/**
 * 加载全量工单查询和新增入口。
 */
const AllWorkOrder = () => import('@/components/AllWorkOrder.vue');
/**
 * 加载工单报价、支付、承保等完整详情流程。
 */
const DetailWorkorder = () => import('@/components/DetailWorkorder.vue');
/**
 * 加载工单分派和基础资料编辑入口。
 */
const DispatchWorkOrder = () => import('@/components/DispatchWorkOrder.vue');
/**
 * 加载当前续保窗口的客户跟进工作台。
 */
const RenewWorkorder = () => import('@/components/RenewWorkorder.vue');
/**
 * 加载下游机构列表。
 */
const DownstreamMerchant = () => import('@/components/DownstreamMerchant.vue');
/**
 * 加载下游机构员工列表。
 */
const DownstreamUser = () => import('@/components/DownstreamUser.vue');
/**
 * 加载工单基础资料新增或编辑表单。
 */
const EditBaseWorkorder = () => import('@/components/EditBaseWorkorder.vue');
/**
 * 加载下游机构新增或编辑表单。
 */
const EditDownstreamMerchant = () => import('@/components/EditDownstreamMerchant.vue');
/**
 * 加载机构员工新增或编辑表单。
 */
const EditDownstreamUser = () => import('@/components/EditDownstreamUser.vue');
/**
 * 加载上游渠道新增或编辑表单。
 */
const EditUpstream = () => import('@/components/EditUpstream.vue');
/**
 * 加载当前账号个人资料和密码修改页面。
 */
const PersonalCenter = () => import('@/components/PersonalCenter.vue');
/**
 * 加载上游渠道管理列表。
 */
const UpStream = () => import('@/components/UpStream.vue');

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
    path: '/sso/callback',
    component: SsoCallbackPage,
    meta: {
      requiresAuth: false,
      ssoCallback: true
    }
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
        path: 'allWorkorder',
        component: AllWorkOrder
      },
      {
        path: 'renewWorkorder',
        component: RenewWorkorder
      }
    ]
  }
];

const router = createRouter({
  history: createWebHistory(process.env.BASE_URL),
  routes
});

/**
 * 在每次路由切换前根据本地 token 判断车险系统登录态。
 * 受保护页面在无会话时回到登录页；已登录用户访问登录页时进入工作台；SSO 回调必须始终放行，
 * 因为该页面需要先用一次性 code 换取 token，进入时本地还不存在会话。
 */
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
    if (to.meta.ssoCallback) {
      next();
    } else if (to.path === '/login' && isLogin) {
      next('/home');
    } else {
      next();
    }
  }
});

export default router;
