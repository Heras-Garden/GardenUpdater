# How GardenUpdater Works

Each Garden plugin has its own public repository and GitHub Releases.

GardenUpdater has a configured list of managed repositories and plugin JAR names. On a scheduled check or administrator command it:

1. Reads the installed plugin version.
2. Requests the latest approved release from GitHub.
3. Compares semantic versions.
4. Downloads the matching JAR asset.
5. Writes it to Paper's configured update folder.
6. Records the staged version.
7. Tells staff that a restart is required.

The current JAR remains untouched until Paper performs the restart.

## Safety

- No `/reload`
- No replacing a loaded JAR in place
- Stable releases by default
- Pre-releases can be opt-in
- Failed downloads do not remove the installed JAR
- Staged files are checked before reporting success