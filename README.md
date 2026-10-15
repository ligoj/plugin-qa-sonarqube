# :link: Ligoj SonarQube plugin ![Maven Central](https://img.shields.io/maven-central/v/org.ligoj.plugin/plugin-qa-sonarqube)

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
- Tested on all SonarQube versions from `4.0` to `9.9.3`
- Compatible with SonarQube Cloud, in any region such as `https://sonarcloud.io` or `https://sonarqube.us`, with a token and an organization
- CREATE mode: creates a SonarQube permission template for the project (or reuses one with the same pattern), creates the mapped groups when missing and grants them their project permissions on it. The subscription shows the latest analyzed project matching the template pattern, the others listed. Unsubscribing with the remote data deletes the template; groups and projects are kept. It needs the `Administer System` permission

# Plugin parameters

| Parameter                             | Default                       | Note                                                                                                                                                   |
|---------------------------------------|-------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------|
| service:qa:sonarqube:metrics          | `ncloc,coverage,sqale_rating` | For 6.3+ API, `security_rating,reliability_rating,security_review_rating` metrics are added.                                                           |
| service:qa:sonarqube:max-branches     | `10`                          | Maximum displayed and retrieved branches. Main branch is always retrieved, the other branches are sorted by last activity. Only for `6.6+` API.        |
| service:qa:sonarqube:metrics-branches | `ncloc,coverage,sqale_rating` | Retrieved and displayed metrics of each branch. By default, the same as the main metrics. When non-empty, one API call is executed per branch.         |
| service:qa:sonarqube:user             |                               | SonarQube's username. Can be any string when password is a SonarQube API token.                                                                        |
| service:qa:sonarqube:password         |                               | SonarQube's password. Only API tokens prior 9.3 (generic) and 9.3+ of type `user` and `global` are supported. This parameter is encrypted in database. |
| service:qa:sonarqube:organization     |                               | SonarQube Cloud organization key, required to search its projects. Empty for a SonarQube Server.                                                      |
| service:qa:sonarqube:project          |                               | LINK mode: linked project identifier. May be an integer or a string depending on the SonarQube API version.                                            |
| service:qa:sonarqube:template         | `<project key>`               | CREATE mode, required: name of the permission template.                                                                                                |
| service:qa:sonarqube:template-pattern | `<project key>:.*`            | CREATE mode, required: regular expression of the project keys the template applies to, when SonarQube creates them.                                   |
| service:qa:sonarqube:roles            |                               | CREATE mode: JSON object keyed by group, each value a list of project permissions among `user`, `codeviewer`, `issueadmin`, `securityhotspotadmin`, `scan`, `admin`. A missing group is created. |
| service:qa:sonarqube:url              |                               | SonarQube base URL. For sample `http://localhost:9000`.                                                                                                |

# Create mode

A subscription in CREATE mode creates the SonarQube permission template of the project, then gives the groups of its
role mapping their project permissions on it. SonarQube applies the template to the projects created afterwards whose
key matches its pattern, typically by the first analysis: the existing projects keep their permissions. The node user
needs the `Administer System` permission. On SonarQube Cloud, every call is scoped to the node organization.

## Node mode

