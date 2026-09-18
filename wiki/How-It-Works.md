# How GardenUpdater Works

Each Garden plugin has its own public repository and publishes stable JARs through GitHub Releases.

GardenUpdater:

1. Reads the installed Bukkit plugin version.
2. Requests the latest approved GitHub Release.
3. Matches the release JAR configured for that plugin.
4. Compares semantic versions.
5. Downloads newer JARs to a temporary directory.
6. Opens the JAR and validates `plugin.yml`.
7. Confirms the plugin name and release version match.
8. Moves the validated JAR into Paper's update folder.
9. Leaves the running plugin untouched.
10. Paper applies the staged JAR on the next restart.

Stable releases are used by default. Pre-releases are opt-in.
