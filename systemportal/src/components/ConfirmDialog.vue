<template>
  <div v-if="visible" class="confirm-dialog" role="presentation" @click.self="cancel">
    <section
      class="confirm-dialog__panel"
      role="alertdialog"
      aria-modal="true"
      :aria-labelledby="titleId"
      :aria-describedby="messageId"
    >
      <div class="confirm-dialog__content">
        <span class="confirm-dialog__icon" aria-hidden="true">
          <i class="layui-icon layui-icon-tips-fill"></i>
        </span>
        <div>
          <h2 :id="titleId">{{ title }}</h2>
          <p :id="messageId">{{ message }}</p>
        </div>
      </div>

      <div class="confirm-dialog__actions">
        <button class="layui-btn layui-btn-primary" type="button" :disabled="loading" @click="cancel">
          {{ cancelText }}
        </button>
        <button class="layui-btn layui-btn-danger" type="button" :disabled="loading" @click="$emit('confirm')">
          {{ loading ? '处理中...' : confirmText }}
        </button>
      </div>
    </section>
  </div>
</template>

<script>
export default {
  name: 'ConfirmDialog',
  props: {
    visible: {
      type: Boolean,
      default: false
    },
    title: {
      type: String,
      default: '确认操作'
    },
    message: {
      type: String,
      required: true
    },
    confirmText: {
      type: String,
      default: '确认'
    },
    cancelText: {
      type: String,
      default: '取消'
    },
    loading: {
      type: Boolean,
      default: false
    }
  },
  emits: ['confirm', 'cancel'],
  computed: {
    titleId() {
      return `${this.$.uid}-confirm-dialog-title`;
    },
    messageId() {
      return `${this.$.uid}-confirm-dialog-message`;
    }
  },
  methods: {
    cancel() {
      if (!this.loading) this.$emit('cancel');
    }
  }
};
</script>

<style scoped>
.confirm-dialog {
  position: fixed;
  inset: 0;
  z-index: 2000;
  display: grid;
  place-items: center;
  padding: 24px;
  background: rgb(15 23 42 / 52%);
}

.confirm-dialog__panel {
  width: min(420px, 100%);
  padding: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 20px 48px rgb(15 23 42 / 20%);
}

.confirm-dialog__content {
  display: flex;
  align-items: flex-start;
  gap: 14px;
}

.confirm-dialog__icon {
  display: grid;
  flex: 0 0 40px;
  width: 40px;
  height: 40px;
  place-items: center;
  border-radius: 50%;
  color: #b45309;
  background: #fffbeb;
}

.confirm-dialog__icon .layui-icon {
  font-size: 22px;
}

.confirm-dialog h2 {
  margin: 1px 0 8px;
  color: #0f172a;
  font-size: 18px;
  line-height: 1.4;
}

.confirm-dialog p {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.7;
}

.confirm-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}

.confirm-dialog__actions .layui-btn {
  min-width: 88px;
  margin: 0;
}

.confirm-dialog__actions .layui-btn:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 480px) {
  .confirm-dialog {
    padding: 16px;
  }

  .confirm-dialog__panel {
    padding: 20px;
  }
}
</style>
