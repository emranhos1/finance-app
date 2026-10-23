package com.finance.controller;

import com.finance.entity.User;
import com.finance.service.DataIntegrityService;
import com.finance.service.IBackupService;
import com.finance.service.IGoogleDriveService;
import com.finance.service.UserService;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequiredArgsConstructor
public class AdminPanelController {
    private final UserService userService;
    private final IBackupService backupService;
    private final IGoogleDriveService googleDriveService;
    private final DataIntegrityService dataIntegrityService;

    @FXML
    private TableView<User> usersTable;

    @FXML
    private TableColumn<User, Long> idColumn;

    @FXML
    private TableColumn<User, String> usernameColumn;

    @FXML
    private TableColumn<User, User.UserRole> roleColumn;

    @FXML
    private TableColumn<User, User.UserStatus> statusColumn;

    @FXML private TextField securityQuestionField;
    @FXML private TextField securityAnswerField;

    @FXML private Button exportLocalBtn;
    @FXML private Button importLocalBtn;
    @FXML private Label localStatusLabel;
    @FXML private ProgressIndicator localProgress;

    @FXML private Button connectDriveBtn;
    @FXML private Button disconnectDriveBtn;
    @FXML private Button uploadDriveBtn;
    @FXML private Button downloadDriveBtn;
    @FXML private Label driveStatusLabel;
    @FXML private ProgressIndicator driveProgress;

    @FXML private TextArea integrityReportArea;

    private interface BackgroundAction {
        String run() throws Exception;
    }

    @FXML
    public void initialize() {
        setupTable();
        loadUsers();

        localProgress.setVisible(false);
        driveProgress.setVisible(false);
        refreshDriveButtons();
    }

    private void setupTable() {
        idColumn.setCellValueFactory(new PropertyValueFactory<>("id"));
        usernameColumn.setCellValueFactory(new PropertyValueFactory<>("username"));
        roleColumn.setCellValueFactory(new PropertyValueFactory<>("role"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void loadUsers() {
        List<User> users = userService.getAllUsers();
        usersTable.setItems(FXCollections.observableArrayList(users));
    }

    @FXML
    public void handleActivateUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a user");
            return;
        }

        try {
            userService.updateUserStatus(selected.getId(), User.UserStatus.active);
            loadUsers();
            showAlert("Success", "User activated successfully");
        } catch (Exception e) {
            showAlert("Error", "Failed to activate user: " + e.getMessage());
        }
    }

    @FXML
    public void handleDeactivateUser() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a user");
            return;
        }

