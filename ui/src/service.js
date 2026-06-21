/*
 * Service layer for plugin "qa-sonarqube".
 *
 * Tool-level plugin (`service:qa:sonarqube`). The parent `plugin-qa`
 * delegates the subscription-row hooks to us. Mirrors the legacy
 * `sonarqube.js`:
 *   - renderFeatures        → a link to the SonarQube project dashboard.
 *   - renderDetailsKey      → the project chip.
 *   - renderDetailsFeatures → the live metric badges + branch list, read from
 *     `subscription.data.project` (populated by status/refresh). Each metric is
 *     styled by KIND: A–E *_rating grades get a graded colour, ncloc renders as
 *     a blue size circle (XS…XL), everything else a neutral compact label.
 *
 * Kept free of Vue SFC imports for unit testing — VNodes only.
 */
import { h } from 'vue'
import { VBtn, VChip, VIcon, VTooltip, useI18nStore } from '@ligoj/host'

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

// PROJECT CONVENTION: tooltips ALWAYS render through Vuetify <v-tooltip>, never
// the native `title` box. `PluginFeatures.promoteTitleToTooltip` only upgrades a
// `title:` on a TOP-LEVEL delegation VNode and does not recurse, so these nested
// badges/links wrap their own activator in v-tooltip. Each `lines` entry is its
// own row, so the name / value / meaning tooltip reads cleanly.
function tip(lines, activator) {
  const rows = lines.filter(Boolean)
  if (!rows.length) return activator({})
  return h(VTooltip, { location: 'top' }, {
    activator: ({ props }) => activator(props),
    default: () => rows.map((line, i) => h('div', { key: i }, line)),
  })
}

// The 3 tooltip rows for a metric: name / value / meaning.
function metricLines(t, name, value, meaning) {
  const label = msg(t, 'service:qa:sonarqube:value') || 'Value'
  return [name, `${label}: ${value}`, meaning]
}

// Build one metric badge VNode for measure `m` = `value`. The badge text is the
// compact form; the v-tooltip carries the metric's name, full value and meaning.
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
    return tip(metricLines(t, name, letter, meaning),
      (p) => h('span', { ...p, class: `sq-metric sq-rating sq-${m}`, style: `${LABEL_STYLE}background:${color};` }, letter))
  }

  if (m.endsWith('ncloc')) {
    const n = Number(value)
    const size = n < 1000 ? 'XS' : n < 10000 ? 'S' : n < 100000 ? 'M' : n < 500000 ? 'L' : 'XL'
    return tip(metricLines(t, name, value, meaning),
      (p) => h('span', { ...p, class: 'sq-metric sq-ncloc', style: NCLOC_STYLE }, size))
  }

  // Neutral numeric metric: badge is compacted (K/M/G); the tooltip keeps the
  // full raw value + the metric's own unit.
  const n = Number(value)
  let display = value
  let badgeUnit = unit
  if (n > 1e9) { display = Math.round(n / 1e9); badgeUnit = 'G' }
  else if (n > 1e6) { display = Math.round(n / 1e6); badgeUnit = 'M' }
  else if (n > 1e3) { display = Math.round(n / 1e3); badgeUnit = 'K' }
  return tip(metricLines(t, name, `${value}${unit || ''}`, meaning),
    (p) => h('span', { ...p, class: `sq-metric sq-${m}`, style: `${LABEL_STYLE}background:${DEFAULT_COLOR};` }, `${display}${badgeUnit || ''}`))
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
  const href = `${url.replace(/\/$/, '')}/dashboard?id=${encodeURIComponent(projectKey)}&${param}`
  const ok = b?.status?.qualityGateStatus === 'OK'
  const lines = [b.type, `${msg(t, 'common.name') || 'Name'}: ${b.name}`]
  if (isPr) {
    lines.push(`${msg(t, 'service:qa:sonarqube:pull-request') || 'Pull Request'} #${b.pullRequestKey}`)
    if (b.targetBranchName) lines.push(`→ ${b.targetBranchName}`)
  }
  lines.push(`${ok ? '✓' : '✗'} ${b.analysisDate || ''}`.trim())
  const link = tip(lines, (p) => h('a', {
    ...p,
    href,
    target: '_blank',
    rel: 'noopener noreferrer',
    class: 'sq-branch-link',
    style: 'display:inline-flex;align-items:center;gap:3px;max-width:130px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:11px;font-weight:600;color:inherit;text-decoration:none;',
  }, [h(VIcon, { size: 'x-small' }, () => (isPr ? 'mdi-pound' : 'mdi-source-branch')), b.pullRequestKey || b.name]))
  const metrics = renderMetrics(b, t)
  return h('div', { class: 'sq-branch', style: 'display:flex;align-items:center;gap:6px;' }, metrics ? [link, metrics] : [link])
}

// Live SonarQube details: project metrics + the non-main branch/PR list.
// Reads `subscription.data.project` (populated by status/refresh); returns null
// until that arrives, so the row degrades cleanly to the key chip alone.
function renderDetailsFeatures(subscription) {
  const project = subscription?.data?.project
  if (!project) return null
  const { t } = useI18nStore()
  const out = []
  const metrics = renderMetrics(project, t)
  if (metrics) out.push(metrics)
  const url = subscription?.parameters?.[PARAM_URL]
  const projectKey = subscription?.parameters?.[PARAM_PROJECT]
  const branches = (project.branches || [])
    .filter((b) => !b.isMain)
    .map((b) => renderBranch(b, url, projectKey, t))
    .filter(Boolean)
  if (branches.length) {
    out.push(h('div', { class: 'sq-branches', style: 'display:flex;flex-direction:column;gap:2px;margin-top:2px;width:100%;' }, branches))
  }
  return out.length ? out : null
}

export default { renderFeatures, renderDetailsKey, renderDetailsFeatures }
