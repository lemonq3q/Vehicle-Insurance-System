<template>
  <div class="header">
    <div class="header_left">
      <button class="sidebar_toggle" type="button" :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'" :aria-expanded="!sidebarCollapsed" @click="$emit('toggle-sidebar')">
        <i class="monitor_icon monitor_icon_shrink" aria-hidden="true"></i>
      </button>
      <nav class="header_breadcrumb" aria-label="当前位置">
        <span>车险管理</span>
        <i class="monitor_icon monitor_icon_right" aria-hidden="true"></i>
        <template v-if="route.meta.section">
          <span>{{ route.meta.section }}</span>
          <i class="monitor_icon monitor_icon_right" aria-hidden="true"></i>
        </template>
        <strong>{{ route.meta.title || '工单管理' }}</strong>
      </nav>
    </div>
    <div class="header_right">
      <el-button class="portal_return_button" type="primary" plain :loading="returningPortal" :disabled="returningPortal" @click="handleReturnPortal">
        <el-icon><back /></el-icon>
        <span>{{ returningPortal ? '正在返回...' : '返回门户' }}</span>
      </el-button>
      <div class="header_notice_wrapper">
        <SystemNotice />
      </div>
      <el-dropdown>
        <div class="header_user_container">
          <img src="../assets/user.png" alt="" class="header_user_img">
          <div style="font-size: 16px;" class="no_select">
          {{ "你好，" + username }}<el-icon class="el-icon--right"><arrow-down /></el-icon>
          </div>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <!-- <el-dropdown-item><span class="header_dropdown_button no_select" @click="handleOpenPassword">修改密码</span></el-dropdown-item> -->
            <el-dropdown-item><span class="header_dropdown_button no_select" @click="handleLogout">退出登录</span></el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>

  <!-- <el-dialog v-model="dialogVisible" title="修改密码">
    <div class="password_dialog">
      <el-form
        ref="passwordRef"
        style="width: 100%; max-width: 600px;"
        :model="passwordForm"
        status-icon
        :rules="rules"
        class="login_form"
      >
        <el-form-item prop="password">
          <el-input
          v-model="passwordForm.password"
          class="responsive-input"
          placeholder="请输入密码"
          size="large"
          show-password
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
          v-model="passwordForm.confirmPassword"
          class="responsive-input"
          placeholder="请确认密码"
          size="large"
          show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button  @click="handleUpdatePassword(passwordRef)" style="margin: 0 auto;">修改</el-button>
        </el-form-item>
      </el-form>
    </div>
  </el-dialog> -->
</template>

<script>
export default {
  props: { sidebarCollapsed: { type: Boolean, default: false } },
  emits: ['toggle-sidebar'],
};
</script>

<script setup>
import { ArrowDown, Back } from '@element-plus/icons-vue';
import { createPortalAuthorization, logout } from '@/api/login';
import { selectRenewCount } from '@/api/workorder';
import { useRoute, useRouter } from 'vue-router';
import { useStore } from 'vuex';
import { computed, onMounted, ref } from 'vue';
// import { validateStr } from '@/utils/validate';
// import { updatePassword } from '@/api/user';
import Message from '@/utils/message';
import { jsonStrToObj } from '@/utils/convert';
import SystemNotice from '@/components/SystemNotice.vue';

const store = useStore();
const route = useRoute();
/**
 * * 优先从用户资料中的姓名字段生成页头问候语，并兼容历史 realName、username 字段。
 */
const username = computed(() => {
  const user = jsonStrToObj(localStorage.getItem('userInfo')) ?? {};
  return user.name || user.realName || user.username || '';
});
const router = useRouter();
const returningPortal = ref(false);

/**
 * 申请车险系统到 SaaS 门户的单点登录授权，并跳转后端返回的完整门户地址。
 * 请求期间锁定按钮防止重复签发 code；授权失败时恢复按钮并保留当前车险会话供用户重试。
 */
const handleReturnPortal = async () => {
  if (returningPortal.value) return;
  returningPortal.value = true;
  try {
    const response = await createPortalAuthorization();
    const payload = response.data;
    if (Number(payload.code) !== 200 || !payload.data?.redirectUrl) {
      throw new Error(payload.msg || '返回门户授权失败');
    }
    window.location.assign(payload.data.redirectUrl);
  } catch (error) {
    returningPortal.value = false;
    Message.error(error.response?.data?.msg || error.message || '返回门户授权失败');
  }
};
// const dialogVisible = ref(false);
// const passwordRef = ref();

// const passwordForm = reactive({
//   password: '',
//   confirmPassword: '',
// });

// const validatePassword = (rule, value) => {
//   return new Promise((resolve, reject) => {
//     if (value !== passwordForm.password) {
//       reject('两次输入的密码不一致');
//     }
//     resolve();
//   });
// }

// const rules = {
//   password: [
//     { required: true, message: '请输入密码', trigger: 'blur' },
//     { min: 6, max: 16, message: '密码长度需在 6 到 16 个字符', trigger: 'blur' },
//     { validator: validateStr, trigger: 'blur' },
//   ],
//   confirmPassword: [
//     { validator: validatePassword, trigger: 'blur' },
//   ],
// };

