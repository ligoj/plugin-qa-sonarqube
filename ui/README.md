# plugin-qa-sonarqube — Vue UI

Tool plugin (`service:qa:sonarqube`), the SonarQube implementation of the
`qa` service. Compiled to `webjars/qa-sonarqube/vue/`.

Ships i18n parameter labels + `renderFeatures` (project dashboard link
`url/dashboard/index/<project>`) and `renderDetailsKey` (project chip).
`requires: ['qa']`. The legacy metric badges + branch list
(`renderDetailsFeatures`, live data) are deferred.

```bash
npm install && npm run build && npm run lint && npm test
```
