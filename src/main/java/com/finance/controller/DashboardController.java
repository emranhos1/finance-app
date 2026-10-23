package com.finance.controller;

import com.finance.FinanceApplication;
import com.finance.context.SessionContext;
import com.finance.service.AutoBackupService;
import com.finance.service.IGoogleDriveService;
import com.finance.service.UserService;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Controller
@RequiredArgsConstructor
public class DashboardController {
    private final SessionContext sessionContext;
    private final UserService userService;
    private final AutoBackupService autoBackupService;
    private final IGoogleDriveService googleDriveService;

    @FXML
    private BorderPane mainBorderPane;

    @FXML
    private Button dashboardBtn;

    @FXML
    private Button accountsBtn;

    @FXML
    private Button transactionBtn;

    @FXML
    private Button reportsBtn;

    @FXML
    private Button calculatorBtn;

    @FXML
    private Button adminBtn;

    @FXML
    private Button changePasswordBtn;

    @FXML
    private Button logoutBtn;

    @FXML
    public void initialize() {
        dashboardBtn.setOnAction(e -> { loadView("/fxml/DashboardHome.fxml"); setActiveButton(dashboardBtn); });
        accountsBtn.setOnAction(e -> { loadView("/fxml/Accounts.fxml"); setActiveButton(accountsBtn); });
        transactionBtn.setOnAction(e -> { loadView("/fxml/Transactions.fxml"); setActiveButton(transactionBtn); });
        reportsBtn.setOnAction(e -> { loadView("/fxml/Reports.fxml"); setActiveButton(reportsBtn); });
        calculatorBtn.setOnAction(e -> { loadView("/fxml/Calculator.fxml"); setActiveButton(calculatorBtn); });
        changePasswordBtn.setOnAction(e -> handleChangePassword());
        logoutBtn.setOnAction(e -> handleLogout());

        if (!sessionContext.isAdmin()) {
            adminBtn.setVisible(false);
            adminBtn.setManaged(false);
        } else {
            adminBtn.setOnAction(e -> { loadView("/fxml/AdminPanel.fxml"); setActiveButton(adminBtn); });
        }

        loadView("/fxml/DashboardHome.fxml");
        setActiveButton(dashboardBtn);
    }

    /** Opens the Loans sub-tab (index 2) from the loan reminder toast. */
    public void goToLoansTab() {
        loadView("/fxml/Transactions.fxml");
        setActiveButton(transactionBtn);
        TabPane tabPane = (TabPane) mainBorderPane.getCenter().lookup("#mainTabPane");
        if (tabPane != null) tabPane.getSelectionModel().select(2);
    }

    public void goToAccountsTab() {
        loadView("/fxml/Accounts.fxml");
        setActiveButton(accountsBtn);
    }

