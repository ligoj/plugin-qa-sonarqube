/*
 * The projects of a permission template subscription: the "+N projects" chip opens a dialog listing all of them as a
 * table, each with its name, its key, its metrics read on demand and shown as for a linked project, and its dashboard.
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'
import SonarOtherProjects from '../components/SonarOtherProjects.vue'

function response(body, status = 200) {
  return { ok: status < 300, status, headers: { get: () => 'application/json' }, clone() { return response(body, status) }, json: async () => body, text: async () => JSON.stringify(body) }
}

const URL = 'https://sonar.example.org'
const SUB = {
  id: 3604,
  node: { id: 'service:qa:sonarqube:local' },
  parameters: { 'service:qa:sonarqube:url': URL, 'service:qa:sonarqube:template': 'demo-1', 'service:qa:sonarqube:template-pattern': 'demo-1:.*' },
}
const PROJECTS = [
  { key: 'demo-1:web', name: 'Web', lastAnalysisDate: '2026-10-01T08:00:00+0000' },
  { key: 'demo-1:new', name: 'New' },
]
const WEB = { key: 'demo-1:web', name: 'Web', measuresAsMap: { ncloc: 900 }, branches: [] }

const stubs = {
  LjDialog: { props: ['modelValue', 'title'], template: '<div v-if="modelValue" class="ljdialog"><h2>{{ title }}</h2><slot /><div class="foot"><slot name="footer" /></div></div>' },
  LjButton: { emits: ['click'], template: '<button class="ljbtn" @click="$emit(\'click\')"><slot /></button>' },
  PluginFeatures: { name: 'PluginFeatures', props: ['subscription', 'action'], template: '<div :class="`pf pf-${action}`" />' },
  VChip: { name: 'VChip', emits: ['click'], template: '<span class="v-chip" @click="$emit(\'click\', $event)"><slot /></span>' },
  VIcon: true,
  VTooltip: { name: 'VTooltip', props: ['modelValue'], emits: ['update:modelValue'], template: '<span class="tip"><slot name="activator" :props="{}" /></span>' },
  'v-progress-circular': true,
}

let t
beforeEach(() => {
  setActivePinia(createPinia())
  def.install()
  useI18nStore().setLocale('en')
  t = useI18nStore().t
  globalThis.fetch = vi.fn(async (url) => (String(url).includes('/project/demo-1%3Aweb') ? response(WEB) : response({}, 404)))
})

// The synthetic linked subscription of a project, drawn by the row renderer
const linked = (key, project) => ({
  id: 3604,
  node: SUB.node,
  parameters: { 'service:qa:sonarqube:url': URL, 'service:qa:sonarqube:project': key },
  data: { project },
})
const features = (row, action) => row.findAllComponents({ name: 'PluginFeatures' }).filter((c) => c.props('action') === action)

const detailCalls = () => fetch.mock.calls.map(([url]) => String(url)).filter((url) => url.includes('/project/'))

async function openDialog() {
  const w = mount(SonarOtherProjects, { props: { subscription: SUB, projects: PROJECTS, more: 22, text: '+24 projects', tooltip: 'demo-1:web — 2026-10-01\ndemo-1:new — never analyzed' }, global: { stubs } })
  expect(w.find('.ljdialog').exists()).toBe(false)
  await w.find('.sq-others-chip').trigger('click')
  await flushPromises()
  return w
}

describe('SonarOtherProjects', () => {
  it('reads nothing until opened', () => {
    mount(SonarOtherProjects, { props: { subscription: SUB, projects: PROJECTS, more: 0, text: '+2 projects' }, global: { stubs } })
    expect(fetch).not.toHaveBeenCalled()
  })

  it('opens the other projects on click as a table: name, key, metrics and link', async () => {
    const w = await openDialog()
    expect(w.find('.ljdialog h2').text()).toContain('demo-1')
    expect(w.findAll('table.sq-table thead th').map((th) => th.text())).toEqual(['Name', 'Key', 'Metrics', ''])
    const rows = w.findAll('table.sq-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].findAll('td').map((td) => td.text()).slice(0, 2)).toEqual(['Web', 'demo-1:web'])
    expect(rows[1].findAll('td')[2].text()).toBe('never analyzed')
    expect(w.text()).toContain('and 22 more')
  })

  it('links each project to its dashboard as a row does', async () => {
    const w = await openDialog()
    const rows = w.findAll('table.sq-table tbody tr')
    expect(features(rows[0], 'renderFeatures').map((c) => c.props('subscription').parameters)).toEqual([linked('demo-1:web').parameters])
    expect(features(rows[1], 'renderFeatures').map((c) => c.props('subscription').parameters)).toEqual([linked('demo-1:new').parameters])
  })

  it('shows the metrics of each analyzed project as a linked project, without its branches', async () => {
    globalThis.fetch = vi.fn(async (url) => (String(url).includes('/project/demo-1%3Aweb')
      ? response({ ...WEB, branches: [{ name: 'feature', measuresAsMap: {} }] }) : response({}, 404)))
    const w = await openDialog()
    // A never analyzed project has no measure to read
    expect(detailCalls()).toHaveLength(1)
    expect(detailCalls()[0]).toContain('rest/service/qa/sonarqube/3604/project/demo-1%3Aweb')
    const rows = w.findAll('table.sq-table tbody tr')
    const metrics = features(rows[0], 'renderDetailsFeatures')
    expect(metrics).toHaveLength(1)
    expect(metrics[0].props('subscription')).toEqual(linked('demo-1:web', { ...WEB, branches: [] }))
    expect(features(rows[1], 'renderDetailsFeatures')).toHaveLength(0)
  })

  it('tells when the metrics cannot be read', async () => {
    globalThis.fetch = vi.fn(async () => response({}, 500))
    const w = await openDialog()
    expect(w.findAll('table.sq-table tbody tr')[0].findAll('td')[2].text()).toBe(t('service:qa:sonarqube:project-details-error'))
  })

  it('hides its tooltip when the list opens', async () => {
    const w = mount(SonarOtherProjects, { props: { subscription: SUB, projects: PROJECTS, text: '+2 projects', tooltip: 'a\nb' }, global: { stubs } })
    const tip = w.findComponent({ name: 'VTooltip' })
    tip.vm.$emit('update:modelValue', true)
    await w.vm.$nextTick()
    expect(tip.props('modelValue')).toBe(true)
    await w.find('.sq-others-chip').trigger('click')
    expect(tip.props('modelValue')).toBe(false)
    // Not shown again over the open list
    tip.vm.$emit('update:modelValue', true)
    await w.vm.$nextTick()
    expect(tip.props('modelValue')).toBe(false)
  })

  it('lists every project, the latest analyzed one with its known metrics', async () => {
    const API = { key: 'demo-1:api', name: 'API', measuresAsMap: { ncloc: 1200 }, branches: [{ name: 'feature' }] }
    const w = mount(SonarOtherProjects, {
      props: { subscription: SUB, projects: [{ key: 'demo-1:api', name: 'API', lastAnalysisDate: '2026-10-10T08:00:00+0200' }, ...PROJECTS], latest: API, text: '+2 projects' },
      global: { stubs },
    })
    await w.find('.sq-others-chip').trigger('click')
    await flushPromises()
    expect(w.find('.ljdialog h2').text()).toBe('Projects of template demo-1')
    const rows = w.findAll('table.sq-table tbody tr')
    expect(rows.map((r) => r.findAll('td')[1].text())).toEqual(['demo-1:api', 'demo-1:web', 'demo-1:new'])
    // Not read again
    expect(detailCalls().map((url) => url.replace(/^.*\/project\//, ''))).toEqual(['demo-1%3Aweb'])
    expect(features(rows[0], 'renderDetailsFeatures')[0].props('subscription')).toEqual(linked('demo-1:api', { ...API, branches: [] }))
  })

  it('reads the details once', async () => {
    const w = await openDialog()
    await w.find('.foot .ljbtn').trigger('click')
    await w.find('.sq-others-chip').trigger('click')
    await flushPromises()
    expect(detailCalls()).toHaveLength(1)
  })
})
