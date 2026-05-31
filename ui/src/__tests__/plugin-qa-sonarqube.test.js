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
})
