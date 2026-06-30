package dev.ultreon.craftos.session.mods;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ModUpdateChecker {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    public static ModInfo checkLatestVersion(String waylandcraft) throws Exception {
        return checkLatestVersion(waylandcraft, null);
    }

    public static ModInfo checkLatestVersion(String waylandcraft, String mcVersion) throws Exception {
        JsonNode latest = getLatestVersion(waylandcraft, mcVersion);

        if (latest == null) {
            return null;
        }

        String versionNumber = latest.get("version_number").asText();

        System.out.println("Latest version: " + versionNumber);

        System.out.println("Minecraft versions:");
        String gameVersion = null;
        for (JsonNode gv : latest.get("game_versions")) {
            System.out.println("- " + gv.asText());
            gameVersion = gv.asText();
        }

        System.out.println("Download URL:");
        System.out.println(getDownloadUrl(latest));

        return new ModInfo(
                versionNumber,
                gameVersion,
                getDownloadUrl(latest)
        );
    }

    public static JsonNode getLatestVersion(String slug, String mcVersion) throws Exception {
        String url = "https://api.modrinth.com/v2/project/" + slug + "/version";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "my-mod-checker/1.0")
                .GET()
                .build();

        HttpResponse<String> response =
                CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode versions = JSON.readTree(response.body());

        if (versions.isEmpty()) {
            throw new IllegalStateException("No versions found");
        }

        if (mcVersion == null) {
            return versions.get(0);
        }

        for (JsonNode version : versions) {
            for (JsonNode gameVersion : version.get("game_versions")) {
                if (mcVersion.equals(gameVersion.asText())) {
                    return version;
                }
            }
        }

        return null;
    }

    public static String getDownloadUrl(JsonNode version) {
        JsonNode files = version.get("files");

        if (files == null || files.isEmpty()) {
            return null;
        }

        // Prefer primary file if present
        for (JsonNode file : files) {
            if (file.has("primary") && file.get("primary").asBoolean()) {
                return file.get("url").asText();
            }
        }

        // fallback to first file
        return files.get(0).get("url").asText();
    }
}