The SonarQube tool accepts both modes. An instance created while the tool was LINK only keeps offering LINK: edit the
node and switch its mode from Link to All, or upsert it with `--mode all` (see [CLI samples](#cli-samples)). Only a
Link or Create node can change its mode, to All: any other change is refused (`invalid-mode-transition`), it would
break the subscriptions created on the node.

## Permission template

| Parameter        | Subscription form default | Behavior                                                                                                   |
|------------------|---------------------------|------------------------------------------------------------------------------------------------------------|
| template         | `<project key>`           | Template name. An existing template of the same name, compared ignoring case, and the same pattern is reused with a warning (`sonar-template-reused`). Another pattern is refused (`sonar-template-exists`). |
| template-pattern | `<project key>:.*`        | Java regular expression of the project keys (`sonar-template-pattern` when invalid).                         |

The subscription row shows the latest analyzed project matching the pattern as a linked project: its key, its metrics,
its branches and its dashboard link. A "+N projects" chip lists the other matching projects with their
last analysis date in its tooltip, 20 at most. On click, it opens the table of all the matching projects: name, key,
metrics once analyzed and dashboard link. The metrics are read on demand with
`GET rest/service/qa/sonarqube/<subscription>/project/<key>`. This call only reads a project matching the template
pattern, as SonarQube has it: the node credentials can read any project. A template deleted on SonarQube puts the
subscription down (`sonar-template`).

## Role mapping

`service:qa:sonarqube:roles` is a JSON object keyed by SonarQube group name, each value the project permissions of
that group among `user`, `codeviewer`, `issueadmin`, `securityhotspotadmin`, `scan` and `admin`. A missing group is
created. Each group gets exactly its listed permissions on the template: the other ones are removed. A blank mapping
creates the template without any group permission.

```json
{
  "demo-1-admin": ["admin", "user", "codeviewer", "issueadmin", "securityhotspotadmin", "scan"],
  "demo-1-dev": ["user", "codeviewer", "issueadmin", "scan"],
  "demo-1-reader": ["user", "codeviewer"]
}
```

A `ligoj bootstrap` definition writes the same permissions as `{"<role>": {"permissions": [...]}}` in the `roles` of a
template, the role naming a declared group. Here, the key is the SonarQube group name itself.
An invalid JSON or an empty list is refused (`sonar-roles-json`), as an unknown permission (`sonar-roles-permission`).

## Subscription sample

A node accepting both modes, then a CREATE subscription of the project `1` (key `demo-1`), sent with `POST rest/node`
and `POST rest/subscription`:

```json
{
  "id": "service:qa:sonarqube:main",
  "name": "SonarQube",
  "node": "service:qa:sonarqube",
  "mode": "ALL",
  "parameters": [
    { "parameter": "service:qa:sonarqube:url", "text": "https://sonarqube.sample.com" },
    { "parameter": "service:qa:sonarqube:user", "text": "ligoj" },
    { "parameter": "service:qa:sonarqube:password", "text": "squ_0123456789abcdef0123456789abcdef01234567" }
  ]
}
```

```json
{
  "node": "service:qa:sonarqube:main",
  "project": 1,
  "mode": "CREATE",
  "parameters": [
    { "parameter": "service:qa:sonarqube:template", "text": "demo-1" },
    { "parameter": "service:qa:sonarqube:template-pattern", "text": "demo-1:.*" },
    {
      "parameter": "service:qa:sonarqube:roles",
      "text": "{\"demo-1-admin\": [\"admin\", \"user\", \"codeviewer\", \"issueadmin\", \"securityhotspotadmin\", \"scan\"], \"demo-1-dev\": [\"user\", \"codeviewer\", \"issueadmin\", \"scan\"], \"demo-1-reader\": [\"user\", \"codeviewer\"]}"
    }
  ]
}
```

The role mapping is sent as text, so its JSON is escaped. This subscription creates the template `demo-1` for the
project keys `demo-1:*`, creates the three groups when missing, then grants them their permissions on the template.

## CLI samples

The same node and subscription with the [Ligoj CLI](https://github.com/ligoj/ligoj-cli). The node parameters, in
`sonarqube-node.json`:

```json
{
  "service:qa:sonarqube:url": "https://sonarqube.sample.com",
  "service:qa:sonarqube:user": "ligoj",
  "service:qa:sonarqube:password": "squ_0123456789abcdef0123456789abcdef01234567"
}
```

```bash
ligoj node upsert --id "service:qa:sonarqube:main" --name "SonarQube" --mode all --from sonarqube-node.json
```

The same command widens an existing Link instance to All. Give it the full parameter file: an upsert replaces the
node parameters, the ones missing from the file are deleted.

The subscription parameters, in `sonarqube-create.json`. The CLI renders `{{ project }}` with the `--project` value,
so this file serves every project:

```json
{
  "service:qa:sonarqube:template": "{{ project }}",
  "service:qa:sonarqube:template-pattern": "{{ project }}:.*",
  "service:qa:sonarqube:roles": "{\"{{ project }}-admin\": [\"admin\", \"user\", \"codeviewer\", \"issueadmin\", \"securityhotspotadmin\", \"scan\"], \"{{ project }}-dev\": [\"user\", \"codeviewer\", \"issueadmin\", \"scan\"], \"{{ project }}-reader\": [\"user\", \"codeviewer\"]}"
}
```

```bash
ligoj subscription create --project demo-1 --node "service:qa:sonarqube:main" --mode create --from sonarqube-create.json
ligoj subscription create --project demo-1 --node "service:qa:sonarqube:main" --mode create --from '{"service:qa:sonarqube:template": "demo-1", "service:qa:sonarqube:template-pattern": "demo-1:.*", "service:qa:sonarqube:roles": "{\"demo-1-dev\": [\"user\", \"codeviewer\", \"scan\"]}"}'
```

The CLI prints the subscription identifier, and skips the creation when the project already has a subscription to
this node with the same parameters. Unsubscribe, deleting the template too:

```bash
ligoj subscription delete --id 362 --with-data
```

## Deletion

Deleting the subscription with the "remote data" option deletes its permission template. The groups are kept, often
shared or synchronized from a directory, and the projects too. A template already deleted is reported as a warning
(`sonar-template-missing`, `X-Ligoj-Warning` header, shown as a toast in the UI and printed by the CLI) without
blocking the unsubscription. Without the option, SonarQube is not modified.
