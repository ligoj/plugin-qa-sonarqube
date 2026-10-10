/*
 * The subscribe wizard's "Project" input: the projects of the selected
 * SonarQube instance, searched as the user types (the host
 * LjRemoteSearchField), storing the project key.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mount, flushPromises } from '@vue/test-utils'
import def from '../index.js'

const PROJECT = { id: 'service:qa:sonarqube:project', type: 'TEXT', mandatory: true }
const PROJECTS = [
  { id: 'org.ligoj.plugin:plugin-id', key: 'org.ligoj.plugin:plugin-id', name: 'Ligoj - Plugin ID' },
  { id: 'org.ligoj.plugin:plugin-ui', key: 'org.ligoj.plugin:plugin-ui', name: 'Ligoj - Plugin UI' },
]

function jsonResponse(body) {
  return { ok: true, status: 200, headers: { get: () => 'application/json' }, json: async () => body }
}
// Expose the bindings of the field; the test types through `update:search`
const stubs = {
  LigojAutocomplete: { name: 'LigojAutocomplete', props: ['items', 'label', 'placeholder'], emits: ['update:search', 'update:modelValue'], template: '<input />' },
}

beforeEach(() => {
  // The search waits for a pause in the typing
  vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] })
  setActivePinia(createPinia())
  def.install()
  globalThis.fetch = vi.fn(async () => jsonResponse(PROJECTS))
})
afterEach(() => { vi.useRealTimers() })

describe('SonarQube project parameter field', () => {
  it('is a remote search to subscribe, the default input to edit a node or for another parameter', () => {
    const field = def.feature('parameterField', { parameter: PROJECT, isNode: false })
    expect(field).toBeTruthy()
    // The same component on each render, else the wizard would remount the input
    expect(def.feature('parameterField', { parameter: PROJECT, isNode: false })).toBe(field)
    expect(def.feature('parameterField', { parameter: PROJECT, isNode: true })).toBeNull()
    expect(def.feature('parameterField', { parameter: { id: 'service:qa:sonarqube:url' }, isNode: false })).toBeNull()
  })

  it('searches the projects of the selected instance and stores the project key', async () => {
    const Field = def.feature('parameterField', { parameter: PROJECT, isNode: false })
    const w = mount(Field, { props: { modelValue: '', parameter: PROJECT, formValues: {}, mode: 'link', isNode: false, instanceNodeId: 'service:qa:sonarqube:local' }, global: { stubs } })
    const input = w.findComponent({ name: 'LigojAutocomplete' })
    expect(input.props('label')).toBe('Project *')
    expect(input.props('placeholder')).toBe('Search for a project by name or key…')
    input.vm.$emit('update:search', 'plugin')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(fetch.mock.calls[0][0]).toBe('rest/service/qa/sonarqube/service%3Aqa%3Asonarqube%3Alocal/plugin')
    // The name is displayed, the key is the stored value and is shown as a subtitle
    expect(input.props('items')).toEqual([
      { title: 'Ligoj - Plugin ID', subtitle: 'org.ligoj.plugin:plugin-id', value: 'org.ligoj.plugin:plugin-id' },
      { title: 'Ligoj - Plugin UI', subtitle: 'org.ligoj.plugin:plugin-ui', value: 'org.ligoj.plugin:plugin-ui' },
    ])
  })

  it('stores the key of a project even when the server gives another identifier', async () => {
    globalThis.fetch = vi.fn(async () => jsonResponse([{ id: 'AYx-uuid', key: 'org.ligoj:app', name: 'App' }]))
    const Field = def.feature('parameterField', { parameter: PROJECT, isNode: false })
    const w = mount(Field, { props: { modelValue: '', parameter: PROJECT, instanceNodeId: 'service:qa:sonarqube:local' }, global: { stubs } })
    w.findComponent({ name: 'LigojAutocomplete' }).vm.$emit('update:search', 'app')
    vi.advanceTimersByTime(300)
    await flushPromises()
    expect(w.findComponent({ name: 'LigojAutocomplete' }).props('items')).toEqual([{ title: 'App', subtitle: 'org.ligoj:app', value: 'org.ligoj:app' }])
  })
})
