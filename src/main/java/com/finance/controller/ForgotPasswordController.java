package com.finance.controller;

import com.finance.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class ForgotPasswordController {

    private final UserService userService;

    @FXML
    private TextField usernameField;

    @FXML
    private VBox resetBox;

    @FXML
    private Label securityQuestionLabel;

    @FXML
    private TextField securityAnswerField;

    @FXML
    private PasswordField newPasswordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Label messageLabel;

    private String verifiedUsername = null;

    @FXML
    public void handleFindAccount() {
        hideMessage();
        resetBox.setVisible(false);
        resetBox.setManaged(false);
        verifiedUsername = null;

        String username = usernameField.getText() == null ? "" : usernameField.getText().trim();
        if (username.isEmpty()) {
            showMessage("Please enter your username.");
            return;
        }
        if (!userService.userExists(username)) {
            showMessage("No account found with that username.");
            return;
        }
        String question = userService.getSecurityQuestion(username);
        if (question == null || question.trim().isEmpty()) {
            showMessage("No security question is set for this account yet. Ask the administrator to set one from Admin Panel.");
            return;
        }

        verifiedUsername = username;
        securityQuestionLabel.setText(question);
        securityAnswerField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        resetBox.setVisible(true);
        resetBox.setManaged(true);
    }

    @FXML
    public void handleResetPassword() {
        hideMessage();

        if (verifiedUsername == null) {
            showMessage("Please find your account first.");
            return;
        }

        String answer = securityAnswerField.getText() == null ? "" : securityAnswerField.getText();
        String newPassword = newPasswordField.getText() == null ? "" : newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText() == null ? "" : confirmPasswordField.getText();

        if (answer.trim().isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showMessage("Please fill in all fields.");
            return;
        }
        if (newPassword.length() < 4) {
            showMessage("New password must be at least 4 characters.");
            return;
        }
        if (!newPassword.equals(confirmPassword)) {
            showMessage("Passwords do not match.");
            return;
        }
        if (!userService.verifySecurityAnswer(verifiedUsername, answer)) {
            showMessage("Incorrect answer to the security question.");
            return;
        }

        try {
            userService.resetPassword(verifiedUsername, newPassword);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Password Reset");
            alert.setHeaderText(null);
            alert.setContentText("Your password has been reset. You can now log in with your new password.");
            alert.showAndWait();
            closeWindow();
        } catch (Exception e) {
            showMessage("Failed to reset password: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        ((Stage) usernameField.getScene().getWindow()).close();
    }

    private void showMessage(String text) {
        messageLabel.setText(text);
        messageLabel.setVisible(true);
        messageLabel.setManaged(true);
    }

    private void hideMessage() {
        messageLabel.setVisible(false);
        messageLabel.setManaged(false);
    }
}
