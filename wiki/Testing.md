# Testing

1. Install GardenUpdater and start the server.
2. Run `/gardenupdate status`.
3. Run `/gardenupdate check`.
4. Confirm repositories without a successful current-main build are skipped safely.
5. Push a tested change to a plugin's `main` branch.
6. Wait for that plugin's GitHub Actions build to pass.
7. Run `/gardenupdate check` and confirm the new main SHA is reported.
8. Run `/gardenupdate stage <plugin>`.
9. Confirm the running plugin JAR is unchanged.
10. Confirm `plugins/update/<PluginName>.jar` exists.
11. Restart the server.
12. Confirm the new build loads.
13. Run `/gardenupdate check` again and confirm the same SHA is not downloaded again.
14. Test a failed main workflow and confirm GardenUpdater refuses to stage it.
15. Test an artifact containing the wrong plugin JAR and confirm validation rejects it.
