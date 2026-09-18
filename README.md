# GardenUpdater

GardenUpdater checks the public Hera's Garden plugin repositories for GitHub Releases and stages newer JARs for Paper to apply on the next server restart.

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

## Automatic updates

By default GardenUpdater checks GitHub every six hours and automatically stages stable releases. A full server restart is still required before Paper applies the staged JARs.

The updater reads GitHub Releases, not branch builds. A release asset must contain a valid `plugin.yml`, must have the expected plugin name, and must have the same version as the GitHub release tag.

Public repositories do not require a GitHub token. If rate limits become an issue, set the `GARDEN_GITHUB_TOKEN` environment variable or configure `updates.github-token`.

## Managed repositories

- Heras-Garden/GardenCore
- Heras-Garden/GardenLands
- Heras-Garden/GardenEssentials
- Heras-Garden/GardenModeration
- Heras-Garden/GardenPost
- Heras-Garden/GardenTrade
- Heras-Garden/GardenCivics
- Heras-Garden/GardenEvents
- Heras-Garden/GardenCosmetics
- Heras-Garden/GardenUpdater
