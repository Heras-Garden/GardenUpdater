package com.herasgarden.gardenupdater;

public record UpdateStatus(
        ManagedPlugin plugin,
        String installedVersion,
        String releaseVersion,
        GithubReleaseClient.Asset asset,
        boolean updateAvailable,
        boolean staged,
        String detail
) {
}
