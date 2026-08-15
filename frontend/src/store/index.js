// src/store/index.js
import { createStore } from 'vuex';
import { handlePermsToMenu } from '@/utils/authenticate';

const helloModule = {
  namespaced: true,
  /**
   * * 创建演示模块的独立计数和用户状态，避免多个 Store 实例共享同一对象。
   */
  state() {
    return {
      count: 0,
      user: { name: '访客' }
    }
  },
  getters: {
    /**
     * * 返回示例计数的两倍，用于演示 Vuex getter 的派生状态。
     */
    doubleCount(state) {
      return state.count * 2
    },
    /**
     * * 从示例用户对象提取展示名称。
     */
    userName(state) {
      return state.user.name
    }
  },
  mutations: {
    /**
     * * 同步增加示例计数，供 Vuex 基础功能演示组件调用。
     */
    increment(state) {
      state.count++
    },
    /**
     * * 使用 action 返回的完整用户对象替换示例用户状态。
     */
    setUser(state, payload) {
      state.user = payload
    }
  },
  actions: {
    /**
     * * 等待调用方指定时长后提交 increment，用于演示异步 action 不直接修改 state。
     */
    async incrementAsync({ commit }, delay) {
      await new Promise(resolve => setTimeout(resolve, delay))
      commit('increment')
    },
    /**
     * * 使用本地模拟用户提交 setUser，保留为 Vuex action 调用示例，不参与正式登录流程。
     */
    async fetchUser({ commit }) {
      // 模拟 API 请求
      const mockUser = { name: '张三' }
      commit('setUser', mockUser)
    }
  }
}

const loginModule = {
  namespaced: true,
  /**
   * * 为每个 Store 实例创建车险登录用户、令牌和菜单权限的初始状态。
   */
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
    /**
     * 保存车险后端签发的访问令牌，供请求拦截器和页面登录态判断使用。
     * 持久化到浏览器的动作由登录页面负责，这里只维护当前 Vuex 运行时状态。
     */
    setToken(state, token){
      state.token = token;
    },
    /**
     * 保存当前用户资料，并根据后端权限编码同步计算可见的一、二级菜单。
     * 用户切换或重新登录时会整体替换旧资料，避免沿用上一账号的菜单权限。
     */
    setUser(state, user){
      state.user = user;
      state.menu = handlePermsToMenu(user.perms);
    },
    /**
     * 清空车险系统运行时会话及菜单权限。
     * 注销、令牌失效或 SSO 失败后调用，保证后续页面不会继续显示上一用户的数据入口。
     */
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
  /**
   * * 初始化当前账号的系统通知列表和续保统计，登录切换时可整体重置。
   */
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
    /**
     * * 返回通知列表长度，供角标或其他组件直接使用。
     */
    noticeCount(state) {
      return state.notices.length;
    }
  },
  mutations: {
    /**
     * 更新续保提醒数量，其中个人数量缺失时归零，全部数量保留 undefined 以表达接口尚未返回。
     * Header 和通知中心共享该状态，避免各自重复请求并出现角标不一致。
     */
    setRenewCount(state, payload) {
      state.renewCount = {
        selfCount: payload?.selfCount ?? 0,
        allCount: payload?.allCount
      };
    },
    /**
     * 按业务 key 新增或替换通知，保证同一类提醒在通知中心只保留一条最新记录。
     * 新通知放到列表头部；已存在的通知保持原位置并整体替换内容。
     */
    upsertNotice(state, notice) {
      const idx = state.notices.findIndex(n => n.key === notice.key);
      if (idx >= 0) {
        state.notices.splice(idx, 1, notice);
      } else {
        state.notices.unshift(notice);
      }
    },
    /**
     * * 移除指定业务 key 的通知，供用户关闭提醒或业务状态已处理时同步清理角标。
     */
    removeNotice(state, key) {
      state.notices = state.notices.filter(n => n.key !== key);
    },
    /**
     * * 在退出登录或切换账号时重置全部通知和续保统计，防止跨账号残留企业业务信息。
     */
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
