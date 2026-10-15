/*
 * CREATE mode of SonarQube: the permission template fields of the subscription form, prefilled from the project key,
 * the role mapping checks, the layout, and the role mapping summary of the status tooltip.
 */
import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount } from '@vue/test-utils'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'
import SonarOtherProjects from '../components/SonarOtherProjects.vue'
import { rolesError, rolesSample } from '../sonarJson.js'

const TEMPLATE = { id: 'service:qa:sonarqube:template', type: 'TEXT', mandatory: true }
const PATTERN = { id: 'service:qa:sonarqube:template-pattern', type: 'TEXT', mandatory: true }
const ROLES = { id: 'service:qa:sonarqube:roles', type: 'TEXT', mandatory: false }
const PROJECT = { id: 1, name: 'Demo', pkey: 'demo-1' }
const stubs = {
  LigojTextField: { name: 'LigojTextField', props: ['modelValue', 'label'], template: '<input />' },
  LigojTextarea: { name: 'LigojTextarea', props: ['modelValue', 'label', 'placeholder'], template: '<textarea />' },
}

beforeEach(() => {
  setActivePinia(createPinia())
  def.install()
  useI18nStore().setLocale('en')
})

describe('SonarQube CREATE mode form', () => {
  it('orders the template fields in CREATE mode only', () => {
    expect(def.feature('parameterLayout', { mode: 'create', isNode: false })).toEqual([{ parameters: [TEMPLATE.id, PATTERN.id, ROLES.id] }])
    expect(def.feature('parameterLayout', { mode: 'link', isNode: false })).toEqual([])
    expect(def.feature('parameterLayout', { mode: 'create', isNode: true })).toEqual([])
  })

  it('gives a field to each template parameter, the default input to edit a node', () => {
    const field = def.feature('parameterField', { parameter: TEMPLATE, mode: 'create', isNode: false })
    expect(field).toBeTruthy()
    expect(def.feature('parameterField', { parameter: PATTERN, mode: 'create', isNode: false })).toBe(field)
    expect(def.feature('parameterField', { parameter: ROLES, mode: 'create', isNode: false })).toBeTruthy()
    expect(def.feature('parameterField', { parameter: TEMPLATE, mode: 'create', isNode: true })).toBeNull()
  })

  it('prefills the template name and pattern from the project key', () => {
    const Field = def.feature('parameterField', { parameter: TEMPLATE, mode: 'create', isNode: false })
    const name = mount(Field, { props: { parameter: TEMPLATE, mode: 'create', project: PROJECT, modelValue: '' }, global: { stubs } })
    expect(name.emitted('update:modelValue')[0]).toEqual(['demo-1'])
    const pattern = mount(Field, { props: { parameter: PATTERN, mode: 'create', project: PROJECT, modelValue: null }, global: { stubs } })
    expect(pattern.emitted('update:modelValue')[0]).toEqual(['demo-1:.*'])
    // A typed value is kept
    const typed = mount(Field, { props: { parameter: TEMPLATE, mode: 'create', project: PROJECT, modelValue: 'team' }, global: { stubs } })
    expect(typed.emitted('update:modelValue')).toBeUndefined()
  })

  it('suggests a role mapping built from the project key', () => {
    const Field = def.feature('parameterField', { parameter: ROLES, mode: 'create', isNode: false })
    const w = mount(Field, { props: { parameter: ROLES, mode: 'create', project: PROJECT, modelValue: '' }, global: { stubs } })
    expect(w.findComponent({ name: 'LigojTextarea' }).props('placeholder')).toBe(rolesSample('demo-1'))
    expect(rolesSample('demo-1')).toContain('"demo-1-dev": ["user", "codeviewer", "scan"]')
  })

  it('checks the role mapping as the backend does', () => {
    expect(rolesError('')).toBeNull()
    expect(rolesError(rolesSample('demo-1'))).toBeNull()
    expect(rolesError('{oops')).toBe('error.rule.sonar-roles-json')
    expect(rolesError('["admin"]')).toBe('error.rule.sonar-roles-json')
    expect(rolesError('{"dev": "user"}')).toBe('error.rule.sonar-roles-json')
    expect(rolesError('{"dev": []}')).toBe('error.rule.sonar-roles-json')
    expect(rolesError('{" ": ["user"]}')).toBe('error.rule.sonar-roles-json')
    expect(rolesError('{"dev": ["write"]}')).toBe('error.rule.sonar-roles-permission')
  })

  it('summarizes the role mapping in the status tooltip', () => {
    const summary = (value) => def.feature('parameterSummary', { parameter: ROLES.id, value })
    expect(summary(rolesSample('demo-1'))).toBe('2 groups: demo-1-admin, demo-1-dev')
    expect(summary('{"a": ["user"]}')).toBe('1 group: a')
    expect(summary(JSON.stringify({ a: ['user'], b: ['user'], c: ['user'], d: ['user'] }))).toBe('4 groups: a, b, c +1')
    expect(summary('{}')).toBe('No group')
    expect(summary('{oops')).toBeNull()
    expect(def.feature('parameterSummary', { parameter: TEMPLATE.id, value: 'demo-1' })).toBeNull()
    useI18nStore().setLocale('fr')
    expect(summary('{"a": ["user"], "b": ["user"]}')).toBe('2 groupes : a, b')
  })

  it('labels the CREATE mode in English and French', async () => {
    const en = (await import('../i18n/en.js')).default
    const fr = (await import('../i18n/fr.js')).default
    for (const key of ['service:qa:sonarqube:template', 'service:qa:sonarqube:template-description', 'service:qa:sonarqube:template-pattern',
      'service:qa:sonarqube:template-pattern-description', 'service:qa:sonarqube:roles', 'service:qa:sonarqube:roles-description',
      'error.rule.sonar-roles-json', 'error.rule.sonar-roles-permission', 'error.rule.sonar-template-pattern', 'error.rule.sonar-template-exists',
      'error.rule.sonar-template', 'error.rule.sonar-admin', 'error.rule.sonar-error', 'warning.sonar-template-reused', 'warning.sonar-template-missing']) {
      expect(en[key], key).toBeTruthy()
      expect(fr[key], key).toBeTruthy()
      expect(fr[key], key).not.toBe(en[key])
    }
  })
})

