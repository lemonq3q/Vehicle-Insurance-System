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

/**

 * * 按顺序比较地区级联编码数组，避免父子组件在内容未变化时相互触发 v-model 更新形成循环。

 */
const isArrayEqual = (arr1, arr2) => {
  const a = Array.isArray(arr1) ? arr1 : [];
  const b = Array.isArray(arr2) ? arr2 : [];
  
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) return false;
  }
  return true;
};

/**

 * * 规范化级联选择器输出并仅在值真实变化时通知父组件，同时保持内部状态始终为数组。

 */
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
/**
 * * 监听父组件对 modelValue 的外部重置或回填；比较后再同步内部值，以兼容编辑页异步加载数据。
 */
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

/**

 * * 组件挂载时读取缓存后的全国省市选项，避免每个地区选择器重复转换静态行政区数据。

 */
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