    private void setActiveButton(Button active) {
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionBtn, reportsBtn, calculatorBtn, adminBtn}) {
            b.getStyleClass().remove("sidebar-btn-active");
        }
        active.getStyleClass().add("sidebar-btn-active");
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent view = loader.load();
            mainBorderPane.setCenter(view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleChangePassword() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Change your password");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 10, 10));

        PasswordField oldPasswordField = new PasswordField();
        oldPasswordField.setPromptText("Current password");
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm new password");

        grid.add(new Label("Current Password:"), 0, 0);
        grid.add(oldPasswordField, 1, 0);
        grid.add(new Label("New Password:"), 0, 1);
        grid.add(newPasswordField, 1, 1);
        grid.add(new Label("Confirm Password:"), 0, 2);
        grid.add(confirmPasswordField, 1, 2);

        dialog.getDialogPane().setContent(grid);

        // consume the event on error so the dialog stays open
        boolean[] changed = {false};
        Button saveButton = (Button) dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            String newPass = newPasswordField.getText();
            if (newPass == null || newPass.isEmpty()) {
                showAlert("Validation Error", "New password cannot be empty");
                event.consume();
                return;
            }
            if (!newPass.equals(confirmPasswordField.getText())) {
                showAlert("Validation Error", "New password and confirmation do not match");
                event.consume();
                return;
            }
            try {
                userService.changePassword(sessionContext.getCurrentUserId(), oldPasswordField.getText(), newPass);
                changed[0] = true;
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
                event.consume();
            }
        });

        dialog.showAndWait();
        if (changed[0]) {
            showAlert("Success", "Password changed successfully");
        }
    }

    private void showAlert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title); a.setHeaderText(null); a.setContentText(msg); a.showAndWait();
    }

    private void handleLogout() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(logoutBtn.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.getDialogPane().getButtonTypes().clear();
        // Dialog only closes with a cancel-type button, so add a hidden one
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node hiddenCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        hiddenCancel.setVisible(false);
        hiddenCancel.setManaged(false);
        dialog.getDialogPane().setStyle("-fx-background-color: transparent;");
        if (getClass().getResource("/css/main.css") != null) {
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/main.css").toExternalForm());
        }

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        // undecorated dialog has no border, so it blends into the page behind
        content.setStyle("-fx-background-color: white; -fx-border-color: #1B5446; -fx-border-width: 2; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 20, 0.3, 0, 6);");
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        showBackupPrompt(dialog, content);

        dialog.show();
    }

    /** Always asks about backup before logout, not based on the auto-backup interval. */
    private void showBackupPrompt(Dialog<Void> dialog, VBox content) {
        Label label = new Label("Back up your data to Google Drive before logging out?");
        label.setWrapText(true);

        Button yesBtn = new Button("Backup Now");
        yesBtn.getStyleClass().add("btn-primary");
        Button noBtn = new Button("Skip & Logout");
        noBtn.getStyleClass().add("btn-secondary");
        yesBtn.setOnAction(e -> {
            if (autoBackupService.hasDriveConnection()) showBackingUp(dialog, content);
            else showNeedsConnect(dialog, content);
        });
        noBtn.setOnAction(e -> closeDialogThenLogout(dialog));

        showStep(dialog, content, label, new HBox(10, yesBtn, noBtn, cancelButton(dialog)));
    }

    private void showNeedsConnect(Dialog<Void> dialog, VBox content) {
        Label label = new Label("Google Drive is not connected on this computer. "
                + "Connect it now so this session's data can be backed up before logging out.");
        label.setWrapText(true);

        Button connectBtn = new Button("Connect Google Drive");
        connectBtn.getStyleClass().add("btn-primary");
        Button skipBtn = new Button("Skip Backup & Logout");
        skipBtn.getStyleClass().add("btn-secondary");
        connectBtn.setOnAction(e -> showConnecting(dialog, content));
        skipBtn.setOnAction(e -> closeDialogThenLogout(dialog));

        showStep(dialog, content, label, new HBox(10, connectBtn, skipBtn), cancelButton(dialog));
    }

    private void showConnecting(Dialog<Void> dialog, VBox content) {
        final int timeoutSeconds = 60;
        AtomicInteger remaining = new AtomicInteger(timeoutSeconds);
        Label statusLabel = new Label("Waiting for Google sign-in in your browser... " + timeoutSeconds + "s");
        statusLabel.setWrapText(true);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        showStep(dialog, content, new HBox(12, new ProgressIndicator(), statusLabel), cancelBtn);

        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            int left = remaining.decrementAndGet();
            if (left > 0) statusLabel.setText("Waiting for Google sign-in in your browser... " + left + "s");
        }));
        countdown.setCycleCount(timeoutSeconds);
        countdown.play();

        Task<Void> connectTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                googleDriveService.connect();
                return null;
            }
        };
        connectTask.setOnSucceeded(e -> { countdown.stop(); showBackingUp(dialog, content); });
        connectTask.setOnFailed(e -> {
            countdown.stop();
            Throwable ex = connectTask.getException();
            String reason = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            showFailure(dialog, content,
                    "Could not connect to Google Drive: " + reason,
                    "Check your internet connection and make sure you complete the sign-in in the browser tab that opened, then try again.",
                    () -> showConnecting(dialog, content));
        });
        // interrupt ends the sign-in attempt and frees port 8888; a cancelled task never logs out
        cancelBtn.setOnAction(e -> {
            countdown.stop();
            connectTask.cancel(true);
            dialog.close();
        });

        Thread thread = new Thread(connectTask, "logout-drive-connect");
        thread.setDaemon(true);
        thread.start();
    }

    private void showBackingUp(Dialog<Void> dialog, VBox content) {
        Label statusLabel = new Label("Backing up your data...");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        showStep(dialog, content, new HBox(12, new ProgressIndicator(), statusLabel), cancelBtn);

        Task<Void> backupTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                autoBackupService.performBackup();
                return null;
            }
        };
        // cancel stops the backup and stays logged in
        cancelBtn.setOnAction(e -> {
            backupTask.cancel(true);
            dialog.close();
        });
        backupTask.setOnSucceeded(e -> {
            cancelBtn.setDisable(true);
            statusLabel.setText("Now logging out...");
            PauseTransition pause = new PauseTransition(Duration.millis(700));
            pause.setOnFinished(ev -> closeDialogThenLogout(dialog));
            pause.play();
        });
        backupTask.setOnFailed(e -> {
            Throwable ex = backupTask.getException();
            String reason = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            // a missing MySQL tool is not an internet/Drive problem
            boolean mysqlToolMissing = reason.contains(".exe was not found") || reason.startsWith("Could not start mysql");
            showFailure(dialog, content,
                    "Backup failed: " + reason,
                    mysqlToolMissing
                            ? "The app could not find MySQL's mysqldump.exe on this PC. Add the 'bin' folder of your "
                                    + "MySQL installation to the system PATH, then try again. Your local data is safe either way."
                            : "Check your internet connection and that Google Drive access hasn't been revoked "
                                    + "(reconnect from Admin Panel if needed), then try again. Your local data is safe either way.",
                    () -> showBackingUp(dialog, content));
        });

        Thread thread = new Thread(backupTask, "logout-backup");
        thread.setDaemon(true);
        thread.start();
    }

    /** Swaps the popup content and resizes it; a shown Dialog does not resize itself. */
    private void showStep(Dialog<Void> dialog, VBox content, Node... nodes) {
        content.getChildren().setAll(nodes);
        fitToContent(dialog, content);
    }

    private void fitToContent(Dialog<Void> dialog, VBox content) {
        Window window = dialog.getDialogPane().getScene().getWindow();
        if (window == null || !window.isShowing()) return;
        dialog.getDialogPane().applyCss();
        // measure at the real width, otherwise wrapped labels get cut off
        content.setPrefHeight(Region.USE_COMPUTED_SIZE);
        content.setPrefHeight(content.prefHeight(content.getPrefWidth()));
        dialog.getDialogPane().layout();
        window.sizeToScene();
        Window owner = dialog.getOwner();
        if (owner != null) {
            window.setX(owner.getX() + (owner.getWidth() - window.getWidth()) / 2);
            window.setY(owner.getY() + (owner.getHeight() - window.getHeight()) / 2);
        }
    }

    private void showFailure(Dialog<Void> dialog, VBox content, String reason, String suggestion, Runnable retryAction) {
        Label reasonLabel = new Label(reason);
        reasonLabel.setWrapText(true);
        reasonLabel.getStyleClass().add("backup-status-error");
        // reason can be a very long mysqldump/Google message, so scroll it
        ScrollPane reasonScroll = new ScrollPane(reasonLabel);
        reasonScroll.setFitToWidth(true);
        reasonScroll.setStyle("-fx-background-color: transparent; -fx-background: white;");

        Label suggestionLabel = new Label("Suggestion: " + suggestion);
        suggestionLabel.setWrapText(true);

        Button retryBtn = new Button("Retry");
        retryBtn.getStyleClass().add("btn-primary");
        Button connectBtn = new Button("Connect Google Drive");
        connectBtn.getStyleClass().add("btn-secondary");
        Button skipBtn = new Button("Skip Backup & Logout");
        skipBtn.getStyleClass().add("btn-secondary");
        retryBtn.setOnAction(e -> retryAction.run());
        connectBtn.setOnAction(e -> showConnecting(dialog, content));
        skipBtn.setOnAction(e -> closeDialogThenLogout(dialog));

        showStep(dialog, content, reasonScroll, suggestionLabel,
                new HBox(10, retryBtn, connectBtn), new HBox(10, skipBtn, cancelButton(dialog)));

        // fit the scroll box to the wrapped message, max 160px
        Insets in = content.getInsets();
        double labelWidth = content.getPrefWidth() - in.getLeft() - in.getRight() - 20; // room for the scroll bar
        reasonScroll.setPrefViewportHeight(Math.min(160, reasonLabel.prefHeight(labelWidth)));
        fitToContent(dialog, content);
    }

    private Button cancelButton(Dialog<Void> dialog) {
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> dialog.close());
        return cancelBtn;
    }

    /** Run doLogout() on the next pulse so the dialog window finishes closing first (stale pixels otherwise). */
    private void closeDialogThenLogout(Dialog<Void> dialog) {
        dialog.close();
        Platform.runLater(this::doLogout);
    }

    private void doLogout() {
        sessionContext.logout();
        try {
            FinanceApplication.showLoginScreen();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}