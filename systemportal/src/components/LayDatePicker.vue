<template>
  <div class="lay-date-picker">
    <input ref="input" class="layui-input" type="text" :placeholder="placeholder" readonly />
    <i class="layui-icon layui-icon-date" aria-hidden="true"></i>
  </div>
</template>

<script>
import 'layui';

export default {
  name: 'LayDatePicker',
  props: {
    modelValue: { type: String, default: '' },
    range: { type: Boolean, default: false },
    placeholder: { type: String, default: '请选择时间' }
  },
  emits: ['update:modelValue', 'change'],
  /**
   * 挂载后通过 layui 初始化日期控件，并在用户选定日期时同时更新 v-model 与发送业务 change 事件，
   * 使组件既支持双向绑定，也支持页面执行额外校验或查询。
   */
  mounted() {
    window.layui.use('laydate', laydate => {
      this.instance = laydate.render({
        elem: this.$refs.input,
        type: 'date',
        range: this.range,
        value: this.modelValue,
        done: value => {
          this.$emit('update:modelValue', value);
          this.$emit('change', value);
        }
      });
    });
  },
  /**
   * 组件销毁时释放对 layui 实例的本地引用，避免后续逻辑误用已经脱离 DOM 的日期控件。
   */
  beforeUnmount() {
    this.instance = null;
  }
};
</script>

<style scoped>
.lay-date-picker {
  position: relative;
  min-width: 290px;
}

.layui-input {
  padding-right: 38px;
  cursor: pointer;
}

.layui-icon {
  position: absolute;
  top: 50%;
  right: 12px;
  color: var(--portal-muted);
  pointer-events: none;
  transform: translateY(-50%);
}
</style>
