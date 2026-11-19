package com.finance.util;

import com.finance.service.UpdateService;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Path;
import java.util.function.DoubleConsumer;
import java.util.prefs.Preferences;

/**
 * The update steps shared by the Login banner and the Dashboard sidebar item: choose the download folder, download the
 * installer with progress, start its wizard and close the app. MySQL data is not touched.
 * With the dev switch (-Ddfms.fakeUpdate) the download is only simulated and the installer is not started.
 */
public final class UpdateFlow {
    private static final String DOWNLOAD_FOLDER_KEY = "updateDownloadFolder";

    private UpdateFlow() {}

    /**
     * @param onStart    called once the folder is chosen, before the download begins (switch the UI to "downloading")
     * @param onProgress called with 0..1 on the FX thread while downloading
     * @param onFailed   called on the FX thread when the download fails (or in test mode when it ends), to reset the UI
     */
    public static void start(Window owner, UpdateService updateService, UpdateService.ReleaseInfo release,
                             Runnable onStart, DoubleConsumer onProgress, Runnable onFailed) {
        if (release == null) return;

        Preferences prefs = Preferences.userNodeForPackage(UpdateFlow.class);
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Where should the update " + release.getVersion() + " be downloaded?");
        File lastFolder = new File(prefs.get(DOWNLOAD_FOLDER_KEY, ""));
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (lastFolder.isDirectory()) chooser.setInitialDirectory(lastFolder);
        else if (downloads.isDirectory()) chooser.setInitialDirectory(downloads);
        File folder = chooser.showDialog(owner);
        if (folder == null) return;
        prefs.put(DOWNLOAD_FOLDER_KEY, folder.getAbsolutePath());

        onStart.run();

        boolean fake = updateService.isFake(release);
        Thread worker = new Thread(() -> {
            try {
                Path installer = updateService.downloadInstaller(release, folder.toPath(),
                        p -> Platform.runLater(() -> onProgress.accept(p)));
                if (fake) {
                    Platform.runLater(() -> {
                        onFailed.run();
                        alert(Alert.AlertType.INFORMATION, "Test mode", "Test mode (dfms.fakeUpdate): the download was only "
                                + "simulated, the installer was not started.");
                    });
                    return;
                }
                updateService.deleteOldDownloads(installer);
                updateService.launchInstaller(installer);
                Platform.runLater(() -> {
                    Platform.exit();
                    System.exit(0);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    onFailed.run();
                    alert(Alert.AlertType.ERROR, "Update failed", "Could not update: " + e.getMessage()
                            + "\nCheck the internet connection and try again.");
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert a = new AppAlert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
    }
}
