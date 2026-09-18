package com.herasgarden.gardenupdater;

public record UpdateStatus(
        ManagedPlugin plugin,
        String installedVersion,
        String mainSha,
        GithubActionsClient.Artifact artifact,
        boolean updateAvailable,
        boolean staged,
        String detail
) {
}
