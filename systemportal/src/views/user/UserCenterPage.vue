<template>
  <section>
    <div class="section-title">
      <div>
        <h1>用户中心</h1>
      </div>
    </div>

    <article class="portal-card profile-card">
      <div class="profile-aside">
        <div class="avatar">{{ avatarText }}</div>
        <h2>{{ profile.realName || '未命名用户' }}</h2>
        <p>{{ profile.username }}</p>
        <span class="portal-tag">账号启用</span>
      </div>

      <div class="profile-content">
        <div class="profile-title-row">
          <div>
            <h2>个人资料</h2>
            <p>账号基础信息与身份资料</p>
          </div>
          <button v-if="!isEditing" class="layui-btn layui-btn-primary layui-border-green portal-btn" type="button" @click="beginEdit">
            <i class="layui-icon layui-icon-edit"></i>
            修改信息
          </button>
        </div>

        <dl v-if="!isEditing" class="profile-info-grid">
          <div><dt>登录账号</dt><dd>{{ profile.username || '-' }}</dd></div>
          <div><dt>姓名</dt><dd>{{ profile.realName || '-' }}</dd></div>
          <div><dt>手机号</dt><dd>{{ profile.phone || '-' }}</dd></div>
          <div><dt>证件号</dt><dd>{{ maskedIdNum }}</dd></div>
          <div><dt>账号状态</dt><dd><span class="status-dot"></span>正常</dd></div>
          <div><dt>上次登录</dt><dd>{{ profile.lastLoginTime || '-' }}</dd></div>
        </dl>

        <form v-else class="form-grid profile-form" @submit.prevent="saveProfile">
          <div class="form-field">
            <label for="username">登录账号</label>
            <input id="username" v-model="form.username" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="realName">姓名</label>
            <input id="realName" v-model="form.realName" class="layui-input" />
          </div>
          <div class="form-field">
            <label for="phone">手机号</label>
            <input id="phone" v-model="form.phone" class="layui-input" autocomplete="tel" />
          </div>
          <div class="form-field full">
            <label for="idNum">证件号</label>
            <input id="idNum" v-model="form.idNum" class="layui-input" />
          </div>
          <div class="action-row full">
            <button class="layui-btn layui-btn-primary portal-btn" type="button" @click="cancelEdit">取消</button>
            <button class="layui-btn portal-btn portal-btn-primary" type="submit">保存个人资料</button>
          </div>
        </form>
      </div>
    </article>

  </section>
</template>

<script>
import { getProfile, updateProfile } from '@/api/portal';

export default {
  name: 'UserCenterPage',
  data() {
    return {
      profile: {},
      form: {},
      isEditing: false
    };
  },
  computed: {
    avatarText() {
      return (this.profile.realName || this.profile.username || '用').slice(0, 1);
    },
    maskedIdNum() {
      const idNum = this.profile.idNum || '';
      if (!idNum) return '-';
      if (idNum.length <= 8) return idNum;
      return `${idNum.slice(0, 4)}${'*'.repeat(idNum.length - 8)}${idNum.slice(-4)}`;
    }
  },
  async created() {
    const response = await getProfile();
    this.profile = response.data;
    this.form = { ...response.data };
  },
  methods: {
    beginEdit() {
      this.form = { ...this.profile };
      this.isEditing = true;
    },
    cancelEdit() {
      this.form = { ...this.profile };
      this.isEditing = false;
    },
    async saveProfile() {
      const response = await updateProfile(this.form);
      this.profile = response.data;
      this.form = { ...response.data };
      this.isEditing = false;
      await this.$store.dispatch('loadContext');
    }
  }
};
</script>

<style scoped>
.profile-card {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 22px;
  padding: 24px;
}

.profile-aside {
  padding: 26px;
  border-radius: 8px;
  background: #f8fafc;
  text-align: center;
}

.avatar {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76px;
  height: 76px;
  margin: 0 auto 16px;
  border-radius: 8px;
  background: #dcfce7;
  color: #166534;
  font-size: 32px;
  font-weight: 800;
}

.profile-aside h2 {
  margin: 0 0 6px;
}

.profile-aside p {
  color: var(--portal-muted);
}

.profile-content {
  min-width: 0;
}

.profile-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 18px;
  border-bottom: 1px solid var(--portal-border);
}

.profile-title-row h2,
.profile-title-row p {
  margin: 0;
}

.profile-title-row p {
  margin-top: 4px;
  color: var(--portal-muted);
  font-size: 13px;
}

.profile-title-row .layui-icon {
  margin-right: 5px;
}

.profile-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 28px;
  margin: 0;
}

.profile-info-grid div {
  min-width: 0;
  padding: 18px 0;
  border-bottom: 1px solid var(--portal-border);
}

.profile-info-grid dt {
  margin-bottom: 5px;
  color: var(--portal-muted);
  font-size: 13px;
}

.profile-info-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-weight: 600;
}

.status-dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  margin-right: 7px;
  border-radius: 50%;
  background: var(--portal-accent);
}

.profile-form {
  align-content: start;
  padding-top: 20px;
}

@media (max-width: 880px) {
  .profile-card {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .profile-title-row {
    align-items: stretch;
    flex-direction: column;
  }

  .profile-info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
