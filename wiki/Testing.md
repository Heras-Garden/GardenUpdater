# Testing

1. Install GardenUpdater and start the server.
2. Run `/gardenupdate status`.
3. Run `/gardenupdate check`.
4. Confirm repositories with no releases report cleanly and do not modify installed JARs.
5. Publish a test release with a version newer than the installed plugin.
6. Run `/gardenupdate stage <plugin>`.
7. Confirm the running plugin JAR is unchanged.
8. Confirm `plugins/update/<PluginName>.jar` exists.
9. Restart the server.
10. Confirm the new plugin version loads.
11. Publish a JAR with the wrong plugin name and confirm GardenUpdater refuses to stage it.
12. Publish a release whose JAR version does not match the release tag and confirm it is refused.
