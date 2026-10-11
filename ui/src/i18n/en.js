// SonarQube-specific i18n for the qa-sonarqube tool plugin.
export default {
  'service:qa:sonarqube': 'SonarQube',
  'service:qa:sonarqube:url': 'URL',
  'service:qa:sonarqube:project': 'Project',
  'service:qa:sonarqube:project-search': 'Search for a project by name or key…',
  'service:qa:sonarqube:key': 'Key',
  'service:qa:sonarqube:user': 'User',
  'service:qa:sonarqube:password': 'Password',
  // Node form hints: the `<id>-description` keys
  'service:qa:sonarqube:user-description': 'Any value when the password is a token',
  'service:qa:sonarqube:password-description': 'A password or a token. SonarQube Cloud only accepts a token',
  'service:qa:sonarqube:organization': 'Organization',
  'service:qa:sonarqube:organization-description': 'SonarQube Cloud only: the key of the organization',
  // Connection errors of an instance (connection test, node status)
  'error.rule.sonar-connection': 'SonarQube cannot be reached at this URL',
  'error.rule.sonar-login': 'SonarQube refused these credentials',
  'error.rule.sonar-rights': 'These credentials cannot browse the SonarQube projects',
  'error.rule.sonar-project': 'Unknown SonarQube project',
  'error.rule.sonar-organization': 'Unknown SonarQube Cloud organization',
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
