import { createStore } from 'vuex';
import Storage from '@/utils/storage';
import { getAccountContext, login } from '@/api/portal';

const store = createStore({
  /**
   * * 从本地缓存恢复门户令牌和用户，企业、成员及上下文加载状态则在运行时重新向后端确认。
   */
  state() {
    return {
      token: Storage.get('portalToken'),
      user: Storage.get('portalUser'),
      enterprises: [],
      currentEnterprise: null,
      currentMember: null,
      contextLoaded: false,
      testRoleCode: '',
      sidebarCollapsed: false
    };
  },
  getters: {
    /**
     * * 仅以门户 token 是否存在判断基础登录态，企业权限需等待上下文加载后再判断。
     */
    isLogin(state) {
      return Boolean(state.token);
    },
    /**
     * * 返回当前生效角色；开发测试角色优先于真实成员角色，未加入企业时返回空串。
     */
    roleCode(state) {
      return state.testRoleCode || state.currentMember?.roleCode || '';
    },
    /**
     * * 企业拥有者和管理员可以维护企业资料、邀请及成员信息。
     */
    canManageEnterprise(state, getters) {
      return ['OWNER', 'ADMIN'].includes(getters.roleCode);
    },
    /**
     * * 企业拥有者和管理员可以充值、订阅及查看完整财务数据。
     */
    canManageFinance(state, getters) {
      return ['OWNER', 'ADMIN'].includes(getters.roleCode);
    },
    /**
     * * 判断当前成员是否为企业唯一拥有者，用于所有权转移等高权限操作。
     */
    isOwner(state, getters) {
      return getters.roleCode === 'OWNER';
    }
  },
  mutations: {
    /**
     * * 同步门户 token 到 Vuex 和带一天有效期的本地缓存。
     */
    setToken(state, token) {
      state.token = token;
      Storage.set('portalToken', token, 60 * 60 * 24);
    },
    /**
     * 使用后端账号上下文整体更新用户、企业列表、当前企业和成员，并标记上下文已加载。
     * 未加入企业时清除测试角色，避免虚拟权限跨越企业边界。
     */
    setContext(state, context) {
      state.user = context.user;
      state.enterprises = context.enterprises || [];
      state.currentEnterprise = context.currentEnterprise;
      state.currentMember = context.currentMember;
      state.contextLoaded = true;
      if (!context.currentMember) state.testRoleCode = '';
      Storage.set('portalUser', context.user);
    },
    /**
     * * 保存侧栏折叠状态，供门户布局和样式同步响应。
     */
    setSidebarCollapsed(state, value) {
      state.sidebarCollapsed = value;
    },
    /**
     * * 设置仅用于前端权限展示验证的临时角色，不写入后端成员关系。
     */
    setTestRoleCode(state, roleCode) {
      state.testRoleCode = roleCode;
    },
    /**
     * * 清空全部门户身份、企业上下文和测试角色，并删除本地令牌与用户缓存。
     */
    clearAuth(state) {
      state.token = null;
      state.user = null;
      state.enterprises = [];
      state.currentEnterprise = null;
      state.currentMember = null;
      state.contextLoaded = false;
      state.testRoleCode = '';
      Storage.remove('portalToken');
      Storage.remove('portalUser');
    }
  },
  actions: {
    /**
     * * 提交门户登录凭据并保存 token；响应已包含企业上下文时直接采用，否则额外查询账号上下文。
     */
    async login({ commit, dispatch }, payload) {
      const response = await login(payload);
      commit('setToken', response.data.token);
      if (Object.prototype.hasOwnProperty.call(response.data, 'currentEnterprise')
        && Object.prototype.hasOwnProperty.call(response.data, 'currentMember')) {
        commit('setContext', response.data);
      } else {
        await dispatch('loadContext');
      }
      return response;
    },
    /**
     * * 从后端重新加载当前账号及企业成员上下文，作为刷新页面和权限判断的权威数据。
     */
    async loadContext({ commit }) {
      const response = await getAccountContext();
      commit('setContext', response.data);
      return response;
    },
    /**
     * * 注销门户本地会话；当前后端无需单独注销接口，因此只执行统一身份清理。
     */
    logout({ commit }) {
      commit('clearAuth');
    }
  }
});

export default store;
