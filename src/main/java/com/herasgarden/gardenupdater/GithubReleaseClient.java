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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GithubReleaseClient {
    private final HttpClient client;
    private final String token;

    public GithubReleaseClient(String token) {
        this.token = token == null ? "" : token.trim();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public Optional<Release> latestRelease(String repository, boolean includePrereleases)
            throws IOException, InterruptedException {
        String endpoint = includePrereleases
                ? "https://api.github.com/repos/" + repository + "/releases?per_page=20"
                : "https://api.github.com/repos/" + repository + "/releases/latest";

        HttpRequest request = request(endpoint).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 404) {
            return Optional.empty();
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " for " + repository + ".");
        }

        if (includePrereleases) {
            JsonArray releases = JsonParser.parseString(response.body()).getAsJsonArray();
            for (JsonElement element : releases) {
                JsonObject object = element.getAsJsonObject();
                if (!object.get("draft").getAsBoolean()) {
                    return Optional.of(parseRelease(object));
                }
            }
            return Optional.empty();
        }

        return Optional.of(parseRelease(JsonParser.parseString(response.body()).getAsJsonObject()));
    }

    public void download(Asset asset, Path destination) throws IOException, InterruptedException {
        HttpRequest request = request(asset.downloadUrl()).GET().build();
        HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(destination));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("GitHub returned HTTP " + response.statusCode() + " while downloading " + asset.name() + ".");
        }
    }

    private HttpRequest.Builder request(String url) {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "Heras-Garden-GardenUpdater");
        if (!token.isBlank()) {
            request.header("Authorization", "Bearer " + token);
        }
        return request;
    }

    private Release parseRelease(JsonObject object) {
        String tag = object.get("tag_name").getAsString();
        boolean prerelease = object.get("prerelease").getAsBoolean();
        List<Asset> assets = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray("assets")) {
            JsonObject asset = element.getAsJsonObject();
            assets.add(new Asset(
                    asset.get("name").getAsString(),
                    asset.get("browser_download_url").getAsString(),
                    asset.get("size").getAsLong()
            ));
        }
        return new Release(tag, prerelease, List.copyOf(assets));
    }

    public record Release(String tag, boolean prerelease, List<Asset> assets) {
    }

    public record Asset(String name, String downloadUrl, long size) {
    }
}
