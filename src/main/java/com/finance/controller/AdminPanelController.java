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
    @FXML private Label driveAccountLabel;
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
        com.finance.util.UiFormat.chipColumn(statusColumn, s -> com.finance.util.UiFormat.capitalize(s.name()), s -> s == User.UserStatus.active ? "chip-income" : "chip-closed");
        com.finance.util.UiFormat.chipColumn(roleColumn, r -> com.finance.util.UiFormat.capitalize(r.name()), r -> r == User.UserRole.admin ? "chip-transfer" : null);
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
                () -> { googleDriveService.connect(); return "Connected to Google Drive (" + googleDriveService.getConnectedEmail() + "). " + googleDriveService.getLatestBackupDisplay(); },
                countdown);
    }

    /** 1-minute countdown while waiting for sign-in; the service times out after the same time. */
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
        if (!confirmFullWidth("This will export your current data and upload it to the 'finance_app' folder in your connected Google Drive account:\n",
                String.valueOf(googleDriveService.getConnectedEmail()), "\n\nContinue?")) return;

        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File dump = new File(backupService.exportToLocal(tempDir));
                    try {
                        String uploadResult = googleDriveService.upload(dump);
                        return uploadResult + " - account: " + googleDriveService.getConnectedEmail()
                                + ". " + googleDriveService.getLatestBackupDisplay();
                    } finally {
                        dump.delete();
                    }
                });
    }

    @FXML
    public void handleDownloadDrive() {
        if (!confirmFullWidth("This will download the most recent backup from the 'finance_app' folder in your Google Drive account:\n",
                String.valueOf(googleDriveService.getConnectedEmail()),
                "\n\nand use it to overwrite your current local database.\n\nContinue?")) return;

        runInBackground(driveProgress, driveStatusLabel,
                new Button[]{connectDriveBtn, disconnectDriveBtn, uploadDriveBtn, downloadDriveBtn},
                () -> {
                    File tempDir = new File(System.getProperty("java.io.tmpdir"));
                    File downloaded = googleDriveService.downloadLatest(tempDir);
                    try {
                        return backupService.importFromLocal(downloaded)
                                + " (Google Drive: " + googleDriveService.getConnectedEmail() + ")";
                    } finally {
                        downloaded.delete();
                    }
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
        // connected Google account: bigger and bold, shown only while connected
        if (driveAccountLabel != null) {
            String email = connected ? googleDriveService.getConnectedEmail() : null;
            boolean show = email != null && !email.isEmpty();
            driveAccountLabel.setText(show ? "Google account: " + email : "");
            driveAccountLabel.setVisible(show);
            driveAccountLabel.setManaged(show);
        }
    }

    private boolean confirm(String message) {
        return com.finance.util.AppDialogs.confirm(message);
    }

    /** Confirm with an own OK / Cancel row: equal width, side by side, full dialog width (standard button bar hidden). */
    private boolean confirmFullWidth(String before, String account, String after) {
        Alert alert = new com.finance.util.AppAlert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirm");
        alert.setHeaderText(null);
        alert.setGraphic(null);
        alert.getDialogPane().setMinWidth(560);
        alert.getDialogPane().setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        // the Google account is bigger and bold
        javafx.scene.text.Text t1 = new javafx.scene.text.Text(before);
        javafx.scene.text.Text t2 = new javafx.scene.text.Text(account);
        javafx.scene.text.Text t3 = new javafx.scene.text.Text(after);
        t1.setStyle("-fx-fill: #1c2b26;");
        t3.setStyle("-fx-fill: #1c2b26;");
        t2.setStyle("-fx-fill: #14463a; -fx-font-weight: bold; -fx-font-size: 16px;");
        javafx.scene.text.TextFlow msg = new javafx.scene.text.TextFlow(t1, t2, t3);
        msg.setPrefWidth(520);   // fixed width: an unconstrained wrapped text made the dialog huge
        msg.setMaxWidth(520);

        Button okBtn = new Button("OK");
        okBtn.getStyleClass().add("btn-primary");
        okBtn.setDefaultButton(true);
        okBtn.setOnAction(e -> alert.setResult(ButtonType.OK));
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-secondary", "btn-reset");
        cancelBtn.setCancelButton(true);
        cancelBtn.setOnAction(e -> alert.setResult(ButtonType.CANCEL));
        javafx.scene.layout.HBox row = new javafx.scene.layout.HBox(10, okBtn, cancelBtn);
        for (Button b : new Button[]{okBtn, cancelBtn}) {
            b.setPrefWidth(150);
            b.setMaxWidth(Double.MAX_VALUE);
            javafx.scene.layout.HBox.setHgrow(b, javafx.scene.layout.Priority.ALWAYS);
        }

        com.finance.util.DialogChrome.hideStandardButtons(alert);
        alert.getDialogPane().setContent(new javafx.scene.layout.VBox(14, msg, row));
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private void showAlert(String title, String message) {
        com.finance.util.AppDialogs.info(title, message);
    }
}