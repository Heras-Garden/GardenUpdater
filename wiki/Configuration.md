# Configuration

```yaml
updates:
  check-on-startup: true
  check-interval-hours: 6
  auto-stage: true
  github-token: ""
```

`auto-stage` downloads a newer successful main build but does not restart the server.

For public repositories, GitHub metadata can often be read without authentication. Actions artifact downloads may require authentication or hit anonymous API limits. Prefer the `GARDEN_GITHUB_TOKEN` environment variable when a token is needed.

Each plugin declares the repository, branch, Actions artifact name, and JAR file pattern.

```yaml
plugins:
  GardenLands:
    repository: Heras-Garden/GardenLands
    branch: main
    artifact: GardenLands
    jar: "GardenLands*.jar"
```

The current main commit is only eligible when a successful Actions run exists for that exact SHA.