        try {
            userService.updateUserStatus(selected.getId(), User.UserStatus.inactive);
            loadUsers();
            showAlert("Success", "User deactivated successfully");
        } catch (Exception e) {
            showAlert("Error", "Failed to deactivate user: " + e.getMessage());
        }
    }

    @FXML
    public void handleSetSecurityQuestion() {
        User selected = usersTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("Error", "Please select a user");
            return;
        }
        String question = securityQuestionField.getText().trim();
        String answer = securityAnswerField.getText().trim();
        if (question.isEmpty() || answer.isEmpty()) {
            showAlert("Error", "Security question and answer are required");
            return;
        }
        try {
            userService.setSecurityQuestion(selected.getId(), question, answer);
            securityQuestionField.clear();
            securityAnswerField.clear();
            showAlert("Success", "Security question set for '" + selected.getUsername() + "'");
        } catch (Exception e) {
            showAlert("Error", "Failed to set security question: " + e.getMessage());
        }
    }

    @FXML
    public void handleExportLocal() {
        Window owner = exportLocalBtn.getScene().getWindow();
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder to save the backup");
        File folder = chooser.showDialog(owner);
        if (folder == null) return;

        if (!confirm("Export your data as a backup .sql file to:\n" + folder.getAbsolutePath() + "\n\nContinue?")) return;

        runInBackground(localProgress, localStatusLabel, new Button[]{exportLocalBtn, importLocalBtn},
                () -> "Backup saved to " + backupService.exportToLocal(folder));
    }

    @FXML
    public void handleImportLocal() {
        Window owner = importLocalBtn.getScene().getWindow();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a .sql backup file to import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQL files", "*.sql"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        if (!confirm("This will overwrite your current data by importing:\n" + file.getAbsolutePath() + "\n\nContinue?")) return;

        runInBackground(localProgress, localStatusLabel, new Button[]{exportLocalBtn, importLocalBtn},
                () -> backupService.importFromLocal(file));
    }

    @FXML
    public void handleConnectDrive() {
        Timeline countdown = startConnectCountdown();
        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> { googleDriveService.connect(); return "Connected to Google Drive. " + googleDriveService.getLatestBackupDisplay(); },
                countdown);
    }

    /**
     * Shows a 1-minute countdown in the status label while waiting for the browser sign-in.
     * If the user abandons the browser tab, the service-side timeout in GoogleDriveServiceImpl
     * gives up after the same duration and frees the local callback port for the next attempt.
     */
    private Timeline startConnectCountdown() {
        final int timeoutSeconds = 60;
        AtomicInteger remaining = new AtomicInteger(timeoutSeconds);
        driveStatusLabel.getStyleClass().removeAll("backup-status-success", "backup-status-error");
        driveStatusLabel.setText("Waiting for Google sign-in in your browser... " + timeoutSeconds + "s");

        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int left = remaining.decrementAndGet();
            driveStatusLabel.setText(left > 0
                    ? "Waiting for Google sign-in in your browser... " + left + "s"
                    : "Waiting for Google sign-in in your browser...");
        }));
        timeline.setCycleCount(timeoutSeconds);
        timeline.play();
        return timeline;
    }

    @FXML
    public void handleDisconnectDrive() {
        if (!confirm("Log out of the currently connected Google Drive account? "
                + "You'll need to sign in again to upload or download backups.\n\nContinue?")) return;

        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> { googleDriveService.disconnect(); return "Logged out of Google Drive"; });
    }

    @FXML
    public void handleUploadDrive() {
        if (!confirm("This will export your current data and upload it to the 'finance_app' folder in your connected Google Drive account.\n\nContinue?")) return;

        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    String localPath = backupService.exportToLocal(tempDir);
                    String uploadResult = googleDriveService.upload(new File(localPath));
                    return uploadResult + ". " + googleDriveService.getLatestBackupDisplay();
                });
    }

    @FXML
    public void handleDownloadDrive() {
        if (!confirm("This will download the most recent backup from the 'finance_app' folder in your Google Drive "
                + "and use it to overwrite your current local database.\n\nContinue?")) return;

        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File downloaded = googleDriveService.downloadLatest(tempDir);
                    return backupService.importFromLocal(downloaded);
                });
    }

    private void runInBackground(ProgressIndicator progress, Label statusLabel, Button[] buttons, BackgroundAction action) {
        runInBackground(progress, statusLabel, buttons, action, null);
    }

    private void runInBackground(ProgressIndicator progress, Label statusLabel, Button[] buttons, BackgroundAction action, Timeline countdown) {
        if (countdown == null) {
            statusLabel.setText("");
            statusLabel.getStyleClass().removeAll("backup-status-success", "backup-status-error");
        }
        progress.setVisible(true);
        for (Button b : buttons) b.setDisable(true);

        Task<String> task = new Task<>() {
            @Override
            protected String call() throws Exception {
                return action.run();
            }
        };

        task.setOnSucceeded(e -> {
            if (countdown != null) countdown.stop();
            progress.setVisible(false);
            for (Button b : buttons) b.setDisable(false);
            refreshDriveButtons();
            statusLabel.getStyleClass().removeAll("backup-status-success", "backup-status-error");
            statusLabel.setText(task.getValue());
            statusLabel.getStyleClass().add("backup-status-success");
        });

        task.setOnFailed(e -> {
            if (countdown != null) countdown.stop();
            progress.setVisible(false);
            for (Button b : buttons) b.setDisable(false);
            refreshDriveButtons();
            Throwable ex = task.getException();
            statusLabel.getStyleClass().removeAll("backup-status-success", "backup-status-error");
            statusLabel.setText(ex != null && ex.getMessage() != null ? ex.getMessage() : "Operation failed");
            statusLabel.getStyleClass().add("backup-status-error");
        });

        Thread thread = new Thread(task, "backup-restore-task");
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    public void handleVerifyData() {
        List<String> issues = dataIntegrityService.runFullCheck();
        integrityReportArea.setManaged(true);
        integrityReportArea.setVisible(true);
        if (issues.isEmpty()) {
            integrityReportArea.setText("All checks passed - no issues found.");
        } else {
            integrityReportArea.setText(String.join("\n\n", issues));
        }
    }

    private void refreshDriveButtons() {
        boolean connected = googleDriveService.isConnected();
        connectDriveBtn.setDisable(connected);
        disconnectDriveBtn.setDisable(!connected);
        uploadDriveBtn.setDisable(!connected);
        downloadDriveBtn.setDisable(!connected);
    }

    private boolean confirm(String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}