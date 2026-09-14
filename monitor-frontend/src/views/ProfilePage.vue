<template>
  <div>
    <PageHeader eyebrow="Account Settings" title="个人中心" description="维护当前登录账号的个人资料与登录密码。" />
    <div v-if="loading" class="panel loading-state">正在加载个人资料…</div>
    <div v-else class="profile-grid">
      <form class="panel" @submit.prevent="saveProfile">
        <section class="form-section">
          <h2>个人资料</h2>
          <div class="form-grid">
            <div class="field"><label for="profileUsername">登录手机号</label><input id="profileUsername" :value="profile.username" class="layui-input" type="tel" autocomplete="username" disabled /></div>
            <div class="field"><label for="profileRole">平台角色</label><input id="profileRole" :value="profile.roleName || roleLabel" class="layui-input" disabled /></div>
            <div class="field" :class="{ invalid: profileErrors.realName }"><label for="profileRealName">真实姓名 *</label><input id="profileRealName" v-model.trim="profile.realName" class="layui-input" maxlength="64" autocomplete="name" /><p v-if="profileErrors.realName" class="error-text">{{ profileErrors.realName }}</p></div>
            <div class="field" :class="{ invalid: profileErrors.email }"><label for="profileEmail">电子邮箱</label><input id="profileEmail" v-model.trim="profile.email" class="layui-input" type="email" maxlength="100" autocomplete="email" /><p v-if="profileErrors.email" class="error-text">{{ profileErrors.email }}</p></div>
          </div>
        </section>
        <footer class="form-footer"><button class="layui-btn monitor-primary" :disabled="savingProfile">{{ savingProfile ? "保存中…" : "保存个人资料" }}</button></footer>
      </form>

      <form class="panel" @submit.prevent="changePassword">
        <section class="form-section">
          <h2>修改密码</h2>
          <div class="password-fields">
            <div class="field" :class="{ invalid: passwordErrors.currentPassword }"><label for="currentPassword">当前密码 *</label><input id="currentPassword" v-model="password.currentPassword" class="layui-input" type="password" maxlength="200" autocomplete="current-password" /><p v-if="passwordErrors.currentPassword" class="error-text">{{ passwordErrors.currentPassword }}</p></div>
            <div class="field" :class="{ invalid: passwordErrors.newPassword }"><label for="newPassword">新密码 *</label><input id="newPassword" v-model="password.newPassword" class="layui-input" type="password" maxlength="200" autocomplete="new-password" /><p class="helper">至少 8 位，不能与当前密码相同</p><p v-if="passwordErrors.newPassword" class="error-text">{{ passwordErrors.newPassword }}</p></div>
            <div class="field" :class="{ invalid: passwordErrors.confirmPassword }"><label for="confirmPassword">确认新密码 *</label><input id="confirmPassword" v-model="password.confirmPassword" class="layui-input" type="password" maxlength="200" autocomplete="new-password" /><p v-if="passwordErrors.confirmPassword" class="error-text">{{ passwordErrors.confirmPassword }}</p></div>
          </div>
        </section>
        <div class="security-note"><i class="layui-icon layui-icon-vercode" aria-hidden="true"></i><p><strong>密码修改后需要重新登录</strong><span>当前登录会话将立即失效，其他设备上的旧会话也会按单登录机制失效。</span></p></div>
        <footer class="form-footer"><button class="layui-btn monitor-primary" :disabled="savingPassword">{{ savingPassword ? "修改中…" : "修改登录密码" }}</button></footer>
      </form>
    </div>
    <AppToast :message="toastMessage" :type="toastType" />
  </div>
</template>

<script>
import PageHeader from "@/components/PageHeader.vue";
import AppToast from "@/components/AppToast.vue";
import feedback from "@/mixins/feedback";
import { authApi } from "@/api/monitor";
import { authState, clearSession, saveSession } from "@/auth/session";

/**
 * 监控平台个人中心。资料修改只覆盖本人可维护字段，密码修改必须提供当前密码；
 * 角色、账号和状态保持只读，防止个人入口绕过平台用户管理权限。
 */
export default {
  name: "ProfilePage",
  components: { PageHeader, AppToast },
  mixins: [feedback],
  data: () => ({ profile: { username: "", roleCode: "", roleName: "", realName: "", email: "" }, password: { currentPassword: "", newPassword: "", confirmPassword: "" }, profileErrors: {}, passwordErrors: {}, loading: true, savingProfile: false, savingPassword: false }),
  computed: { roleLabel() { return this.profile.roleCode === "ADMIN" ? "管理员" : "售后客服"; } },
  /** 页面进入时读取数据库中的最新账号资料，不直接依赖可能过期的本地缓存。 */
  async mounted() { try { this.profile = { ...this.profile, ...(await authApi.me()) }; } catch (error) { this.errorMessage(error); } finally { this.loading = false; } },
  methods: {
    /** 校验本人可编辑的姓名与邮箱；登录手机号只由管理员账号管理入口维护。 */
    validateProfile() { const errors = {}; if (!this.profile.realName) errors.realName = "请输入真实姓名"; if (this.profile.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.profile.email)) errors.email = "请输入有效的电子邮箱"; this.profileErrors = errors; return !Object.keys(errors).length; },
    /** 保存本人资料后同步响应式会话与本地缓存，使页头姓名无需刷新即可更新。 */
    async saveProfile() { if (!this.validateProfile() || this.savingProfile) return; this.savingProfile = true; try { const user = await authApi.updateProfile({ realName: this.profile.realName, email: this.profile.email }); this.profile = { ...this.profile, ...user }; saveSession(authState.token, user); this.notify("个人资料已保存"); } catch (error) { this.errorMessage(error); } finally { this.savingProfile = false; } },
    /** 校验三项密码输入后提交；成功即清理本地会话并返回登录页，避免继续使用已撤销令牌。 */
    async changePassword() { const errors = {}; if (!this.password.currentPassword) errors.currentPassword = "请输入当前密码"; if (!this.password.newPassword || this.password.newPassword.length < 8) errors.newPassword = "新密码至少需要 8 位"; if (!this.password.confirmPassword) errors.confirmPassword = "请再次输入新密码"; else if (this.password.newPassword !== this.password.confirmPassword) errors.confirmPassword = "两次输入的新密码不一致"; this.passwordErrors = errors; if (Object.keys(errors).length || this.savingPassword) return; this.savingPassword = true; try { await authApi.changePassword(this.password); this.notify("密码已修改，请重新登录"); setTimeout(async () => { clearSession(); await this.$router.replace("/login"); }, 700); } catch (error) { this.errorMessage(error); this.savingPassword = false; } },
  },
};
</script>

<style scoped>
.profile-grid{display:grid;grid-template-columns:minmax(0,1.25fr) minmax(340px,.75fr);gap:16px;align-items:start}.form-grid>.field{min-width:0}.form-grid .layui-input{display:block;width:100%;box-sizing:border-box}.password-fields{display:grid;gap:14px}.form-footer{display:flex;justify-content:flex-end;padding:16px 20px;border-top:1px solid var(--border)}.security-note{display:flex;gap:11px;margin:0 20px 18px;padding:13px;border:1px solid #dbeafe;border-radius:7px;color:#1e40af;background:#eff6ff}.security-note i{font-size:20px}.security-note p{display:grid;gap:3px;margin:0}.security-note span{color:var(--text-secondary);font-size:12px}@media(max-width:900px){.profile-grid{grid-template-columns:1fr}}
</style>
