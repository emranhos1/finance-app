package com.finance.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;

/**
 * Checks GitHub Releases for a newer version of the app and downloads/launches its update
 * installer. The repo is public, so no token is needed. Every release built by
 * .github/workflows/release.yml carries two assets this class relies on:
 * {@code DFMS-Update-<version>.exe} and {@code DFMS-Update-<version>.exe.sha256}.
 */
@Service
@Slf4j
public class UpdateService {
    private static final String UPDATE_ASSET_PREFIX = "DFMS-Update-";
    /** newline-separated full paths of the installers this app has downloaded */
    private static final String DOWNLOADS_KEY = "downloadedInstallers";

    private final Preferences prefs = Preferences.userNodeForPackage(UpdateService.class);

    private final String currentVersion;
    private final String repo;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public UpdateService(@Value("${app.version}") String currentVersion,
                         @Value("${app.update.repo}") String repo) {
        this.currentVersion = currentVersion;
        this.repo = repo;
    }

    public String getCurrentVersion() { return currentVersion; }

    /** Outcome of an update check, so the login screen can say which one happened. */
    public enum CheckStatus { AVAILABLE, UP_TO_DATE, FAILED, NOT_CHECKED }

    @Data
    @AllArgsConstructor
    public static class CheckResult {
        private CheckStatus status;
        /** Only set when status is AVAILABLE. */
        private ReleaseInfo release;
    }

    /** Asks GitHub for the latest release. Never throws: offline/any error comes back as FAILED. */
    public CheckResult checkForUpdate() {
        // unfiltered "@project.version@" means a dev run straight from an IDE - never offer updates there
        if (currentVersion == null || currentVersion.contains("@")) return new CheckResult(CheckStatus.NOT_CHECKED, null);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + repo + "/releases/latest"))
                    .header("Accept", "application/vnd.github+json")
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) return new CheckResult(CheckStatus.FAILED, null);

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String latest = stripV(json.get("tag_name").getAsString());
            if (compareVersions(latest, currentVersion) <= 0) return new CheckResult(CheckStatus.UP_TO_DATE, null);

            String installerUrl = null;
            String checksumUrl = null;
            JsonArray assets = json.getAsJsonArray("assets");
            for (JsonElement el : assets) {
                JsonObject asset = el.getAsJsonObject();
                String name = asset.get("name").getAsString();
                String url = asset.get("browser_download_url").getAsString();
                if (name.startsWith(UPDATE_ASSET_PREFIX) && name.endsWith(".exe")) installerUrl = url;
                if (name.startsWith(UPDATE_ASSET_PREFIX) && name.endsWith(".exe.sha256")) checksumUrl = url;
            }
            // newer release whose installer isn't attached (yet) - nothing installable, treat as current
            if (installerUrl == null) return new CheckResult(CheckStatus.UP_TO_DATE, null);

            String notes = json.has("body") && !json.get("body").isJsonNull() ? json.get("body").getAsString() : "";
            return new CheckResult(CheckStatus.AVAILABLE, new ReleaseInfo(latest, notes, installerUrl, checksumUrl));
        } catch (Exception e) {
            log.info("Update check failed: {}", e.toString());
            return new CheckResult(CheckStatus.FAILED, null);
        }
    }

    /** Downloads the update installer into the folder the user picked, verifying its SHA-256 when
     *  the release has one. If that folder already holds this version's installer intact (e.g. the
     *  wizard was cancelled last time), it is reused instead of downloaded again. */
    public Path downloadInstaller(ReleaseInfo release, Path folder, DoubleConsumer progress) throws IOException, InterruptedException {
        Path target = folder.resolve(UPDATE_ASSET_PREFIX + release.getVersion() + ".exe");
        String expected = release.getChecksumUrl() != null ? fetchExpectedChecksum(release) : null;
        if (expected != null && Files.isRegularFile(target) && expected.equalsIgnoreCase(sha256Of(target))) {
            progress.accept(1.0);
            rememberDownload(target);
            return target;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(release.getInstallerUrl())).GET().build();
        HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) throw new IOException("Download failed (HTTP " + response.statusCode() + ")");

        long total = response.headers().firstValueAsLong("Content-Length").orElse(-1L);
        MessageDigest sha256 = sha256();
        try (InputStream in = new DigestInputStream(response.body(), sha256);
             var out = Files.newOutputStream(target)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) progress.accept((double) done / total);
            }
        }

        if (expected != null && !expected.equalsIgnoreCase(toHex(sha256.digest()))) {
            Files.deleteIfExists(target);
            throw new IOException("Downloaded file is corrupted (checksum mismatch) - please try again.");
        }
        rememberDownload(target);
        return target;
    }

    /**
     * Deletes the update installers this app downloaded earlier, keeping only {@code keep}. The
     * download folder is never scanned: only exact paths the app itself recorded at download time
     * are touched, so other files in that folder (or another app's installers) are never deleted.
     * Best effort - a file that can't be deleted (e.g. open elsewhere) stays recorded for next time.
     */
    public void deleteOldDownloads(Path keep) {
        Path keepPath = keep.toAbsolutePath().normalize();
        List<String> stillThere = new ArrayList<>();
        for (String recorded : recordedDownloads()) {
            Path file = Path.of(recorded);
            if (file.equals(keepPath)) {
                stillThere.add(recorded);
                continue;
            }
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                log.info("Could not delete old installer {}: {}", file, e.toString());
                stillThere.add(recorded);
            }
        }
        prefs.put(DOWNLOADS_KEY, String.join("\n", stillThere));
    }

    private void rememberDownload(Path file) {
        String path = file.toAbsolutePath().normalize().toString();
        List<String> recorded = recordedDownloads();
        if (!recorded.contains(path)) recorded.add(path);
        prefs.put(DOWNLOADS_KEY, String.join("\n", recorded));
    }

    private List<String> recordedDownloads() {
        List<String> list = new ArrayList<>();
        for (String line : prefs.get(DOWNLOADS_KEY, "").split("\n")) {
            if (!line.isBlank()) list.add(line.trim());
        }
        return list;
    }

    private String fetchExpectedChecksum(ReleaseInfo release) throws IOException, InterruptedException {
        HttpRequest sumRequest = HttpRequest.newBuilder(URI.create(release.getChecksumUrl())).GET().build();
        HttpResponse<String> response = http.send(sumRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IOException("Could not read the update's checksum (HTTP " + response.statusCode() + ")");
        return response.body().trim().split("\\s+")[0];
    }

    private static String sha256Of(Path file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return toHex(digest.digest());
    }

    /** Opens the installer's normal wizard, which asks where to install (pre-filled with the
     *  current install folder) and can start the app again when it finishes. */
    public void launchInstaller(Path installer) throws IOException {
        new ProcessBuilder(installer.toString()).start();
    }

    /** Compares dotted numeric versions ("1.10.0" > "1.9.2"); non-numeric parts count as 0. */
    static int compareVersions(String a, String b) {
        String[] pa = stripV(a).split("\\.");
        String[] pb = stripV(b).split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int x = i < pa.length ? parse(pa[i]) : 0;
            int y = i < pb.length ? parse(pb[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D.*$", "")); } catch (NumberFormatException e) { return 0; }
    }

    private static String stripV(String v) {
        return v != null && (v.startsWith("v") || v.startsWith("V")) ? v.substring(1) : v;
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @Data
    @AllArgsConstructor
    public static class ReleaseInfo {
        private String version;
        private String notes;
        private String installerUrl;
        private String checksumUrl;
    }
}