/**
 * 拉取个人及企业续保窗口数量，同步页头角标，并按固定 key 新增、替换或移除续保通知。
 * 管理员可看到全企业数量，普通用户只使用个人数量；请求失败不阻断页头和其他业务页面加载。
 */
const refreshRenewCount = async () => {
  try{
    await selectRenewCount().then(res => {
      res = res.data;
      if (res.code === 200) {
        store.commit('notice/setRenewCount', res.data);
        const selfCount = res.data?.selfCount ?? 0;
        const allCount = res.data?.allCount;
        const hasMsg = selfCount > 0 || (allCount != null && allCount > 0);
        if (hasMsg) {
          let content = `你有 ${selfCount} 个工单进入续保窗口`;
          if (allCount != null) {
            content = `我的工单：${selfCount} 个；全部工单：${allCount} 个（当前续保窗口）`;
          }
          store.commit('notice/upsertNotice', {
            key: 'renew',
            title: '续保提醒',
            content,
            route: '/home/renewWorkorder',
            createdAt: Date.now()
          });
        } else {
          store.commit('notice/removeNotice', 'renew');
        }
      }
    });
  }
  catch{
    return;
  }
}

/**

 * * 页头挂载后立即刷新一次续保统计，使登录或 SSO 进入工作台时无需打开续保页即可看到提醒。

 */
onMounted(() => {
  refreshRenewCount();
});


/**
 * 注销后端会话，并在成功响应后清除 Vuex 登录态、通知及浏览器身份缓存，最后返回登录页。
 * 只有后端确认注销成功才清理本地数据，避免网络瞬断时用户被错误踢出当前页面。
 */
const handleLogout = () => {
  logout().then(res => { 
    res = res.data;
    if (res.code === 200) {
      store.commit('login/clear');
      store.commit('notice/clear');
      localStorage.removeItem('token');
      localStorage.removeItem('userInfo');
      router.push('/login');
    }
  });
  
}

// const handleOpenPassword = () => {
//   dialogVisible.value = true;
// }

// const handleUpdatePassword = async (formEl) => {
//   if (!formEl) return;
//   formEl.validate((valid) => {
//     if(valid){
//       let user = {
//         id: store.state.login.user.id,
//         password: passwordForm.password
//       }
//       updatePassword(user).then(res => {
//         res = res.data;
//         if (res.code == 200){
//           Message.success('密码修改成功');
//         }
//       });
//     }
//   });
// }
</script>


<style scoped>
.header_dropdown_button {
  padding: 10px;
  font-size: 16px !important;
}

.header {
  padding: 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  height: 68px;
  min-height: 68px;
  background-color: rgba(255, 255, 255, .94);
  border-bottom: 1px solid var(--insurance-border);
  color: var(--insurance-text);
  backdrop-filter: blur(12px);
}
.header_left {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
}
.sidebar_toggle {
  display: grid;
  place-items: center;
  flex: 0 0 44px;
  width: 44px;
  height: 44px;
  padding: 0;
  border: 1px solid var(--insurance-border);
  border-radius: 8px;
  background: #fff;
  color: var(--insurance-secondary);
  cursor: pointer;
}
.sidebar_toggle:hover { background: #eff6ff; border-color: #93c5fd; color: var(--insurance-primary); }
.sidebar_toggle:focus-visible { outline: 2px solid rgba(71, 85, 105, .42); outline-offset: 2px; }
@font-face {
  font-family: 'insurance-monitor-icon';
  src: url('@/assets/fonts/monitor-iconfont.woff2') format('woff2');
  font-display: block;
}
.monitor_icon {
  font-family: 'insurance-monitor-icon' !important;
  font-size: 16px;
  font-style: normal;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}
.monitor_icon_shrink::before { content: '\e668'; }
.monitor_icon_right::before { content: '\e602'; }
.header_breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  white-space: nowrap;
  color: var(--insurance-muted);
  font-size: 14px;
  line-height: 1.55;
  font-family: 'Open Sans', 'PingFang SC', 'Microsoft YaHei', Arial, sans-serif;
}
.header_breadcrumb .monitor_icon { flex: 0 0 auto; font-size: 12px; }
.header_breadcrumb strong { overflow: hidden; text-overflow: ellipsis; color: var(--insurance-text); }
.header_right { flex: 0 0 auto; }

.header_right {
  display: flex;
  align-items: center;
}

.portal_return_button {
  margin-right: 20px;
}

.header_user_container {
  display: flex;
  align-items: center;
  color: var(--insurance-secondary);
  cursor: pointer;
}

.header_user_img {
  width: 40px;
  height: 40px;
  margin-right: 10px;
}

.password_dialog {
  width: 100%;
  display: flex;
  justify-content: center;
}

.header_notice_wrapper {
  margin-right: 20px;
  display: flex;
  align-items: center;
  height: 100%;
}
@media (max-width: 900px) {
  .portal_return_button { margin-right: 10px; }
  .header_notice_wrapper { margin-right: 10px; }
}
@media (max-width: 620px) {
  .header { padding: 0 12px; }
}

</style>
