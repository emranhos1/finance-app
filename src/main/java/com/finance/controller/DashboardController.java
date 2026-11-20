package com.finance.controller;

import com.finance.FinanceApplication;
import com.finance.context.SessionContext;
import com.finance.service.AutoBackupService;
import com.finance.service.IGoogleDriveService;
import com.finance.service.UserService;
import com.finance.theme.ThemeManager;
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
import javafx.scene.Scene;
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
    private final com.finance.service.UpdateService updateService;

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
    private Button themeBtn;

    @FXML
    private Button changePasswordBtn;

    @FXML
    private Button logoutBtn;

    /** Blinking "update available" item under Admin Panel; optional, a layout may leave it out. */
    @FXML private Button updateBtn;
    @FXML private javafx.scene.control.ProgressBar updateProgressBar;
    private javafx.animation.Timeline updateBlink;
    /** Current app version under Logout; hidden only while the sidebar is collapsed. */
    @FXML private Label versionLabel;

    private void refreshVersionLabel() {
        if (versionLabel == null) return;
        String v = updateService.getCurrentVersion();
        boolean known = v != null && !v.isBlank() && !v.contains("@");
        versionLabel.setText(known ? "Version " + v : "");
        boolean show = known && !sidebarCollapsed;
        versionLabel.setVisible(show);
        versionLabel.setManaged(show);
    }

    private void showUpdateProgress(boolean show) {
        if (updateProgressBar == null) return;
        updateProgressBar.setVisible(show);
        updateProgressBar.setManaged(show);
        if (show) updateProgressBar.setProgress(javafx.scene.control.ProgressBar.INDETERMINATE_PROGRESS);
    }

    private void setupUpdateButton() {
        if (updateBtn == null) return;
        if (updateBlink != null) { updateBlink.stop(); updateBlink = null; }
        com.finance.service.UpdateService.CheckResult known = updateService.getLastResult();
        if (known != null) {
            showUpdateItem(known);
            return;
        }
        // the Login check has not finished yet (or never ran): check in the background, a slow connection never blocks
        Thread check = new Thread(() -> {
            com.finance.service.UpdateService.CheckResult result = updateService.checkForUpdate();
            javafx.application.Platform.runLater(() -> showUpdateItem(result));
        }, "update-check-dashboard");
        check.setDaemon(true);
        check.start();
    }

    private void showUpdateItem(com.finance.service.UpdateService.CheckResult result) {
        if (updateBtn == null || result == null
                || result.getStatus() != com.finance.service.UpdateService.CheckStatus.AVAILABLE) return;
        com.finance.service.UpdateService.ReleaseInfo release = result.getRelease();
        String label = "Update to " + release.getVersion();
        updateBtn.setText(label);
        updateBtn.setVisible(true);
        updateBtn.setManaged(true);
        applySidebarState();   // keeps the collapsed look (icon only + tooltip) right for the new item

        // fade in and out until the user clicks it
        updateBlink = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(updateBtn.opacityProperty(), 1.0)),
                new javafx.animation.KeyFrame(javafx.util.Duration.millis(700),
                        new javafx.animation.KeyValue(updateBtn.opacityProperty(), 0.35)));
        updateBlink.setAutoReverse(true);
        updateBlink.setCycleCount(javafx.animation.Animation.INDEFINITE);
        updateBlink.play();
        // stop when the item leaves the window (logout, shell rebuild) so no animation keeps running in the background
        javafx.animation.Timeline blink = updateBlink;
        updateBtn.sceneProperty().addListener((o, a, b) -> { if (b == null) blink.stop(); });

        updateBtn.setOnAction(e -> {
            // ask first: after the download the app closes and the installer starts
            if (!com.finance.util.AppDialogs.confirm("Update to " + release.getVersion() + "?\n\n"
                    + "The app will download the new version, then close and start the installer. "
                    + "Your data is not touched.\n\nContinue?")) return;
            Button btn = updateBtn;
            com.finance.util.UpdateFlow.start(btn.getScene().getWindow(), updateService, release,
                    () -> {
                        blink.stop();
                        btn.setOpacity(1.0);
                        btn.setDisable(true);
                        btn.setText("Updating 0%");
                        showUpdateProgress(true);
                    },
                    p -> {
                        // floor, so it never shows 100% early
                        btn.setText("Updating " + (int) Math.floor(p * 100) + "%");
                        if (updateProgressBar != null) updateProgressBar.setProgress(p);
                    },
                    () -> {
                        btn.setDisable(false);
                        btn.setText(label);
                        showUpdateProgress(false);
                        blink.play();
                    });
        });
    }

    // sidebar collapse: optional, a layout may leave any of these out
    @FXML private VBox sidebarVBox;
    @FXML private Node sidebarLogoTile;
    @FXML private Node sidebarLogoText;
    @FXML private Node sidebarSpacer;
    @FXML private Button sidebarToggleBtn;
    /** Kept in the controller so a shell rebuild (theme change) keeps the choice. */
    private boolean sidebarCollapsed;

    private void applySidebarState() {
        if (sidebarVBox == null) return;
        sidebarVBox.getStyleClass().remove("sidebar-collapsed");
        if (sidebarCollapsed) sidebarVBox.getStyleClass().add("sidebar-collapsed");
        for (Node n : new Node[]{sidebarLogoTile, sidebarLogoText, sidebarSpacer}) {
            if (n != null) { n.setVisible(!sidebarCollapsed); n.setManaged(!sidebarCollapsed); }
        }
        refreshVersionLabel();
        for (Node n : sidebarVBox.getChildren()) {
            if (n instanceof Button && n.getStyleClass().contains("sidebar-btn")) {
                Button b = (Button) n;
                b.setContentDisplay(sidebarCollapsed ? javafx.scene.control.ContentDisplay.GRAPHIC_ONLY
                        : javafx.scene.control.ContentDisplay.LEFT);
                b.setTooltip(sidebarCollapsed ? new javafx.scene.control.Tooltip(b.getText()) : null);
            }
        }
    }

    @FXML
    public void initialize() {
        if (sidebarToggleBtn != null) {
            sidebarToggleBtn.setOnAction(e -> { sidebarCollapsed = !sidebarCollapsed; applySidebarState(); });
        }
        applySidebarState();
        dashboardBtn.setOnAction(e -> { loadView("/fxml/DashboardHome.fxml"); setActiveButton(dashboardBtn); });
        accountsBtn.setOnAction(e -> { loadView("/fxml/Accounts.fxml"); setActiveButton(accountsBtn); });
        transactionBtn.setOnAction(e -> { loadView("/fxml/Transactions.fxml"); setActiveButton(transactionBtn); });
        reportsBtn.setOnAction(e -> { loadView("/fxml/Reports.fxml"); setActiveButton(reportsBtn); });
        calculatorBtn.setOnAction(e -> { loadView("/fxml/Calculator.fxml"); setActiveButton(calculatorBtn); });
        themeBtn.setOnAction(e -> { loadView("/fxml/Theme.fxml"); setActiveButton(themeBtn); });
        changePasswordBtn.setOnAction(e -> handleChangePassword());
        logoutBtn.setOnAction(e -> handleLogout());

        if (!sessionContext.isAdmin()) {
            adminBtn.setVisible(false);
            adminBtn.setManaged(false);
        } else {
            adminBtn.setOnAction(e -> { loadView("/fxml/AdminPanel.fxml"); setActiveButton(adminBtn); });
        }

        setupUpdateButton();

        loadView("/fxml/DashboardHome.fxml");
        setActiveButton(dashboardBtn);

        ThemeManager.setOnThemeChanged(this::reloadShell);
    }

    /**
     * The navigation layout belongs to the theme (left sidebar, icon rail, top bar), so a theme change
     * builds the window shell again from the new theme's Dashboard.fxml and reopens the Theme tab.
     */
    private void reloadShell() {
        Scene scene = mainBorderPane.getScene();
        if (scene == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(ThemeManager.resolveFxml("/fxml/Dashboard.fxml")));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            // load() runs initialize() on this same controller, which rebinds every field to the new nodes
            Parent newRoot = loader.load();
            // keep the window's own title bar around the new shell
            scene.setRoot(com.finance.util.WindowChrome.wrap((javafx.stage.Stage) scene.getWindow(), newRoot, true));
            themeBtn.fire();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
        for (Button b : new Button[]{dashboardBtn, accountsBtn, transactionBtn, reportsBtn, calculatorBtn, adminBtn, themeBtn}) {
            b.getStyleClass().remove("sidebar-btn-active");
        }
        active.getStyleClass().add("sidebar-btn-active");
    }

    private void loadView(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(ThemeManager.resolveFxml(fxmlPath)));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent view = loader.load();
            mainBorderPane.setCenter(fxmlPath.endsWith("/Calculator.fxml") ? com.finance.util.UiFormat.scrollable(view) : view);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleChangePassword() {
        Dialog<ButtonType> dialog = new com.finance.util.AppDialog<>();
        dialog.setTitle("Change Password");
        dialog.setHeaderText("Change your password");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 10, 4, 10));

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

        // Own Save / Cancel row: equal halves, from the label's left edge to the field's right edge
        // (the standard button bar is hidden). The dialog stays open on a validation error.
        boolean[] changed = {false};
        Button saveButton = new Button("Save");
        saveButton.getStyleClass().add("btn-primary");
        saveButton.setDefaultButton(true);
        Button cancelButton = new Button("Cancel");
        cancelButton.getStyleClass().addAll("btn-secondary", "btn-reset");
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(e -> dialog.setResult(ButtonType.CANCEL));
        saveButton.setOnAction(event -> {
            String newPass = newPasswordField.getText();
            if (newPass == null || newPass.isEmpty()) {
                showAlert("Validation Error", "New password cannot be empty");
                return;
            }
            if (!newPass.equals(confirmPasswordField.getText())) {
                showAlert("Validation Error", "New password and confirmation do not match");
                return;
            }
            try {
                userService.changePassword(sessionContext.getCurrentUserId(), oldPasswordField.getText(), newPass);
                changed[0] = true;
                dialog.setResult(saveButtonType);
            } catch (Exception e) {
                showAlert("Error", e.getMessage());
            }
        });
        HBox buttonRow = new HBox(10, saveButton, cancelButton);
        for (Button b : new Button[]{saveButton, cancelButton}) {
            b.setPrefWidth(150);
            b.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(b, javafx.scene.layout.Priority.ALWAYS);
        }
        GridPane.setMargin(buttonRow, new Insets(10, 0, 0, 0));
        grid.add(buttonRow, 0, 3, 2, 1);

        com.finance.util.DialogChrome.hideStandardButtons(dialog);
        dialog.getDialogPane().setContent(grid);

        dialog.showAndWait();
        if (changed[0]) {
            showAlert("Success", "Password changed successfully");
        }
    }

    private void showAlert(String title, String msg) {
        com.finance.util.AppDialogs.info(title, msg);
    }

    private void handleLogout() {
        Dialog<Void> dialog = new com.finance.util.AppDialog<>();
        dialog.initOwner(logoutBtn.getScene().getWindow());
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.getDialogPane().getButtonTypes().clear();
        // Dialog only closes with a cancel-type button, so add a hidden one
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CANCEL);
        Node hiddenCancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        hiddenCancel.setVisible(false);
        hiddenCancel.setManaged(false);
        // main.css and the theme are added to the dialog window by ThemeManager

        VBox content = new VBox(14);
        content.setPadding(new Insets(20));
        content.getStyleClass().add("logout-box");
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

        // same grid as the "Backup failed" dialog: equal-width buttons, Cancel full width underneath
        Button cancelBtn = cancelButton(dialog);
        GridPane buttons = new GridPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
        col.setPercentWidth(50);
        buttons.getColumnConstraints().addAll(col, col);
        for (Button b : new Button[]{connectBtn, skipBtn, cancelBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(38);
            GridPane.setHgrow(b, javafx.scene.layout.Priority.ALWAYS);
        }
        buttons.add(connectBtn, 0, 0);
        buttons.add(skipBtn, 1, 0);
        buttons.add(cancelBtn, 0, 1, 2, 1);

        showStep(dialog, content, label, buttons);
    }

    private void showConnecting(Dialog<Void> dialog, VBox content) {
        final int timeoutSeconds = 60;
        AtomicInteger remaining = new AtomicInteger(timeoutSeconds);
        Label statusLabel = new Label("Waiting for Google sign-in in your browser... " + timeoutSeconds + "s");
        statusLabel.setWrapText(true);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-secondary", "btn-reset");
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
        connectTask.setOnSucceeded(e -> { countdown.stop(); showConnected(dialog, content); });
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

    /** After a fresh connect: show which Google account is linked; the backup starts only when the user clicks. */
    private void showConnected(Dialog<Void> dialog, VBox content) {
        Label label = new Label("Connected to Google Drive as:\n" + googleDriveService.getConnectedEmail()
                + "\n\nYour backup will be uploaded to this account.");
        label.setWrapText(true);

        Button backupBtn = new Button("Backup Now");
        backupBtn.getStyleClass().add("btn-primary");
        Button skipBtn = new Button("Skip & Logout");
        skipBtn.getStyleClass().add("btn-secondary");
        backupBtn.setOnAction(e -> showBackingUp(dialog, content));
        skipBtn.setOnAction(e -> closeDialogThenLogout(dialog));

        showStep(dialog, content, label, new HBox(10, backupBtn, skipBtn, cancelButton(dialog)));
    }

    private void showBackingUp(Dialog<Void> dialog, VBox content) {
        Label statusLabel = new Label("Backing up your data...");
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-secondary", "btn-reset");
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
        reasonScroll.setStyle("-fx-background-color: transparent; -fx-background: -c-surface;");

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

        // 2x2 grid, equal-width columns, every button fills its cell so sizes and edges line up
        Button cancelBtn = cancelButton(dialog);
        GridPane buttons = new GridPane();
        buttons.setHgap(10);
        buttons.setVgap(10);
        javafx.scene.layout.ColumnConstraints col = new javafx.scene.layout.ColumnConstraints();
        col.setPercentWidth(50);
        buttons.getColumnConstraints().addAll(col, col);
        int row = 0, colIdx = 0;
        for (Button b : new Button[]{retryBtn, connectBtn, skipBtn, cancelBtn}) {
            b.setMaxWidth(Double.MAX_VALUE);
            b.setMinHeight(38);
            GridPane.setHgrow(b, javafx.scene.layout.Priority.ALWAYS);
            buttons.add(b, colIdx, row);
            if (++colIdx == 2) { colIdx = 0; row++; }
        }

        showStep(dialog, content, reasonScroll, suggestionLabel, buttons);

        // fit the scroll box to the wrapped message, max 160px
        Insets in = content.getInsets();
        double labelWidth = content.getPrefWidth() - in.getLeft() - in.getRight() - 20; // room for the scroll bar
        reasonScroll.setPrefViewportHeight(Math.min(160, reasonLabel.prefHeight(labelWidth)));
        fitToContent(dialog, content);
    }

    private Button cancelButton(Dialog<Void> dialog) {
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().addAll("btn-secondary", "btn-reset");
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