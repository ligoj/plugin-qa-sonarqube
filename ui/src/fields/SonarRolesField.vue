<template>
  <!-- CREATE mode role mapping: the project permissions of each group on the permission template. The live check keeps
       a malformed value from reaching the backend, which validates it again. -->
  <LigojTextarea
    :model-value="modelValue"
    :label="t(parameter.id)"
    :placeholder="rolesSample(project?.pkey)"
    :hint="t(`${parameter.id}-description`)"
    :rules="rules"
    persistent-hint
    variant="outlined"
    density="compact"
    rows="4"
    auto-grow
    @blur="trimValue"
    @update:model-value="(v) => emit('update:modelValue', v ?? '')"
  />
</template>

<script setup>
import { useI18nStore, LigojTextarea } from '@ligoj/host'
import { rolesError, rolesSample } from '../sonarJson.js'

const props = defineProps({
  modelValue: { type: [String, null], default: null },
  parameter: { type: Object, required: true },
  formValues: { type: Object, default: () => ({}) },
  mode: { type: String, default: null },
  isNode: { type: Boolean, default: false },
  nodeId: { type: String, default: null },
  instanceNodeId: { type: String, default: null },
  project: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue'])
const { t } = useI18nStore()

/** Saved trimmed: trim when the field loses the focus, which the submit click triggers first (not while typing). */
function trimValue() {
  const value = props.modelValue
  if (typeof value === 'string' && value.trim() !== value) emit('update:modelValue', value.trim())
}
const rules = [(v) => { const key = rolesError(v); return key ? t(key) : true }]
</script>