const URL = 'https://sonar.example.org'
const TEMPLATE_SUB = {
  parameters: { 'service:qa:sonarqube:url': URL, 'service:qa:sonarqube:template': 'demo-1', 'service:qa:sonarqube:template-pattern': 'demo-1:.*' },
  data: {
    template: { name: 'demo-1', pattern: 'demo-1:.*' },
    project: { key: 'demo-1:api', name: 'API', measuresAsMap: { ncloc: 1200 }, branches: [] },
    projects: [
      { key: 'demo-1:api', name: 'API', lastAnalysisDate: '2026-10-10T08:00:00+0200' },
      { key: 'demo-1:web', name: 'Web', lastAnalysisDate: '2026-10-01T08:00:00+0000' },
      { key: 'demo-1:new', name: 'New' },
    ],
    projectsCount: 25,
  },
}

describe('SonarQube template-based subscription row', () => {
  it('links the latest analyzed project', () => {
    const vnodes = def.feature('renderFeatures', TEMPLATE_SUB)
    expect(vnodes).toHaveLength(1)
    expect(vnodes[0].props.href).toBe(`${URL}/dashboard?id=demo-1%3Aapi`)
  })

  it('shows the latest analyzed project as a linked one, without the template', () => {
    const project = def.feature('renderDetailsKey', TEMPLATE_SUB)
    // renderDetailsChip children: [icon, ' ', text]
    expect(project.children.default()[2]).toBe('demo-1:api')
    expect(project.props.title.split('\n')).toEqual(['Latest analyzed project', 'API'])
  })

  it('shows no key until a project is analyzed', () => {
    expect(def.feature('renderDetailsKey', { parameters: TEMPLATE_SUB.parameters })).toBeNull()
    expect(def.feature('renderDetailsKey', { parameters: TEMPLATE_SUB.parameters, data: { ...TEMPLATE_SUB.data, project: undefined } })).toBeNull()
  })

  it('lists the other projects with their last analysis', () => {
    const out = def.feature('renderDetailsFeatures', TEMPLATE_SUB)
    const others = out[out.length - 1]
    expect(others.type).toBe(SonarOtherProjects)
    expect(others.props.text).toBe('+24 projects')
    expect(others.props.tooltip.split('\n')).toEqual(['demo-1:web — 2026-10-01', 'demo-1:new — never analyzed', 'and 22 more'])
    // The dialog lists every project, the latest analyzed one with its known metrics
    expect(others.props.projects.map((p) => p.key)).toEqual(['demo-1:api', 'demo-1:web', 'demo-1:new'])
    expect(others.props.latest).toBe(TEMPLATE_SUB.data.project)
    expect(others.props.more).toBe(22)
  })

  it('tells no project was analyzed yet', () => {
    const sub = { ...TEMPLATE_SUB, data: { ...TEMPLATE_SUB.data, project: undefined, projects: [{ key: 'demo-1:new' }], projectsCount: 1 } }
    expect(def.feature('renderFeatures', sub)).toEqual([])
    const out = def.feature('renderDetailsFeatures', sub)
    expect(out[0].children).toBe('No analyzed project yet')
    expect(out).toHaveLength(2)
  })

  it('shows nothing until the status data arrives', () => {
    expect(def.feature('renderDetailsFeatures', { parameters: TEMPLATE_SUB.parameters })).toBeNull()
    expect(def.feature('renderFeatures', { parameters: TEMPLATE_SUB.parameters })).toEqual([])
  })
})
