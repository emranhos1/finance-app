package com.finance.service.impl;

import com.finance.service.IGoogleDriveService;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.FileList;
import org.springframework.stereotype.Service;

import java.awt.Desktop;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class GoogleDriveServiceImpl implements IGoogleDriveService {

    private static final String APPLICATION_NAME = "Daily Finance Management System";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Collections.singletonList(DriveScopes.DRIVE_FILE);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String BACKUP_NAME_PREFIX = "backup_data";
    private static final String APP_FOLDER_NAME = "finance_app";
    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";
    private static final int SIGN_IN_TIMEOUT_SECONDS = 60;
    private static final DateTimeFormatter DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Drive driveService;
    private Credential currentCredential;

    @Override
    public boolean isConnected() {
        return driveService != null;
    }

    @Override
    public boolean hasStoredCredentials() {
        File tokensDir = new File(TOKENS_DIRECTORY_PATH);
        File[] files = tokensDir.listFiles();
        return tokensDir.exists() && files != null && files.length > 0;
    }

    @Override
    public void connect() throws Exception {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = getCredentials(httpTransport);
        driveService = new Drive.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        currentCredential = credential;
    }

    @Override
    public void disconnect() throws Exception {
        if (currentCredential != null) {
            String token = currentCredential.getRefreshToken() != null
                    ? currentCredential.getRefreshToken() : currentCredential.getAccessToken();
            if (token != null) {
                try {
                    revokeToken(token);
                } catch (Exception ignored) {
                    // best-effort - still clear local state below even if revoking with Google fails (e.g. offline)
                }
            }
        }
        deleteStoredTokens();
        currentCredential = null;
        driveService = null;
    }

    private void revokeToken(String token) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL("https://oauth2.googleapis.com/revoke?token=" + token).openConnection();
        conn.setRequestMethod("POST");
        conn.getResponseCode();
        conn.disconnect();
    }

    private void deleteStoredTokens() throws IOException {
        File tokensDir = new File(TOKENS_DIRECTORY_PATH);
        if (!tokensDir.exists()) return;
        try (var paths = Files.walk(tokensDir.toPath())) {
            paths.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
        }
    }

    @Override
    public String upload(File localDumpFile) throws Exception {
        ensureConnected();
        if (localDumpFile == null || !localDumpFile.isFile()) {
            throw new IllegalArgumentException("No local backup file to upload");
        }

        String folderId = getOrCreateAppFolderId();

        com.google.api.services.drive.model.File fileMetadata = new com.google.api.services.drive.model.File();
        fileMetadata.setName(localDumpFile.getName());
        fileMetadata.setParents(Collections.singletonList(folderId));
        FileContent mediaContent = new FileContent("application/sql", localDumpFile);

        com.google.api.services.drive.model.File uploaded = driveService.files()
                .create(fileMetadata, mediaContent)
                .setFields("id, name, modifiedTime")
                .execute();

        return "Uploaded " + uploaded.getName() + " to Google Drive (" + APP_FOLDER_NAME + " folder)";
    }

    @Override
    public File downloadLatest(File targetFolder) throws Exception {
        ensureConnected();
        if (targetFolder == null || !targetFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid download destination folder");
        }

        String folderId = getOrCreateAppFolderId();

        FileList result = driveService.files().list()
                .setQ("'" + folderId + "' in parents and name contains '" + BACKUP_NAME_PREFIX + "' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setPageSize(1)
                .setFields("files(id, name, modifiedTime)")
                .execute();

        List<com.google.api.services.drive.model.File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            throw new Exception("No " + BACKUP_NAME_PREFIX + "*.sql file found in the " + APP_FOLDER_NAME + " folder on Google Drive");
        }
        com.google.api.services.drive.model.File latest = files.get(0);

        File outputFile = new File(targetFolder, latest.getName());
        try (OutputStream out = new FileOutputStream(outputFile)) {
            driveService.files().get(latest.getId()).executeMediaAndDownloadTo(out);
        }
        return outputFile;
    }

    @Override
    public String getLatestBackupDisplay() throws Exception {
        ensureConnected();
        String folderId = getOrCreateAppFolderId();

        FileList result = driveService.files().list()
                .setQ("'" + folderId + "' in parents and name contains '" + BACKUP_NAME_PREFIX + "' and trashed = false")
                .setOrderBy("modifiedTime desc")
                .setPageSize(1)
                .setFields("files(id, name, modifiedTime)")
                .execute();

        List<com.google.api.services.drive.model.File> files = result.getFiles();
        if (files == null || files.isEmpty()) {
            return "No backups found yet";
        }
        com.google.api.services.drive.model.File latest = files.get(0);
        LocalDateTime modified = Instant.ofEpochMilli(latest.getModifiedTime().getValue())
                .atZone(ZoneId.systemDefault()).toLocalDateTime();
        return "Last backup: " + modified.format(DISPLAY_FORMAT);
    }

    /** Finds this app's dedicated "finance_app" Drive folder, creating it the first time if it doesn't exist yet. */
    private String getOrCreateAppFolderId() throws IOException {
        FileList result = driveService.files().list()
                .setQ("mimeType = '" + FOLDER_MIME_TYPE + "' and name = '" + APP_FOLDER_NAME + "' and trashed = false")
                .setFields("files(id, name)")
                .execute();

        List<com.google.api.services.drive.model.File> folders = result.getFiles();
        if (folders != null && !folders.isEmpty()) {
            return folders.get(0).getId();
        }

        com.google.api.services.drive.model.File folderMetadata = new com.google.api.services.drive.model.File();
        folderMetadata.setName(APP_FOLDER_NAME);
        folderMetadata.setMimeType(FOLDER_MIME_TYPE);
        com.google.api.services.drive.model.File folder = driveService.files().create(folderMetadata)
                .setFields("id")
                .execute();
        return folder.getId();
    }

    /**
     * credentials.json is never committed, so builds made by GitHub Actions don't contain it.
     * On an installed PC the file is copied once by hand next to DFMS.exe (the install folder) -
     * the update installer never deletes it. Falls back to the copy inside the jar, which is what
     * a local developer build (mvn javafx:run) uses.
     */
    private InputStream openCredentials() throws IOException {
        String appPath = System.getProperty("jpackage.app-path"); // set by the jpackage launcher (DFMS.exe)
        File[] candidates = {
                appPath != null ? new File(new File(appPath).getParentFile(), "credentials.json") : null,
                new File("credentials.json")
        };
        for (File f : candidates) {
            if (f != null && f.isFile()) return Files.newInputStream(f.toPath());
        }
        return GoogleDriveServiceImpl.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
    }

    private Credential getCredentials(NetHttpTransport httpTransport) throws Exception {
        InputStream in = openCredentials();
        if (in == null) {
            throw new FileNotFoundException("credentials.json not found. Copy it into the DFMS install folder " +
                    "(next to DFMS.exe), then try again.");
        }
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                httpTransport, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        AuthorizationCodeInstalledApp app = new AuthorizationCodeInstalledApp(flow, receiver, this::openBrowser);

        // If the user closes the browser tab without finishing sign-in, authorize() blocks forever
        // waiting on the local callback server, and that server keeps holding port 8888. Bound the
        // wait so an abandoned attempt gives up on its own and frees the port for the next try.
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "google-oauth-signin");
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Credential> future = executor.submit(() -> app.authorize("user"));
            try {
                return future.get(SIGN_IN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new IOException("Google sign-in timed out after " + SIGN_IN_TIMEOUT_SECONDS +
                        " seconds. Click 'Connect Google Drive' to try again.");
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) throw (Exception) cause;
                throw new IOException("Google sign-in failed", cause);
            }
        } finally {
            try {
                receiver.stop();
            } catch (Exception ignored) {
                // best-effort - the port must be freed even if the receiver was never fully started
            }
            executor.shutdownNow();
        }
    }

    /**
     * The library's default browse() only prints the URL to the console and swallows any failure,
     * so on machines where Desktop.browse() doesn't work it hangs forever with no visible error.
     * This opens the browser directly and falls back to an OS-specific command, throwing a clear
     * error (surfaced in the UI) only if every attempt fails.
     */
    private void openBrowser(String url) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(URI.create(url));
                return;
            } catch (Exception ignored) {
                // fall through to OS-specific fallback below
            }
        }
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", url);
            } else {
                pb = new ProcessBuilder("xdg-open", url);
            }
            pb.start();
        } catch (Exception e) {
            throw new IOException("Could not open your default browser automatically. Please open this address manually to sign in: " + url, e);
        }
    }

    private void ensureConnected() throws Exception {
        if (driveService == null) {
            throw new Exception("Not connected to Google Drive. Click 'Connect Google Drive' first.");
        }
    }
}
