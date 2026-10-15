/*
 * Service layer for plugin "qa-sonarqube".
 *
 * Tool-level plugin (`service:qa:sonarqube`). The parent `plugin-qa`
 * delegates the subscription-row hooks to us. Mirrors the legacy
 * `sonarqube.js`:
 *   - renderFeatures        → a link to the SonarQube project dashboard.
 *   - renderDetailsKey      → the project chip; for a permission template, the
 *     latest analyzed project chip.
 *   - renderDetailsFeatures → the live metric badges + branch list, read from
 *     `subscription.data.project` (populated by status/refresh). Each metric is
 *     styled by KIND: A–E *_rating grades get a graded colour, ncloc renders as
 *     a blue size circle (XS…XL), everything else a neutral compact label.
 *   - parameterField        → the subscribe-wizard project autocomplete
 *     (legacy `configureSubscriptionParameters`).
 *
 * TOOLTIPS ARE IMPLICIT. Every node (the dashboard button, the key chip, each
 * metric badge, each branch link) just sets a plain `title:` — including the
 * nested badges, whose multi-row "name / value / meaning" hint is a single
 * "\n"-joined string. The host's `PluginFeatures.promoteTitleToTooltip`
 * recurses the returned tree and upgrades every `title:` to a themed
 * <v-tooltip>, so this plugin imports no VTooltip and wraps no activators.
 *
 * Kept free of Vue SFC imports for unit testing — VNodes only.
 */
import { h } from 'vue'
import { VIcon, useI18nStore, renderServiceLink, renderDetailsChip, remoteSearchField } from '@ligoj/host'

import { PARAM_URL, PARAM_PROJECT, PARAM_TEMPLATE, PARAM_TEMPLATE_PATTERN, PARAM_ROLES } from './params.js'
import { rolesSummary } from './sonarJson.js'
import { analysisDay, dashboardUrl } from './dashboard.js'
import SonarTemplateField from './fields/SonarTemplateField.vue'
import SonarRolesField from './fields/SonarRolesField.vue'
import SonarOtherProjects from './components/SonarOtherProjects.vue'

// The project a row shows: the linked one, or for a permission template the latest analyzed of its projects
function shownProject(subscription) {
  return subscription?.parameters?.[PARAM_PROJECT] || subscription?.data?.project?.key || null
}

function renderFeatures(subscription) {
  const url = subscription?.parameters?.[PARAM_URL]
  const project = shownProject(subscription)
  if (!url || !project) return []
  const { t } = useI18nStore()
  return [renderServiceLink({
    icon: 'mdi-home',
    href: dashboardUrl(url, project),
    title: t('service:qa:sonarqube:project'),
  })]
}

function renderDetailsKey(subscription) {
  const { t } = useI18nStore()
  if (subscription?.parameters?.[PARAM_TEMPLATE]) {
    // A permission template: its latest analyzed project, shown as the linked project of a LINK subscription
    const project = subscription?.data?.project
    if (!project?.key) return null
    const title = [t('service:qa:sonarqube:latest-project'), project.name].filter(Boolean).join('\n')
    return renderDetailsChip({ icon: 'mdi-shield-check-outline', text: project.key, title })
  }
  const project = subscription?.parameters?.[PARAM_PROJECT]
  if (!project) return null
  return renderDetailsChip({ icon: 'mdi-shield-check-outline', text: project, title: t('service:qa:sonarqube:project') })
}

/* ---- metrics ------------------------------------------------------------ */

// Rating grade (1..5 = A..E) → colour. Legacy Bootstrap label classes were
// success / primary / warning / danger / danger — green / blue / amber / red.
const RATING_COLORS = ['#16a34a', '#2563eb', '#d9701a', '#dc2626', '#dc2626', '#dc2626']
const DEFAULT_COLOR = '#64748b' // legacy label-default (neutral)
const NCLOC_COLOR = '#297bae' // legacy .metric-ncloc circle

const LABEL_STYLE = 'display:inline-block;padding:1px 6px;border-radius:6px;font-size:10.5px;font-weight:700;line-height:1.6;color:#fff;white-space:nowrap;'
const NCLOC_STYLE = `display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;border-radius:50%;background:${NCLOC_COLOR};color:#fff;font-size:10px;font-weight:700;`

// vue-i18n echoes the key back when missing; treat that as "no translation".
function msg(t, key) {
  const v = t(key)
  return v === key ? '' : v
}

