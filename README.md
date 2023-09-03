# :link: Ligoj SonarQube plugin [![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-qa-sonar/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.ligoj.plugin/plugin-qa-sonarqube)

[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=org.ligoj.plugin%3Aplugin-qa-sonarqube&metric=coverage)](https://sonarcloud.io/dashboard?id=org.ligoj.plugin%3Aplugin-qa-sonarqube)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?metric=alert_status&project=org.ligoj.plugin:plugin-qa-sonarqube)](https://sonarcloud.io/dashboard/index/org.ligoj.plugin:plugin-qa-sonarqube)
[![CodeFactor](https://www.codefactor.io/repository/github/ligoj/plugin-qa-sonarqube/badge)](https://www.codefactor.io/repository/github/ligoj/plugin-qa-sonarqube)
[![License](http://img.shields.io/:license-mit-blue.svg)](http://fabdouglas.mit-license.org/)

[Ligoj](https://github.com/ligoj/ligoj) SonarQube QA plugin, an
extending [QA plugin](https://github.com/ligoj/plugin-qa)
Provides the following features :

- Metrics (when available): `ncloc`, `coverage`, `sqale_rating`
- List of branches with theirs measures, and links
- Compatible with SonarQube Enterprise, SonarQube Community with or
  without [sonarqube-community-branch-plugin](https://github.com/mc1arke/sonarqube-community-branch-plugin)
- Detect SonarQube API (`< 6,3`, `>=6.3` and `>=6.6`) for compatibility
- Tested on all SonarQube versions from `4.0` to `9.9`

# Plugin parameters

| Parameter                         | Default                       | Note                                                                                                                                            |                     
|-----------------------------------|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| service:qa:sonarqube:metrics      | `ncloc,coverage,sqale_rating` | For 6.3+ API, `security_rating,reliability_rating,security_review_rating` metrics are added.                                                    |
| service:qa:sonar:max-branches     | `10`                          | Maximum displayed and retrieved branches. Main branch is always retrieved, the other branches are sorted by last activity. Only for `6.6+` API. |
| service:qa:sonar:metrics-branches | `ncloc,coverage,sqale_rating` | Retrieved and displayed metrics of each branch. By default, the same as the main metrics. When non-empty, one API call is executed per branch.  |
| service:qa:sonar:user             |                               | SonarQube's username. API key is not yet supported.                                                                                             |
| service:qa:sonar:password         |                               | SonarQube's password. API key is not yet supported. This parameter is encrypted in database.                                                    |
| service:qa:sonar:project          |                               | Linked project identifier. May be an integer or a string depending on the SonarQube API version.                                                |
| service:qa:sonar:url              |                               | SonarQube base URL. For sample `http://localhost:9000`.                                                                                         |
