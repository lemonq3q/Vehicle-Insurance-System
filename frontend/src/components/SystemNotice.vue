<template>
  <el-popover placement="bottom" trigger="hover" :width="360">
    <template #reference>
      <el-badge :value="badgeValue" :hidden="badgeValue <= 0" class="notice_badge">
        <el-icon class="notice_icon"><Message /></el-icon>
      </el-badge>
    </template>
    <div class="notice_panel">
      <div class="notice_panel_header">
        <span>系统提示</span>
        <el-button v-if="notices.length > 0" text size="small" @click="handleClear">清空</el-button>
      </div>
      <div v-if="notices.length === 0" class="notice_empty">暂无提示</div>
      <div v-else class="notice_list">
        <div v-for="item in notices" :key="item.key" class="notice_item" @click="handleClick(item)">
          <div class="notice_title">{{ item.title }}</div>
          <div class="notice_content">{{ item.content }}</div>
        </div>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { Message } from '@element-plus/icons-vue';
import { computed } from 'vue';
import { useStore } from 'vuex';
import { useRouter } from 'vue-router';
import { jsonStrToObj } from '@/utils/convert';

const store = useStore();
const router = useRouter();

/**

 * * 从 Vuex 读取已经按业务 key 去重的系统通知列表，供弹层统一渲染。

 */
const notices = computed(() => store.state.notice.notices);
/**
 * * 读取个人与全企业续保数量，作为通知角标的权威数据源。
 */
const renewCount = computed(() => store.state.notice.renewCount);

/**
 * 根据当前用户权限选择角标口径：拥有 all 权限的管理员优先显示全企业续保数，普通用户显示个人数。
 * 接口尚未返回全企业数量时回退到个人数量，避免角标短暂显示 undefined。
 */
const badgeValue = computed(() => {
  const user = jsonStrToObj(localStorage.getItem('userInfo'));
  const isAdmin = user?.perms?.includes('all');
  if (isAdmin) {
    return renewCount.value.allCount ?? renewCount.value.selfCount ?? 0;
  }
  return renewCount.value.selfCount ?? 0;
});

/**

 * * 点击通知时进入通知携带的业务路由；没有路由的纯提示通知只展示内容，不改变当前页面。

 */
const handleClick = (item) => {
  if (item?.route) {
    router.push(item.route);
  }
};

/**

 * * 清空当前账号运行时通知和续保角标；后续页头刷新仍可根据真实待办重新生成续保通知。

 */
const handleClear = () => {
  store.commit('notice/clear');
};
</script>

<style scoped>
.notice_badge {
  margin-right: 18px;
}

.notice_icon {
  font-size: 22px;
  color: var(--insurance-primary);
  cursor: pointer;
}

.notice_panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice_panel_header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 14px;
  color: var(--insurance-text);
}

.notice_empty {
  font-size: 13px;
  color: var(--insurance-muted);
  padding: 10px 0;
}

.notice_list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice_item {
  padding: 10px;
  border: 1px solid var(--insurance-border);
  border-radius: 8px;
  cursor: pointer;
}

.notice_item:hover {
  border-color: #b7c7ed;
  background: #f4f8ff;
}

.notice_title {
  font-size: 14px;
  color: var(--insurance-text);
  margin-bottom: 4px;
}

.notice_content {
  font-size: 13px;
  color: var(--insurance-secondary);
  line-height: 18px;
}
</style>
