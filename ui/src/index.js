/*
 * Plugin "qa-sonarqube" — SonarQube implementation of plugin-qa.
 *
 * Tool-level plugin (`service:qa:sonarqube`). Augments the parent
 * `plugin-qa` via i18n parameter labels + row features (dashboard link,
 * project chip) merged in through plugin-qa's `subPluginIdFor` delegation.
 */
import { useI18nStore } from '@ligoj/host'
import enMessages from './i18n/en.js'
import frMessages from './i18n/fr.js'
import service from './service.js'

const features = {
  renderFeatures: service.renderFeatures,
  renderDetailsKey: service.renderDetailsKey,
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
