<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  valueType: string
  modelValue: string
  label?: string
}>()
const emit = defineEmits<{ (e: 'update:modelValue', value: string): void }>()

const value = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v)
})
</script>

<template>
  <div>
    <select v-if="valueType === 'BOOLEAN'" v-model="value" class="input" style="width:100%">
      <option value="true">true</option>
      <option value="false">false</option>
    </select>
    <textarea v-else-if="valueType === 'JSON'" v-model="value" class="input" rows="4" style="width:100%"></textarea>
    <input v-else-if="valueType === 'NUMBER'" v-model="value" class="input" type="number" style="width:100%" />
    <input v-else v-model="value" class="input" type="text" style="width:100%" />
    <div class="hint">
      {{ valueType === 'BOOLEAN' ? 'BOOLEAN：true / false'
        : valueType === 'NUMBER' ? 'NUMBER：必须是数字'
        : valueType === 'JSON' ? 'JSON：必须是合法 JSON 对象或数组'
        : 'STRING：任意文本' }}
    </div>
  </div>
</template>
