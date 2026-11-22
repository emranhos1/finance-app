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
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.prefs.Preferences;

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

    /** Update check runs in the background so a slow connection never delays login. */
    @FXML
    public void initialize() {
        String version = "Version " + updateService.getCurrentVersion();
        versionLabel.setText(version + "  ·  checking for updates...");
        addThemeMenu();
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
        updateLabel.setText("Update " + release.getVersion() + " available (you have "
                + updateService.getCurrentVersion() + ").");
        updateBanner.setVisible(true);
        updateBanner.setManaged(true);
        // measure at the scene width minus padding; at width 0 the text wraps one letter per line
        Scene scene = updateBanner.getScene();
        Window window = scene != null ? scene.getWindow() : null;
        if (window != null && updateBanner.getParent() instanceof Region) {
            Insets pad = ((Region) updateBanner.getParent()).getPadding();
            double width = scene.getWidth() - pad.getLeft() - pad.getRight();
            double bannerHeight = updateBanner.prefHeight(width);
            double spacing = updateBanner.getParent() instanceof VBox ? ((VBox) updateBanner.getParent()).getSpacing() : 0;
            growWindow(bannerHeight + spacing);
        }
    }

    /** A small button in the top-right corner of the login window: just the theme names, a pick applies it at once. */
    private void addThemeMenu() {
        // with a single theme there is nothing to choose
        if (com.finance.theme.ThemeManager.Theme.values().length < 2) return;
        if (!(versionLabel.getParent() instanceof VBox)) return;
        com.finance.theme.ThemeManager.Theme current = com.finance.theme.ThemeManager.getCurrent();
        javafx.scene.control.MenuButton themeButton = new javafx.scene.control.MenuButton(current.getDisplayName());
        themeButton.getStyleClass().add("login-theme-btn");
        themeButton.setFocusTraversable(false);
        ToggleGroup group = new ToggleGroup();
        for (com.finance.theme.ThemeManager.Theme theme : com.finance.theme.ThemeManager.Theme.values()) {
            javafx.scene.control.RadioMenuItem item = new javafx.scene.control.RadioMenuItem(theme.getDisplayName());
            item.setToggleGroup(group);
            item.setSelected(theme == current);
            item.setOnAction(e -> switchTheme(theme));
            themeButton.getItems().add(item);
        }
        VBox card = (VBox) versionLabel.getParent();
        // top-right corner of the window, floating: not managed, so it never pushes the login fields down
        themeButton.setManaged(false);
        Runnable place = () -> {
            themeButton.applyCss();
            double w = themeButton.prefWidth(-1);
            double h = themeButton.prefHeight(-1);
            themeButton.resize(w, h);
            themeButton.relocate(Math.max(0, card.getWidth() - w - 14), 8);
        };
        card.widthProperty().addListener((o, a, b) -> place.run());
        card.sceneProperty().addListener((o, a, b) -> javafx.application.Platform.runLater(place));
        card.getChildren().add(themeButton);
        javafx.application.Platform.runLater(place);
    }

    /** Saves the pick, restyles every window and rebuilds the login screen, whose layout differs per theme. */
    private void switchTheme(com.finance.theme.ThemeManager.Theme theme) {
        if (theme == com.finance.theme.ThemeManager.getCurrent()) return;
        // a Dashboard from an earlier session may still be registered: nothing to rebuild on the login screen
        com.finance.theme.ThemeManager.setOnThemeChanged(null);
        com.finance.theme.ThemeManager.setTheme(theme);
        try {
            FinanceApplication.showLoginScreen();
        } catch (Exception ex) {
            showAlert("Error", "Could not apply the theme: " + ex.getMessage());
        }
    }

    private double progressBarSpace() {
        return updateProgress.prefHeight(-1) + updateBanner.getSpacing();
    }

    /** Resizes the fixed-size login window, never past the screen height. */
    private void growWindow(double extraHeight) {
        Scene scene = updateBanner.getScene();
        Window window = scene != null ? scene.getWindow() : null;
        if (window == null) return;
        double maxHeight = Screen.getPrimary().getVisualBounds().getHeight();
        window.setHeight(Math.min(window.getHeight() + extraHeight, maxHeight));
    }

    /** English part, then Bangla part after this marker. */
    private static final String BANGLA_MARKER = "<!-- bn -->";
    private static final String NOTES_LANG_KEY = "releaseNotesLanguage";
    private static final String DOWNLOAD_FOLDER_KEY = "updateDownloadFolder";

    @FXML
    public void handleShowReleaseNotes() {
        if (availableUpdate == null) return;
        String body = availableUpdate.getNotes() == null ? "" : availableUpdate.getNotes();
        int marker = body.indexOf(BANGLA_MARKER);

        TextArea notes = new TextArea();
        notes.setEditable(false);
        notes.setWrapText(true);
        notes.setPrefSize(520, 360);
        VBox content = new VBox(10);

        if (marker < 0) {
            notes.setText(body.trim());
        } else {
            String english = body.substring(0, marker).trim();
            String bangla = body.substring(marker + BANGLA_MARKER.length()).trim();
            ToggleGroup group = new ToggleGroup();
            ToggleButton englishBtn = new ToggleButton("English");
            ToggleButton banglaBtn = new ToggleButton("বাংলা");
            englishBtn.setToggleGroup(group);
            banglaBtn.setToggleGroup(group);
            englishBtn.getStyleClass().add("notes-lang");
            banglaBtn.getStyleClass().add("notes-lang");
            Preferences prefs = Preferences.userNodeForPackage(LoginController.class);
            group.selectedToggleProperty().addListener((obs, old, selected) -> {
                if (selected == null) { old.setSelected(true); return; } // keep one language always selected
                boolean bn = selected == banglaBtn;
                notes.setText(bn ? bangla : english);
                prefs.put(NOTES_LANG_KEY, bn ? "bn" : "en");
            });
            (("bn".equals(prefs.get(NOTES_LANG_KEY, "en"))) ? banglaBtn : englishBtn).setSelected(true);
            content.getChildren().add(new HBox(6, englishBtn, banglaBtn));
        }
        content.getChildren().add(notes);

        Alert alert = new com.finance.util.AppAlert(Alert.AlertType.INFORMATION);
        alert.setTitle("What's new in " + availableUpdate.getVersion());
        alert.setHeaderText(null);
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().getStyleClass().add("notes-dialog");
        alert.showAndWait();
    }

    /** Downloads the installer, opens its wizard and closes the app. MySQL data is not touched. */
    @FXML
    public void handleUpdate() {
        if (availableUpdate == null) return;
        // the steps are shared with the Dashboard sidebar item (util/UpdateFlow)
        com.finance.util.UpdateFlow.start(updateButton.getScene().getWindow(), updateService, availableUpdate,
                () -> {
                    updateButton.setDisable(true);
                    updateButton.setText("Downloading...");
                    updateProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
                    updateProgress.setVisible(true);
                    updateProgress.setManaged(true);
                    growWindow(progressBarSpace());
                },
                p -> {
                    updateProgress.setProgress(p);
                    // floor, so it never shows 100% early
                    updateButton.setText("Downloading... " + (int) Math.floor(p * 100) + "%");
                },
                () -> {
                    updateButton.setDisable(false);
                    updateButton.setText("Update now");
                    updateProgress.setVisible(false);
                    updateProgress.setManaged(false);
                    growWindow(-progressBarSpace());
                });
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
            FXMLLoader loader = new FXMLLoader(getClass().getResource(com.finance.theme.ThemeManager.resolveFxml("/fxml/Register.fxml")));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage registerStage = new Stage(javafx.stage.StageStyle.UNDECORATED);
            registerStage.setTitle("Register New Account");
            registerStage.setScene(new Scene(com.finance.util.WindowChrome.wrap(registerStage,
                    com.finance.util.UiFormat.scrollable(root), false), 400, 480 + com.finance.util.WindowChrome.BAR_HEIGHT));
            registerStage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to open registration window");
        }
    }

    @FXML
    public void handleForgotPassword() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(com.finance.theme.ThemeManager.resolveFxml("/fxml/ForgotPassword.fxml")));
            loader.setControllerFactory(FinanceApplication.getApplicationContext()::getBean);
            Parent root = loader.load();

            Stage forgotPasswordStage = new Stage(javafx.stage.StageStyle.UNDECORATED);
            forgotPasswordStage.setTitle("Reset Password");
            forgotPasswordStage.setScene(new Scene(com.finance.util.WindowChrome.wrap(forgotPasswordStage,
                    com.finance.util.UiFormat.scrollable(root), false), 400, 420 + com.finance.util.WindowChrome.BAR_HEIGHT));
            forgotPasswordStage.show();
        } catch (IOException e) {
            showAlert("Error", "Failed to open reset password window");
        }
    }

    private void showAlert(String title, String message) {
        com.finance.util.AppDialogs.error(title, message);
    }
}