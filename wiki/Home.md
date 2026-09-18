# GardenUpdater Wiki

GardenUpdater keeps the Hera's Garden plugin family current without manually replacing every JAR.

It checks approved GitHub Releases, downloads newer plugin JARs, verifies the downloaded file, and stages it in Paper's plugin update folder. Paper applies the staged JAR on the next full server restart.

GardenUpdater does not hot-reload Garden plugins.