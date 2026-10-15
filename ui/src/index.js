// Load the sibling index.css at runtime. Vite's library build emits it as
// a separate file but does NOT add `import './index.css'` to the JS entry
// — so when the host dynamic-imports this bundle the stylesheet never
// loads and the scoped SFC styles do not apply. The link carries the
// `?v=<digest>` query of the JS bundle URL (app-ui plugins/loader.js), so
// the stylesheet rotates with the bundle and its `data-v-*` ids match.
if (typeof document !== 'undefined') {
  const id = 'ligoj-plugin-qa-sonarqube-css'
  if (!document.getElementById(id)) {
    const link = document.createElement('link')
    link.id = id
    link.rel = 'stylesheet'
    const cssUrl = new URL(/* @vite-ignore */ './index.css', import.meta.url)
    const selfQuery = new URL(import.meta.url).search
    if (selfQuery) cssUrl.search = selfQuery
    link.href = cssUrl.href
    document.head.appendChild(link)
  }
}

/*
 * Plugin "qa-sonarqube" — SonarQube implementation of plugin-qa.
 *
 * Tool-level plugin (`service:qa:sonarqube`). Augments the parent
 * `plugin-qa` via i18n parameter labels + row features (dashboard link,
 * project chip) merged in through plugin-qa's `subPluginIdFor` delegation.
 * The subscribe wizard calls `parameterField` directly for the project
 * autocomplete.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
  renderDetailsFeatures: service.renderDetailsFeatures,
  parameterField: service.parameterField,
  parameterLayout: service.parameterLayout,
  parameterSummary: service.parameterSummary,
}

export default {
  id: 'qa-sonarqube',
  label: 'SonarQube',
  requires: ['qa'],
  install() {
    const i18n = useI18nStore()
    i18n.merge(enMessages, 'en')
    i18n.merge(frMessages, 'fr')
  },
  feature(action, ...args) {
    const fn = features[action]
    if (!fn) throw new Error(`Plugin "qa-sonarqube" has no feature "${action}"`)
    return fn(...args)
  },
  service,
  meta: { icon: 'mdi-shield-check', color: 'blue-darken-1' },
}

export { service }
