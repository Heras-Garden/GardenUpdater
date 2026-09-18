# GardenUpdater

GardenUpdater checks the public Hera's Garden repositories for successful GitHub Actions builds from each plugin's `main` branch and stages newer builds for Paper to apply on the next server restart.

It does not replace loaded JARs, hot-reload plugins, or restart the server.

## Requirements

- Java 25
- Paper 26.2

## Build

```bash
mvn clean package
```

The JAR is written to `target/`.

## Commands

```text
/gardenupdate status
/gardenupdate check
/gardenupdate stage all
/gardenupdate stage <plugin>
/gardenupdate clear all
/gardenupdate clear <plugin>
```

The commands require `gardenupdater.admin`.

## Main branch update mode

GardenUpdater reads the current `main` commit SHA for each configured repository. It only uses an Actions artifact from a successful workflow run built from that exact SHA.

After an artifact is staged, the commit SHA is saved in `plugins/GardenUpdater/state.yml`. The same commit is not downloaded again on later checks.

A staged JAR is validated before it is moved into Paper's update folder. The JAR must contain `plugin.yml` and the plugin name must match the configured Garden plugin.

This is intended as the development update channel while the individual Garden repositories are being established. Stable GitHub Releases can replace this channel later without changing the server-side staging model.

## GitHub token

Public repository metadata may be readable without a token. GitHub can require authentication for Actions artifact downloads or enforce anonymous rate limits.

If needed, set `GARDEN_GITHUB_TOKEN` in the server environment. Use a token with read access to the Garden repositories and Actions artifacts. Do not commit a token to GitHub.
