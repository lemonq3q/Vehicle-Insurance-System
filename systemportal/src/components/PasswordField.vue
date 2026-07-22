<template>
  <div class="password-field">
    <input
      :id="id"
      :value="modelValue"
      class="layui-input"
      :type="visible ? 'text' : 'password'"
      :autocomplete="autocomplete"
      @input="$emit('update:modelValue', $event.target.value)"
    />
    <button
      class="password-toggle"
      type="button"
      :aria-label="visible ? '隐藏密码' : '显示密码'"
      :aria-pressed="visible"
      :title="visible ? '隐藏密码' : '显示密码'"
      @click="visible = !visible"
    >
      <i class="layui-icon" :class="visible ? 'layui-icon-eye-invisible' : 'layui-icon-eye'"></i>
    </button>
  </div>
</template>

<script>
export default {
  name: 'PasswordField',
  emits: ['update:modelValue'],
  props: {
    id: { type: String, required: true },
    modelValue: { type: String, default: '' },
    autocomplete: { type: String, default: 'current-password' }
  },
  data() {
    return { visible: false };
  }
};
</script>

<style scoped>
.password-field {
  position: relative;
}

.password-field .layui-input {
  min-height: 44px;
  padding-right: 48px;
}

.password-toggle {
  position: absolute;
  top: 50%;
  right: 0;
  width: 44px;
  height: 44px;
  border: 0;
  background: transparent;
  color: #475569;
  cursor: pointer;
  transform: translateY(-50%);
}

.password-toggle:hover {
  color: #166534;
}

.password-toggle:focus-visible {
  border-radius: 4px;
  outline: 3px solid rgba(22, 101, 52, 0.35);
  outline-offset: -4px;
}
</style>
