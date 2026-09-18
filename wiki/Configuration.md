# Configuration

```yaml
updates:
  check-on-startup: true
  check-interval-hours: 6
  auto-stage: true
  include-prereleases: false
  github-token: ""
```

`auto-stage` downloads approved newer releases but does not restart the server.

For public repositories a GitHub token is optional. If one is needed, prefer the `GARDEN_GITHUB_TOKEN` environment variable instead of committing a token to a repository.

Each managed plugin maps to a repository and release asset pattern.

```yaml
plugins:
  GardenCore:
    repository: Heras-Garden/GardenCore
    asset: "GardenCore*.jar"
```
