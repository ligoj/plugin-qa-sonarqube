<template>
  <!-- CREATE mode: the permission template name and the project key pattern it applies to, prefilled from the Ligoj
       project key while empty (`demo-1`, `demo-1:.*`), editable. -->
  <LigojTextField
    :model-value="modelValue"
    :label="label"
    :hint="t(`${parameter.id}-description`)"
    :rules="rules"
    persistent-hint
    variant="outlined"
    density="compact"
    @update:model-value="(v) => emit('update:modelValue', v ?? '')"
  />
</template>

<script setup>
import { computed, watch } from 'vue'
import { useI18nStore, LigojTextField } from '@ligoj/host'
import { PARAM_TEMPLATE } from '../params.js'

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

const label = computed(() => `${t(props.parameter.id)}${props.parameter.mandatory ? ' *' : ''}`)
const rules = [(v) => (v != null && String(v).trim() !== '') || t('error.rule.NotBlank')]

/** Default of a new subscription: the template is named after the project key and applies to its keys `<key>:...`. */
function defaultValue(pkey) {
  return props.parameter.id === PARAM_TEMPLATE ? pkey : `${pkey}:.*`
}
watch(() => props.project?.pkey, (pkey) => {
  if (pkey && (props.modelValue == null || props.modelValue === '')) emit('update:modelValue', defaultValue(pkey))
}, { immediate: true })
</script>
