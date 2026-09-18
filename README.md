# GardenUpdater

Release updater for the Hera's Garden plugin family. Updates are staged in Paper's update folder and applied on restart.

## Requirements

- Java 25
- Paper 26.2
- Paper 26.2

## Build

```bash
mvn clean package
```

Built JARs are written to `target/`.

## Releases

Tags named `vX.Y.Z` build a release JAR and attach it to a GitHub Release. GardenUpdater reads those releases and stages newer versions for the next server restart.
