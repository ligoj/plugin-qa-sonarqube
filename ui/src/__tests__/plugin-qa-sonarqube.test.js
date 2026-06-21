import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-qa-sonarqube contract', () => {
  it('exposes a valid tool-level manifest', () => {
    expect(def.id).toBe('qa-sonarqube')
    expect(def.requires).toEqual(['qa'])
    expect(def.routes).toBeUndefined()
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })
  it('merges i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:qa:sonarqube:project')).toBe('Project')
  })
  it('throws for an unknown feature', () => {
    expect(() => def.feature('nope')).toThrow(/no feature "nope"/)
  })
  it('renderFeatures returns the dashboard link when url + project are set', () => {
    def.install()
    const vnodes = def.feature('renderFeatures', {
      parameters: { 'service:qa:sonarqube:url': 'https://sonar.example.org', 'service:qa:sonarqube:project': 'org.ligoj:app' },
    })
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].props.href).toBe('https://sonar.example.org/dashboard/index/org.ligoj%3Aapp')
    expect(vnodes[0].props.target).toBe('_blank')
  })
  it('renderFeatures returns [] without url or project', () => {
    def.install()
    expect(def.feature('renderFeatures', { parameters: { 'service:qa:sonarqube:url': 'x' } })).toEqual([])
    expect(def.feature('renderFeatures', {})).toEqual([])
  })
  it('renderDetailsKey returns the project chip when present', () => {
    def.install()
    expect(def.feature('renderDetailsKey', { parameters: { 'service:qa:sonarqube:project': 'p' } })).toBeTruthy()
    expect(def.feature('renderDetailsKey', { parameters: {} })).toBeNull()
  })
  it('renderDetailsFeatures returns null until data.project arrives', () => {
    def.install()
    expect(def.feature('renderDetailsFeatures', { parameters: {} })).toBeNull()
  })
  // Every badge is a <v-tooltip> (project convention — no native `title`).
  // Decode it: call the activator slot for the styled span, the default slot
  // for the tooltip rows.
  const decode = (tipVNode) => {
    const span = tipVNode.children.activator({ props: {} })
    const lines = (tipVNode.children.default() || []).map((d) => d.children)
    return { class: String(span.props.class), text: span.children, style: String(span.props.style), lines }
  }
  it('renderDetailsFeatures styles each metric by kind', () => {
    def.install()
    const out = def.feature('renderDetailsFeatures', {
      parameters: { 'service:qa:sonarqube:url': 'https://s.org', 'service:qa:sonarqube:project': 'p' },
      data: { project: { measuresAsMap: { sqale_rating: 1, ncloc: 250000, coverage: 85 } } },
    })
    const badges = out[0].children.map(decode) // the .sq-metrics span's metric children
    const byClass = (frag) => badges.find((b) => b.class.includes(frag))
    // A-grade rating → letter "A" on the green (success) colour.
    expect(byClass('sq-sqale_rating').text).toBe('A')
    expect(byClass('sq-sqale_rating').style).toContain('#16a34a')
    // ncloc → size bucket (250k → L) as a blue circle.
    expect(byClass('sq-ncloc').text).toBe('L')
    expect(byClass('sq-ncloc').style).toContain('#297bae')
    // neutral numeric → value + unit on the default colour.
    expect(byClass('sq-coverage').text).toBe('85%')
  })
  it('every metric tooltip is a v-tooltip showing name, value and meaning', () => {
    def.install()
    const out = def.feature('renderDetailsFeatures', {
      parameters: { 'service:qa:sonarqube:url': 'https://s.org', 'service:qa:sonarqube:project': 'p' },
      data: { project: { measuresAsMap: { sqale_rating: 1, ncloc: 250000, coverage: 85 } } },
    })
    const badges = out[0].children
    // Tooltips render via <v-tooltip>, not the native `title` attribute.
    expect(badges.every((tv) => tv.type?.name === 'VTooltip')).toBe(true)
    const byClass = (frag) => badges.map(decode).find((b) => b.class.includes(frag))
    // rating: name "Maintainability", value "A" (not the raw grade), meaning.
    expect(byClass('sq-sqale_rating').lines).toEqual(['Maintainability', 'Value: A', 'A-to-E rating based on the technical debt ratio'])
    // ncloc: tooltip keeps the FULL count, badge shows the bucket.
    expect(byClass('sq-ncloc').lines).toEqual(['Lines of code', 'Value: 250000', 'Non-commenting lines of code'])
    // coverage: value + unit + meaning.
    expect(byClass('sq-coverage').lines).toEqual(['Coverage', 'Value: 85%', 'Percentage of code covered by tests'])
  })
  it('renderDetailsFeatures lists non-main branches with a dashboard link', () => {
    def.install()
    const out = def.feature('renderDetailsFeatures', {
      parameters: { 'service:qa:sonarqube:url': 'https://s.org/', 'service:qa:sonarqube:project': 'p' },
      data: { project: { measuresAsMap: {}, branches: [
        { name: 'main', isMain: true },
        { name: 'feat/x', type: 'BRANCH', status: { qualityGateStatus: 'OK' }, measuresAsMap: {} },
      ] } },
    })
    const branchesDiv = out.find((n) => String(n.props.class).includes('sq-branches'))
    expect(branchesDiv.children).toHaveLength(1) // only the non-main branch
    const linkTip = branchesDiv.children[0].children[0] // <v-tooltip> wrapping the <a>
    expect(linkTip.type?.name).toBe('VTooltip')
    const link = linkTip.children.activator({ props: {} })
    expect(link.props.href).toBe('https://s.org/dashboard?id=p&branch=feat%2Fx')
  })
})
