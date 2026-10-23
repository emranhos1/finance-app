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

    /** Called from the Dashboard's loan reminder toast so clicking it jumps straight to the
     *  Loans sub-tab (index 2 of Transaction/Transfer/Loans) rather than just the Transaction page. */
    public void goToLoansTab() {
        loadView("/fxml/Transactions.fxml");
        setActiveButton(transactionBtn);
        TabPane tabPane = (TabPane) mainBorderPane.getCenter().lookup("#mainTabPane");
        if (tabPane != null) tabPane.getSelectionModel().select(2);
    }

    /** Called from the Dashboard's FDR/DPS maturity reminder toast so clicking it jumps to the Accounts tab. */
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

        // Validate and save inside the Save button's event filter: consuming the event keeps the
        // dialog open on an error, so the user can fix the field instead of reopening and retyping.
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
        // A Dialog refuses dialog.close() unless it has a cancel-type button, so the Cancel button
        // below did nothing. Add one and hide it - the popup's own buttons do the closing.
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
        // Undecorated dialogs have no native title bar, border or shadow, so without an explicit
        // border here the popup visually blends into whatever page is open behind it.
        content.setStyle("-fx-background-color: white; -fx-border-color: #1B5446; -fx-border-width: 2; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; "
                + "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 20, 0.3, 0, 6);");
        content.setPrefWidth(380);
        dialog.getDialogPane().setContent(content);

        showBackupPrompt(dialog, content);

        dialog.show();
    }

    /** Asks every time whether to back up before logging out, rather than deciding silently based
     *  on the auto-backup interval - the user wants to be asked and choose, not have the app skip
     *  it quietly because it decided a backup wasn't "due" yet. */
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
        // stops waiting for the sign-in: the interrupt ends the sign-in attempt, which frees port 8888
        // for the next try. A cancelled task runs neither handler above, so this never logs out.
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
        // cancels the backup (nothing is uploaded unless the upload had already started) and stays
        // logged in - a cancelled task runs neither the success nor the failure handler
        cancelBtn.setOnAction(e -> {
            backupTask.cancel(true);
            dialog.close();
        });
        backupTask.setOnSucceeded(e -> {
            cancelBtn.setDisable(true); // already done - logging out now
            statusLabel.setText("Now logging out...");
            PauseTransition pause = new PauseTransition(Duration.millis(700));
            pause.setOnFinished(ev -> closeDialogThenLogout(dialog));
            pause.play();
        });
        backupTask.setOnFailed(e -> {
            Throwable ex = backupTask.getException();
            String reason = ex != null && ex.getMessage() != null ? ex.getMessage() : "Unknown error";
            // a missing MySQL tool has nothing to do with the internet or Drive - don't send the user there
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

    /** Swaps the popup's content for the next step and resizes the popup to fit it. A Dialog does not
     *  resize itself when its content changes after it was first shown, so a longer step (e.g. a
     *  backup error) would otherwise be cut off at the bottom, with its lines overlapping. */
    private void showStep(Dialog<Void> dialog, VBox content, Node... nodes) {
        content.getChildren().setAll(nodes);
        fitToContent(dialog, content);
    }

    private void fitToContent(Dialog<Void> dialog, VBox content) {
        Window window = dialog.getDialogPane().getScene().getWindow();
        // before the first show(), the dialog sizes and centers itself
        if (window == null || !window.isShowing()) return;
        dialog.getDialogPane().applyCss();
        // A window sizes to its scene by measuring heights without a width, which measures every
        // wrapped label as one long line - the popup came out too short and longer text was cut off.
        // Measure the content at its real width and pin that height before sizing the window.
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
        // the reason is the raw mysqldump / Google message and can be very long - scroll it instead of
        // letting it push the buttons off the popup
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

        // The scroll box would otherwise show only as many lines as an unwrapped label needs. Size it
        // to the wrapped message (now that its font is known), up to 160px - longer text scrolls.
        Insets in = content.getInsets();
        double labelWidth = content.getPrefWidth() - in.getLeft() - in.getRight() - 20; // room for the scroll bar
        reasonScroll.setPrefViewportHeight(Math.min(160, reasonLabel.prefHeight(labelWidth)));
        fitToContent(dialog, content);
    }

    /** Closes the popup without logging out, so the user is never stuck in it (it is modal). */
    private Button cancelButton(Dialog<Void> dialog) {
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("btn-secondary");
        cancelBtn.setOnAction(e -> dialog.close());
        return cancelBtn;
    }

    /** Closing this dialog and then immediately resizing/re-showing its owner Stage (which is
     *  what doLogout()'s showLoginScreen() does) in the very same event can race with Windows
     *  still tearing down the modal dialog's native window, leaving stale pixels from both
     *  windows on screen and the app looking frozen. Deferring doLogout() to the next pulse via
     *  Platform.runLater gives the dialog's close a full pulse to finish first. */
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