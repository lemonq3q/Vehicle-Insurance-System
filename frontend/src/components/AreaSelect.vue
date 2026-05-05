<template>
  <div class="area_select">
    <el-cascader
      v-model="innerSelectedArea" 
      :options="options" 
      clearable 
      :props="cascaderProps"
      filterable
      :placeholder="props.placeholder"
      @change="handleCascaderChange" 
    />
  </div>
</template>

<script setup>
import { ref, watch, defineProps, defineEmits, onMounted } from 'vue';
import { getSelectOption } from '@/utils/ChinaCitys';

// 1. 定义接收父组件的属性
const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  placeholder: {
    type: String,
    default: '请选择地区'
  },
  multi: {
    type: Boolean,
    default: false
  }
});


const emit = defineEmits(['update:modelValue']);

const innerSelectedArea = ref([...props.modelValue]);
const options = ref([]); 
const cascaderProps = { multiple: props.multi };

const isArrayEqual = (arr1, arr2) => {
  const a = Array.isArray(arr1) ? arr1 : [];
  const b = Array.isArray(arr2) ? arr2 : [];
  
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
};

const handleCascaderChange = (val) => {
  const normalizedVal = Array.isArray(val) ? [...val] : [];
  // 仅当值真正变化时，才派发给父组件
  if (!isArrayEqual(normalizedVal, props.modelValue)) {
    emit('update:modelValue', normalizedVal);
  }
  // 确保内部值始终是数组
  innerSelectedArea.value = normalizedVal;
};

// 仅监听父组件值变化
watch(
  () => props.modelValue,
  (newVal) => {
    const normalizedVal = Array.isArray(newVal) ? [...newVal] : [];
    // 仅当父组件值与内部值不同时，才更新内部值
    if (!isArrayEqual(normalizedVal, innerSelectedArea.value)) {
      innerSelectedArea.value = normalizedVal;
    }
  },
  { deep: true, immediate: true }
);

onMounted(() => {
  const areaOptions = getSelectOption();
  if (areaOptions && areaOptions.length) {
    options.value = areaOptions;
  }
});
</script>

<style scoped>
.area_select :deep(.el-cascader) {
  width: 100%;
}
</style>