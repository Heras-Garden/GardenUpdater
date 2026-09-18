package com.herasgarden.gardenupdater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public final class GithubActionsClient {
    private final HttpClient client;
    private final String token;

    public GithubActionsClient(String token) {
        this.token = token == null ? "" : token.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<MainBuild> latestSuccessfulMainBuild(ManagedPlugin plugin)
            throws IOException, InterruptedException {
        String headSha = branchHead(plugin.repository(), plugin.branch());
        Optional<WorkflowRun> run = successfulRun(plugin.repository(), plugin.branch(), headSha);
        if (run.isEmpty()) {
            return Optional.empty();
        }

        Optional<Artifact> artifact = artifact(
                plugin.repository(),
                run.get().id(),
                plugin.artifactName()
        );
        return artifact.map(value -> new MainBuild(headSha, run.get().id(), value));
    }

    public void downloadArtifact(Artifact artifact, Path destination)
            throws IOException, InterruptedException {
        HttpRequest request = request(artifact.archiveDownloadUrl()).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw authAware(
                    response.statusCode(),
                    "while downloading Actions artifact " + artifact.name()
            );
        }
    }

    private String branchHead(String repository, String branch)
            throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(
                request("https://api.github.com/repos/" + repository + "/commits/" + branch)
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw authAware(response.statusCode(), "while reading " + repository + "@" + branch);
        }

        JsonObject object = JsonParser.parseString(response.body()).getAsJsonObject();
        return object.get("sha").getAsString();
    }

    private Optional<WorkflowRun> successfulRun(
            String repository,
            String branch,
            String headSha
    ) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + repository
                + "/actions/runs?branch=" + encode(branch)
                + "&status=success&per_page=30";

        HttpResponse<String> response = client.send(
                request(url).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw authAware(response.statusCode(), "while reading Actions runs for " + repository);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray runs = root.getAsJsonArray("workflow_runs");
        for (JsonElement element : runs) {
            JsonObject run = element.getAsJsonObject();
            String sha = run.get("head_sha").getAsString();
            String conclusion = run.get("conclusion").isJsonNull()
                    ? ""
                    : run.get("conclusion").getAsString();
            if (headSha.equals(sha) && "success".equalsIgnoreCase(conclusion)) {
                return Optional.of(new WorkflowRun(run.get("id").getAsLong(), sha));
            }
        }
        return Optional.empty();
    }

    private Optional<Artifact> artifact(
            String repository,
            long runId,
            String expectedName
    ) throws IOException, InterruptedException {
        String url = "https://api.github.com/repos/" + repository
                + "/actions/runs/" + runId + "/artifacts?per_page=100";

        HttpResponse<String> response = client.send(
                request(url).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw authAware(response.statusCode(), "while reading Actions artifacts for " + repository);
        }

        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray artifacts = root.getAsJsonArray("artifacts");
        for (JsonElement element : artifacts) {
            JsonObject object = element.getAsJsonObject();
            if (object.get("expired").getAsBoolean()) {
                continue;
            }
            String name = object.get("name").getAsString();
            if (!expectedName.equalsIgnoreCase(name)) {
                continue;
            }
            return Optional.of(new Artifact(
                    object.get("id").getAsLong(),
                    name,
                    object.get("size_in_bytes").getAsLong(),
                    object.get("archive_download_url").getAsString()
            ));
        }
        return Optional.empty();
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Heras-Garden-GardenUpdater");
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private IOException authAware(int statusCode, String context) {
        if ((statusCode == 401 || statusCode == 403) && token.isBlank()) {
            return new IOException(
                    "GitHub returned HTTP " + statusCode + " " + context
                            + ". Set GARDEN_GITHUB_TOKEN with repository Actions read access."
            );
        }
        return new IOException("GitHub returned HTTP " + statusCode + " " + context + ".");
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    public record MainBuild(String headSha, long runId, Artifact artifact) {
    }

    public record Artifact(long id, String name, long size, String archiveDownloadUrl) {
    }

    private record WorkflowRun(long id, String headSha) {
    }
}
