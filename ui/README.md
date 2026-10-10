# plugin-qa-sonarqube — Vue UI

Tool plugin (`service:qa:sonarqube`), the SonarQube implementation of the
`qa` service. Compiled to `webjars/qa-sonarqube/vue/`.

Ships i18n parameter labels + `renderFeatures` (project dashboard link
`url/dashboard?id=<project>`), `renderDetailsKey` (project chip),
`renderDetailsFeatures` (metric badges + branch list, live data) and
`parameterField` (the subscribe wizard's project autocomplete, storing the
project key). `requires: ['qa']`.

```bash
npm install && npm run build && npm run lint && npm test
```
