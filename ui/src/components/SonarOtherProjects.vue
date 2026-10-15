<template>
  <!-- The other projects of a permission template: a chip opening the table of all its projects, each with its name, its
       key, its metrics read when the table opens and shown as for a linked project, and its dashboard link. -->
  <span class="sq-others">
    <!-- Its own tooltip, closed by the click: one promoted by the host would stay over the open list -->
    <VTooltip :model-value="tip" location="bottom" @update:model-value="(v) => (tip = v && !open)">
      <template #activator="{ props: tipProps }">
        <VChip v-bind="tipProps" class="sq-others-chip mr-1" size="small" variant="tonal" link @click.stop="show">
          <VIcon start size="small">mdi-folder-multiple-outline</VIcon>
          {{ text }}
        </VChip>
      </template>
      <div v-for="(line, i) in tooltipLines" :key="i">{{ line }}</div>
    </VTooltip>
    <LjDialog v-model="open" :title="dialogTitle" icon="mdi-folder-multiple-outline" :max-width="900">
      <p class="sq-pattern">{{ t('service:qa:sonarqube:template-pattern') }}: <code>{{ patternText }}</code></p>
      <table class="sq-table">
        <thead>
          <tr>
            <th>{{ t('common.name') }}</th>
            <th>{{ t('service:qa:sonarqube:key') }}</th>
            <th>{{ t('service:qa:sonarqube:measures') }}</th>
            <th />
          </tr>
        </thead>
        <tbody>
          <tr v-for="p in projects" :key="p.key">
            <td class="sq-name">{{ p.name || p.key }}</td>
            <td><code class="sq-key">{{ p.key }}</code></td>
            <td>
              <span v-if="!p.lastAnalysisDate" class="sq-muted">{{ t('service:qa:sonarqube:never-analyzed') }}</span>
              <v-progress-circular v-else-if="!(p.key in details)" size="14" width="2" indeterminate />
              <PluginFeatures v-else-if="details[p.key]" :subscription="linked(p.key)" action="renderDetailsFeatures" />
              <span v-else class="sq-muted">{{ t('service:qa:sonarqube:project-details-error') }}</span>
            </td>
            <td class="sq-link"><PluginFeatures :subscription="linked(p.key)" action="renderFeatures" /></td>
          </tr>
        </tbody>
      </table>
      <p v-if="more > 0" class="sq-more">{{ t('service:qa:sonarqube:more-projects', { count: more }) }}</p>
      <template #footer>
        <LjButton variant="ghost" @click="open = false">{{ t('common.close') }}</LjButton>
      </template>
    </LjDialog>
  </span>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useApi, useI18nStore, LjButton, LjDialog, PluginFeatures, VChip, VIcon, VTooltip } from '@ligoj/host'
import { PARAM_PROJECT, PARAM_TEMPLATE, PARAM_TEMPLATE_PATTERN, PARAM_URL } from '../params.js'

const props = defineProps({
  // The permission template subscription
  subscription: { type: Object, required: true },
  // Its matching projects: `{ key, name, lastAnalysisDate }`
  projects: { type: Array, default: () => [] },
  // The latest analyzed project of the row, its metrics already read
  latest: { type: Object, default: null },
  // The matching projects beyond the listed ones
  more: { type: Number, default: 0 },
  pattern: { type: String, default: '' },
  text: { type: String, default: '' },
  // The chip tooltip, one row per line
  tooltip: { type: String, default: '' },
})

const api = useApi()
const { t } = useI18nStore()
const open = ref(false)
const tip = ref(false)
const tooltipLines = computed(() => (props.tooltip ? props.tooltip.split('\n') : []))
// Details by project key, for the metrics: a project when read, null when it could not be, absent while loading
const details = reactive({})
const loading = new Set()
watch(() => props.latest, (project) => { if (project?.key) details[project.key] = project }, { immediate: true })

const url = computed(() => props.subscription?.parameters?.[PARAM_URL] || '')
const patternText = computed(() => props.pattern || props.subscription?.parameters?.[PARAM_TEMPLATE_PATTERN] || '')
const dialogTitle = computed(() => t('service:qa:sonarqube:template-projects', { template: props.subscription?.parameters?.[PARAM_TEMPLATE] || '' }))

function show() {
  tip.value = false
  open.value = true
}

// A project shown as the linked project of a LINK subscription, so the row renderer draws its dashboard link and its
// metrics: its branches are left out of the table
function linked(key) {
  return {
    id: props.subscription.id,
    node: props.subscription.node,
    parameters: { [PARAM_URL]: url.value, [PARAM_PROJECT]: key },
    data: { project: details[key] ? { ...details[key], branches: [] } : details[key] },
  }
}

// The details of the analyzed projects, read once; a failed read is retried at the next opening
function load() {
  for (const p of props.projects) {
    if (!p.lastAnalysisDate || details[p.key] || loading.has(p.key)) continue
    delete details[p.key]
    loading.add(p.key)
    api.get(`rest/service/qa/sonarqube/${props.subscription.id}/project/${encodeURIComponent(p.key)}`, { silent: true })
      .then((project) => { details[p.key] = project || null })
      .catch(() => { details[p.key] = null })
      .finally(() => loading.delete(p.key))
  }
}
watch(open, (value) => { if (value) load() })
</script>

<style scoped>
.sq-pattern { margin: 0 0 12px; font-size: 13px; color: var(--ink-3); }
.sq-table { width: 100%; border-collapse: collapse; font-size: 13px; }
.sq-table th, .sq-table td { padding: 6px 8px; border-bottom: 1px solid rgba(var(--v-theme-on-surface), .08); text-align: left; vertical-align: middle; }
.sq-table th { font-size: 11px; text-transform: uppercase; letter-spacing: .05em; color: rgba(var(--v-theme-on-surface), .55); }
.sq-name { font-weight: 600; }
.sq-key { font-size: 12px; }
.sq-link { width: 1%; white-space: nowrap; text-align: right; }
.sq-muted, .sq-more { font-size: 12px; color: var(--ink-3); }
.sq-more { margin: 8px 0 0; }
</style>