// The metric tooltip: name / value / meaning, one per line. Joined with "\n"
// so the host renders it as a multi-row v-tooltip; empty rows are dropped.
function metricTitle(t, name, value, meaning) {
  const label = msg(t, 'service:qa:sonarqube:value') || 'Value'
  return [name, `${label}: ${value}`, meaning].filter(Boolean).join('\n')
}

// Build one metric badge VNode for measure `m` = `value`. The badge text is the
// compact form; its `title:` carries the metric's name, full value and meaning.
function metricNode(m, value, t) {
  const short = m.endsWith('_rating') ? m.slice(0, -'_rating'.length) : null
  const name = msg(t, `service:qa:sonarqube:metric:${m}`)
    || (short ? msg(t, `service:qa:sonarqube:metric:${short}`) : '')
    || m
  const meaning = msg(t, `service:qa:sonarqube:metric:${m}:meaning`)
  const unit = msg(t, `service:qa:sonarqube:metric:${m}:unit`)

  if (short) {
    const grade = Math.floor(Number(value))
    const color = (grade >= 1 && RATING_COLORS[grade - 1]) || DEFAULT_COLOR
    const letter = grade >= 1 ? String.fromCharCode(64 + grade) : '?' // 1→A, 2→B…
    return h('span', { class: `sq-metric sq-rating sq-${m}`, style: `${LABEL_STYLE}background:${color};`, title: metricTitle(t, name, letter, meaning) }, letter)
  }

  if (m.endsWith('ncloc')) {
    const n = Number(value)
    const size = n < 1000 ? 'XS' : n < 10000 ? 'S' : n < 100000 ? 'M' : n < 500000 ? 'L' : 'XL'
    return h('span', { class: 'sq-metric sq-ncloc', style: NCLOC_STYLE, title: metricTitle(t, name, value, meaning) }, size)
  }

  // Neutral numeric metric: badge is compacted (K/M/G); the tooltip keeps the
  // full raw value + the metric's own unit.
  const n = Number(value)
  let display = value
  let badgeUnit = unit
  if (n > 1e9) { display = Math.round(n / 1e9); badgeUnit = 'G' }
  else if (n > 1e6) { display = Math.round(n / 1e6); badgeUnit = 'M' }
  else if (n > 1e3) { display = Math.round(n / 1e3); badgeUnit = 'K' }
  return h('span', { class: `sq-metric sq-${m}`, style: `${LABEL_STYLE}background:${DEFAULT_COLOR};`, title: metricTitle(t, name, `${value}${unit || ''}`, meaning) }, `${display}${badgeUnit || ''}`)
}

// Render every measure of `source` (a project OR a branch — both carry
// `measuresAsMap`). `_rating` metrics sort first (legacy `_`-prefix trick).
function renderMetrics(source, t) {
  const measures = source?.measuresAsMap || {}
  const keys = Object.keys(measures).sort((a, b) => {
    const ka = a.endsWith('_rating') ? `_${a}` : a
    const kb = b.endsWith('_rating') ? `_${b}` : b
    return ka < kb ? -1 : ka > kb ? 1 : 0
  })
  if (!keys.length) return null
  return h(
    'span',
    { class: 'sq-metrics', style: 'display:inline-flex;flex-wrap:wrap;gap:4px;align-items:center;' },
    keys.map((m) => metricNode(m, measures[m], t)),
  )
}

// One non-main branch / pull-request: a dashboard link + its own metrics.
function renderBranch(b, url, projectKey, t) {
  if (!url || !projectKey) return null
  const isPr = !!b.pullRequestKey
  const param = isPr ? `pullRequest=${encodeURIComponent(b.pullRequestKey)}` : `branch=${encodeURIComponent(b.name)}`
  const href = `${dashboardUrl(url, projectKey)}&${param}`
  const ok = b?.status?.qualityGateStatus === 'OK'
  const lines = [b.type, `${msg(t, 'common.name') || 'Name'}: ${b.name}`]
  if (isPr) {
    lines.push(`${msg(t, 'service:qa:sonarqube:pull-request') || 'Pull Request'} #${b.pullRequestKey}`)
    if (b.targetBranchName) lines.push(`→ ${b.targetBranchName}`)
  }
  lines.push(`${ok ? '✓' : '✗'} ${b.analysisDate || ''}`.trim())
  const link = h('a', {
    href,
    target: '_blank',
    rel: 'noopener noreferrer',
    class: 'sq-branch-link',
    style: 'display:inline-flex;align-items:center;gap:3px;max-width:130px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:600;color:inherit;text-decoration:none;',
    title: lines.filter(Boolean).join('\n'),
  }, [h(VIcon, { size: 'x-small' }, () => (isPr ? 'mdi-pound' : 'mdi-source-branch')), b.pullRequestKey || b.name])
  const metrics = renderMetrics(b, t)
  return h('div', { class: 'sq-branch', style: 'display:flex;align-items:center;gap:6px;' }, metrics ? [link, metrics] : [link])
}

