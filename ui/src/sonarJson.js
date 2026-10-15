/**
 * Role mapping of the CREATE mode (`service:qa:sonarqube:roles`): a JSON object keyed by SonarQube group, each value
 * the non-empty list of the project permissions the permission template grants to the group. The checks mirror the
 * backend so the message shows while typing; the backend stays the reference. A blank value is valid: no group
 * permission.
 */

/** Project permissions of a permission template. */
export const PERMISSIONS = ['user', 'codeviewer', 'issueadmin', 'securityhotspotadmin', 'scan', 'admin']

/** Groups named in a summary; the others are counted. */
const SUMMARY_NAMES = 3

function parseObject(value) {
  const text = String(value ?? '').trim()
  if (!text) return { empty: true }
  try {
    const json = JSON.parse(text)
    return json && typeof json === 'object' && !Array.isArray(json) ? { json } : { invalid: true }
  } catch {
    return { invalid: true }
  }
}

/**
 * @param {string|null|undefined} value The typed mapping.
 * @returns {string|null} The i18n key of the first problem, or null.
 */
export function rolesError(value) {
  const { json, invalid } = parseObject(value)
  if (invalid) return 'error.rule.sonar-roles-json'
  for (const [group, permissions] of Object.entries(json || {})) {
    if (!group.trim() || !Array.isArray(permissions) || !permissions.length) return 'error.rule.sonar-roles-json'
    if (permissions.some((p) => !PERMISSIONS.includes(p))) return 'error.rule.sonar-roles-permission'
  }
  return null
}

/**
 * Role mapping sample of a project, placeholder of the roles input: its administrators and developers groups.
 *
 * @param {string} pkey The project key.
 */
export function rolesSample(pkey) {
  const key = pkey || 'project'
  return `{\n  "${key}-admin": ["admin", "user"],\n  "${key}-dev": ["user", "codeviewer", "scan"]\n}`
}

/**
 * Short text of the role mapping, for the subscription status tooltip: "2 groups: demo-1-admin, demo-1-dev", the first
 * names only then "+N".
 *
 * @returns {string|null} The summary, or null for a blank or unreadable value.
 */
export function rolesSummary(value, t) {
  const { json } = parseObject(value)
  if (!json) return null
  const names = Object.keys(json)
  const shown = names.slice(0, SUMMARY_NAMES).join(', ') + (names.length > SUMMARY_NAMES ? ` +${names.length - SUMMARY_NAMES}` : '')
  return t('service:qa:sonarqube:roles-summary', { count: names.length, names: shown }, names.length)
}
