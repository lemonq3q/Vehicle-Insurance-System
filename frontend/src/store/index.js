// src/store/index.js
import { createStore } from 'vuex';
import { handlePermsToMenu } from '@/utils/authenticate';

const helloModule = {
  namespaced: true,
  state() {
    return {
      count: 0,
      user: { name: '访客' }
    }
  },
  getters: {
    doubleCount(state) {
      return state.count * 2
    },
    userName(state) {
      return state.user.name
    }
  },
  mutations: {
    increment(state) {
      state.count++
    },
    setUser(state, payload) {
      state.user = payload
    }
  },
  actions: {
    async incrementAsync({ commit }, delay) {
      await new Promise(resolve => setTimeout(resolve, delay))
      commit('increment')
    },
    async fetchUser({ commit }) {
      // 模拟 API 请求
      const mockUser = { name: '张三' }
      commit('setUser', mockUser)
    }
  }
}

const loginModule = {
  namespaced: true,
  state() {
    return {
      user: {
        id: null,
        username: null,
        name: null,
        perms: null
      },
      token: null,
      menu: {
        firstMenu: ["upstream-downstream"],
        secondMenu: ["upstream", "downstream"]
      }
    }
  },

  getters: {

  },

  mutations: {
    setToken(state, token){
      state.token = token;
    },
    setUser(state, user){
      state.user = user;
      state.menu = handlePermsToMenu(user.perms);
    },
    clear(state){
      state.user = {
        id: null,
        username: null,
        name: null,
        perms: null
      };
      state.token = null;
      state.menu = {
        firstMenu: [],
        secondMenu: []
      };
    }
  },
  actions: {

  }
};

const noticeModule = {
  namespaced: true,
  state() {
    return {
      notices: [],
      renewCount: {
        selfCount: 0,
        allCount: undefined
      }
    }
  },
  getters: {
    noticeCount(state) {
      return state.notices.length;
    }
  },
  mutations: {
    setRenewCount(state, payload) {
      state.renewCount = {
        selfCount: payload?.selfCount ?? 0,
        allCount: payload?.allCount
      };
    },
    upsertNotice(state, notice) {
      const idx = state.notices.findIndex(n => n.key === notice.key);
      if (idx >= 0) {
        state.notices.splice(idx, 1, notice);
      } else {
        state.notices.unshift(notice);
      }
    },
    removeNotice(state, key) {
      state.notices = state.notices.filter(n => n.key !== key);
    },
    clear(state) {
      state.notices = [];
      state.renewCount = { selfCount: 0, allCount: undefined };
    }
  },
  actions: {

  }
}

const store = createStore({
  modules: {
    hello: helloModule,
    login: loginModule,
    notice: noticeModule
  }
});

export default store;
