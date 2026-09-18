# How GardenUpdater Works

GardenUpdater currently follows successful builds from each plugin's `main` branch.

For every configured Garden plugin it:

1. Reads the current commit SHA of `main`.
2. Finds a successful GitHub Actions run built from that exact SHA.
3. Finds the configured workflow artifact.
4. Compares the main SHA with the last SHA GardenUpdater staged.
5. Downloads the Actions artifact when the SHA is new.
6. Extracts the matching plugin JAR.
7. Validates `plugin.yml` and the Bukkit plugin name.
8. Moves the validated JAR into Paper's update folder.
9. Saves the staged main SHA.
10. Leaves the running plugin untouched until the next full restart.

This means a failed Actions build can never be staged by GardenUpdater.

GardenUpdater does not use `/reload` and does not restart the server automatically.
