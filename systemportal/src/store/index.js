import { createStore } from 'vuex';
import Storage from '@/utils/storage';
import { getAccountContext, login } from '@/api/portal';

const store = createStore({
  state() {
    return {
      token: Storage.get('portalToken'),
      user: Storage.get('portalUser'),
      enterprises: [],
      currentEnterprise: null,
      currentMember: null,
      testRoleCode: '',
      sidebarCollapsed: false
    };
  },
  getters: {
    isLogin(state) {
      return Boolean(state.token);
    },
    roleCode(state) {
      return state.testRoleCode || state.currentMember?.roleCode || '';
    },
    canManageEnterprise(state, getters) {
      return ['OWNER', 'ADMIN'].includes(getters.roleCode);
    },
    canManageFinance(state, getters) {
      return ['OWNER', 'ADMIN'].includes(getters.roleCode);
    },
    isOwner(state, getters) {
      return getters.roleCode === 'OWNER';
    }
  },
  mutations: {
    setToken(state, token) {
      state.token = token;
      Storage.set('portalToken', token, 60 * 60 * 24);
    },
    setContext(state, context) {
      state.user = context.user;
      state.enterprises = context.enterprises || [];
      state.currentEnterprise = context.currentEnterprise;
      state.currentMember = context.currentMember;
      if (!context.currentMember) state.testRoleCode = '';
      Storage.set('portalUser', context.user);
    },
    setSidebarCollapsed(state, value) {
      state.sidebarCollapsed = value;
    },
    setTestRoleCode(state, roleCode) {
      state.testRoleCode = roleCode;
    },
    clearAuth(state) {
      state.token = null;
      state.user = null;
      state.enterprises = [];
      state.currentEnterprise = null;
      state.currentMember = null;
      state.testRoleCode = '';
      Storage.remove('portalToken');
      Storage.remove('portalUser');
    }
  },
  actions: {
    async login({ commit }, payload) {
      const response = await login(payload);
      commit('setToken', response.data.token);
      commit('setContext', response.data);
      return response;
    },
    async loadContext({ commit }) {
      const response = await getAccountContext();
      commit('setContext', response.data);
      return response;
    },
    logout({ commit }) {
      commit('clearAuth');
    }
  }
});

export default store;
