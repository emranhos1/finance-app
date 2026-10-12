package com.finance.controller;

import com.finance.FinanceApplication;
import com.finance.context.SessionContext;
import com.finance.entity.User;
import com.finance.service.UpdateService;
import com.finance.service.UserService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class LoginController {
    private final UserService userService;
    private final SessionContext sessionContext;
    private final UpdateService updateService;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML private VBox updateBanner;
    @FXML private Label updateLabel;
    @FXML private Button updateButton;
    @FXML private ProgressBar updateProgress;
    @FXML private Label versionLabel;
    private UpdateService.ReleaseInfo availableUpdate;

    /** Shows the running version and checks GitHub for a newer one in the background, so a slow
     *  or missing internet connection never delays the login screen. */
    @FXML
    public void initialize() {
        String version = "Version " + updateService.getCurrentVersion();
        versionLabel.setText(version + "  ·  checking for updates...");
        Thread check = new Thread(() -> {
            UpdateService.CheckResult result = updateService.checkForUpdate();
            Platform.runLater(() -> {
                switch (result.getStatus()) {
                    case AVAILABLE:
                        versionLabel.setText(version + "  ·  update available");
                        showUpdateBanner(result.getRelease());
                        break;
                    case UP_TO_DATE:
                        versionLabel.setText(version + "  ·  ✓ Up to date");
                        break;
                    case FAILED:
                        versionLabel.setText(version + "  ·  update check failed (no internet?)");
                        break;
                    default:
                        versionLabel.setText(version);
                }
            });
        }, "update-check");
        check.setDaemon(true);
        check.start();
    }

    private void showUpdateBanner(UpdateService.ReleaseInfo release) {
        availableUpdate = release;
        updateLabel.setText("New version " + release.getVersion() + " is available (you have "
                + updateService.getCurrentVersion() + ").");
        updateBanner.setVisible(true);
        updateBanner.setManaged(true);
        // the login window has a fixed size; grow it by the banner's height so nothing gets squeezed
        Window window = updateBanner.getScene() != null ? updateBanner.getScene().getWindow() : null;
        if (window != null) window.setHeight(window.getHeight() + updateBanner.prefHeight(updateBanner.getWidth()) + 8);
    }

    @FXML
    public void handleShowReleaseNotes() {
        if (availableUpdate == null) return;
        TextArea notes = new TextArea(availableUpdate.getNotes());
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefSize(520, 360);
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("What's new in " + availableUpdate.getVersion());
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(notes);
        alert.showAndWait();
    }

    /** Asks where to save the update installer, downloads it there, then opens its wizard (which
     *  asks where to install) and closes this app so its files can be replaced. Data in MySQL is
     *  never touched. */
    @FXML
    public void handleUpdate() {
        if (availableUpdate == null) return;
        UpdateService.ReleaseInfo release = availableUpdate;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Where should the update " + release.getVersion() + " be downloaded?");
        File downloads = new File(System.getProperty("user.home"), "Downloads");
        if (downloads.isDirectory()) chooser.setInitialDirectory(downloads);
        File folder = chooser.showDialog(updateButton.getScene().getWindow());
        if (folder == null) return;

        updateButton.setDisable(true);
        updateButton.setText("Downloading...");
        updateProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        updateProgress.setVisible(true);
        updateProgress.setManaged(true);

        Thread worker = new Thread(() -> {
            try {
                Path installer = updateService.downloadInstaller(release, folder.toPath(),
                        p -> Platform.runLater(() -> updateProgress.setProgress(p)));
                updateService.launchInstaller(installer);
                Platform.runLater(() -> {
                    Platform.exit();
                    System.exit(0);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateButton.setDisable(false);
                    updateButton.setText("Update now");
                    updateProgress.setVisible(false);
                    updateProgress.setManaged(false);
                    showAlert("Update failed", "Could not update: " + e.getMessage()
                            + "\nCheck the internet connection and try again.");
                });
            }
        }, "update-download");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    public void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Validation Error", "Please enter username and password");
            return;
        }

        Optional<User> user = userService.authenticateUser(username, password);
        if (user.isPresent()) {
            sessionContext.login(user.get());
            try {
                FinanceApplication.showDashboard();
            } catch (Exception e) {
                showAlert("Error", "Failed to load dashboard");
            }
        } else {
            showAlert("Authentication Failed", "Invalid username or password, or account is inactive");
            passwordField.clear();
        }
    }

    @FXML
    public void handleRegister() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Register.fxml"));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage registerStage = new Stage();
            registerStage.setTitle("Register New Account");
            registerStage.setScene(new Scene(root, 400, 300));
            registerStage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to open registration window");
        }
    }

    @FXML
    public void handleForgotPassword() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ForgotPassword.fxml"));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage forgotPasswordStage = new Stage();
            forgotPasswordStage.setTitle("Reset Password");
            forgotPasswordStage.setScene(new Scene(root, 400, 420));
            forgotPasswordStage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to open reset password window");
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}