// The other projects of a permission template: a chip, its tooltip listing them with their last analysis, opening the
// table of all its projects
function renderOtherProjects(subscription, shown, t) {
  const data = subscription.data
  const others = (data.projects || []).filter((p) => p.key !== shown)
  const count = (data.projectsCount ?? others.length) - (shown ? 1 : 0)
  if (count <= 0) return null
  const lines = others.map((p) => `${p.key} — ${analysisDay(p.lastAnalysisDate) || t('service:qa:sonarqube:never-analyzed')}`)
  if (count > others.length) lines.push(t('service:qa:sonarqube:more-projects', { count: count - others.length }))
  const projects = data.projects || []
  return h(SonarOtherProjects, {
    subscription,
    projects,
    latest: data.project,
    more: (data.projectsCount ?? projects.length) - projects.length,
    pattern: data.template?.pattern || subscription.parameters?.[PARAM_TEMPLATE_PATTERN] || '',
    text: t('service:qa:sonarqube:other-projects', { count }, count),
    tooltip: lines.join('\n'),
  })
}

// Live SonarQube details: project metrics + the non-main branch/PR list, and for a permission template the other
// projects. Reads `subscription.data` (populated by status/refresh); returns null until it arrives, so the row degrades
// cleanly to the key chip alone.
function renderDetailsFeatures(subscription) {
  const data = subscription?.data
  const project = data?.project
  const template = !!subscription?.parameters?.[PARAM_TEMPLATE]
  if (!project && !(template && data?.projects)) return null
  const { t } = useI18nStore()
  const out = []
  if (project) {
    const metrics = renderMetrics(project, t)
    if (metrics) out.push(metrics)
    const url = subscription?.parameters?.[PARAM_URL]
    const projectKey = shownProject(subscription)
    const branches = (project.branches || [])
      .filter((b) => !b.isMain)
      .map((b) => renderBranch(b, url, projectKey, t))
      .filter(Boolean)
    if (branches.length) {
      out.push(h('div', { class: 'sq-branches', style: 'display:flex;flex-direction:column;gap:2px;margin-top:2px;width:100%;' }, branches))
    }
  } else {
    out.push(h('span', { class: 'sq-none', style: 'font-size:12px;opacity:.7;' }, t('service:qa:sonarqube:no-analyzed-project')))
  }
  if (template) {
    const others = renderOtherProjects(subscription, project?.key, t)
    if (others) out.push(others)
  }
  return out.length ? out : null
}

// Subscribe-wizard input: the projects of the selected instance, searched as the user types. The key is stored
// and shown: the measures and the dashboard link read a project by key.
const ProjectField = remoteSearchField({
  path: 'rest/service/qa/sonarqube',
  toValue: (p) => p.key ?? p.id,
  toSubtitle: (p) => p.key,
  placeholder: 'service:qa:sonarqube:project-search',
})

/**
 * Subscribe-wizard hooks: the project is picked among the projects of the selected SonarQube node; in CREATE mode, the
 * permission template name and pattern are prefilled from the project key, and the role mapping is a checked JSON.
 * Null when editing a node and for the other parameters: the default type-based input is rendered.
 */
function parameterField({ parameter, isNode } = {}) {
  if (isNode) return null
  const id = parameter?.id
  if (id === PARAM_PROJECT) return ProjectField
  if (id === PARAM_TEMPLATE || id === PARAM_TEMPLATE_PATTERN) return SonarTemplateField
  if (id === PARAM_ROLES) return SonarRolesField
  return null
}

/**
 * Subscribe-wizard order of the CREATE mode: the template, its pattern, then the role mapping. The default order
 * elsewhere.
 */
function parameterLayout({ mode, isNode } = {}) {
  if (isNode || String(mode).toLowerCase() !== 'create') return []
  return [{ parameters: [PARAM_TEMPLATE, PARAM_TEMPLATE_PATTERN, PARAM_ROLES] }]
}

/**
 * Subscription status tooltip: the role mapping as its groups instead of the raw JSON. Null for the other parameters.
 */
function parameterSummary({ parameter, value } = {}) {
  return parameter === PARAM_ROLES ? rolesSummary(value, useI18nStore().t) : null
}

export default { renderFeatures, renderDetailsKey, renderDetailsFeatures, parameterField, parameterLayout, parameterSummary }
