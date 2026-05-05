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

const notices = computed(() => store.state.notice.notices);
const renewCount = computed(() => store.state.notice.renewCount);

const badgeValue = computed(() => {
  const user = jsonStrToObj(localStorage.getItem('userInfo'));
  const isAdmin = user?.perms?.includes('all');
  if (isAdmin) {
    return renewCount.value.allCount ?? renewCount.value.selfCount ?? 0;
  }
  return renewCount.value.selfCount ?? 0;
});

const handleClick = (item) => {
  if (item?.route) {
    router.push(item.route);
  }
};

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
  color: #409EFF;
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
  color: #303133;
}

.notice_empty {
  font-size: 13px;
  color: #909399;
  padding: 10px 0;
}

.notice_list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notice_item {
  padding: 10px;
  border: 1px solid #EBEEF5;
  border-radius: 6px;
  cursor: pointer;
}

.notice_item:hover {
  border-color: #C6E2FF;
  background: #ECF5FF;
}

.notice_title {
  font-size: 14px;
  color: #303133;
  margin-bottom: 4px;
}

.notice_content {
  font-size: 13px;
  color: #606266;
  line-height: 18px;
}
</style>

