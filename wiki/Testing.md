# Testing

1. Install a deliberately older test build.
2. Publish or point at a newer release.
3. Run the update check and confirm the newer version is found.
4. Stage the update.
5. Confirm the running JAR is not modified.
6. Confirm the new JAR exists only in Paper's update folder.
7. Restart the server.
8. Confirm the new version loads.
9. Test a failed or missing release asset and confirm the old plugin remains intact.
10. Test a plugin already on the latest version and confirm nothing is downloaded.