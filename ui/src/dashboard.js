/*
 * SonarQube project links and dates, shared by the row renderer and the dialog of the other projects.
 */

// The project dashboard. `/dashboard?id=<key>` is the route of SonarQube 9.9 to
// 26.x, and later versions redirect it with its query. The legacy
// `/dashboard/index/<key>` redirect was dropped in 10.0: it is a 404 since.
export function dashboardUrl(url, projectKey) {
  return `${url.replace(/\/$/, '')}/dashboard?id=${encodeURIComponent(projectKey)}`
}

// SonarQube analysis dates (`2026-10-10T08:00:00+0200`) as the day only
export function analysisDay(date) {
  return date ? String(date).slice(0, 10) : null
}
