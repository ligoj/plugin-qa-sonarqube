/*
 * Service layer for plugin "qa-sonarqube".
 *
 * Tool-level plugin (`service:qa:sonarqube`). The parent `plugin-qa`
 * delegates the subscription-row hooks to us. Mirrors `sonarqube.js`:
 *   - renderFeatures   → a link to the SonarQube project dashboard
 *     (url/dashboard/index/<project>, the legacy URL shape).
 *   - renderDetailsKey → the project chip.
 *
 * The legacy `renderDetailsFeatures` (metric badges, branch list) reads
 * live `subscription.data.project` and is deferred. Kept free of Vue SFC
 * imports for unit testing.
 */
import { h } from 'vue'
import { VBtn, VChip, VIcon, useI18nStore } from '@ligoj/host'

const PARAM_URL = 'service:qa:sonarqube:url'
const PARAM_PROJECT = 'service:qa:sonarqube:project'

function renderFeatures(subscription) {
  const params = subscription?.parameters
  const url = params?.[PARAM_URL]
  const project = params?.[PARAM_PROJECT]
  if (!url || !project) return []
  const { t } = useI18nStore()
  return [
    h(
      VBtn,
      {
        icon: true,
        size: 'small',
        variant: 'text',
        href: `${url.replace(/\/$/, '')}/dashboard/index/${encodeURIComponent(project)}`,
        target: '_blank',
        rel: 'noopener noreferrer',
        title: t('service:qa:sonarqube:project'),
      },
      () => h(VIcon, { size: 'small' }, () => 'mdi-home'),
    ),
  ]
}

function renderDetailsKey(subscription) {
  const project = subscription?.parameters?.[PARAM_PROJECT]
  if (!project) return null
  const { t } = useI18nStore()
  return h(
    VChip,
    { size: 'small', variant: 'tonal', class: 'mr-1', title: t('service:qa:sonarqube:project') },
    () => [h(VIcon, { start: true, size: 'small' }, () => 'mdi-shield-check-outline'), ' ', String(project)],
  )
}

export default { renderFeatures, renderDetailsKey }
