// SonarQube-specific i18n for the qa-sonarqube tool plugin.
export default {
  'service:qa:sonarqube': 'SonarQube',
  'service:qa:sonarqube:url': 'URL',
  'service:qa:sonarqube:project': 'Project',
  'service:qa:sonarqube:key': 'Key',
  'service:qa:sonarqube:user': 'User',
  'service:qa:sonarqube:password': 'Password',
  // Metric badges (renderDetailsFeatures). Each tooltip shows name / value /
  // meaning, so every metric has a short `:<m>` name and a `:<m>:meaning`.
  'service:qa:sonarqube:value': 'Value',
  'service:qa:sonarqube:metric:ncloc': 'Lines of code',
  'service:qa:sonarqube:metric:ncloc:meaning': 'Non-commenting lines of code',
  'service:qa:sonarqube:metric:coverage': 'Coverage',
  'service:qa:sonarqube:metric:coverage:unit': '%',
  'service:qa:sonarqube:metric:coverage:meaning': 'Percentage of code covered by tests',
  'service:qa:sonarqube:metric:sqale_rating': 'Maintainability',
  'service:qa:sonarqube:metric:sqale_rating:meaning': 'A-to-E rating based on the technical debt ratio',
  'service:qa:sonarqube:metric:security_rating': 'Security',
  'service:qa:sonarqube:metric:security_rating:meaning': 'A-to-E rating based on detected security vulnerabilities',
  'service:qa:sonarqube:metric:reliability_rating': 'Reliability',
  'service:qa:sonarqube:metric:reliability_rating:meaning': 'A-to-E rating based on detected bugs',
  'service:qa:sonarqube:metric:security_review_rating': 'Security Review',
  'service:qa:sonarqube:metric:security_review_rating:meaning': 'A-to-E rating based on reviewed security hotspots',
  'service:qa:sonarqube:pull-request': 'Pull Request',
  'service:qa:sonarqube:branch': 'Branch',
}
