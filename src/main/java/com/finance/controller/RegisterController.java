package com.finance.controller;

import com.finance.entity.User;
import com.finance.service.UserService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class RegisterController {
    private final UserService userService;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField securityQuestionField;

    @FXML
    private TextField securityAnswerField;

    @FXML
    public void handleRegister() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        String securityQuestion = securityQuestionField.getText().trim();
        String securityAnswer = securityAnswerField.getText().trim();

        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Validation Error", "Please enter username and password");
            return;
        }

        if (securityQuestion.isEmpty() || securityAnswer.isEmpty()) {
            showAlert("Validation Error", "Please set a security question and answer - it's needed to reset your password later");
            return;
        }

        if (username.length() < 3) {
            showAlert("Validation Error", "Username must be at least 3 characters");
            return;
        }

        if (password.length() < 6) {
            showAlert("Validation Error", "Password must be at least 6 characters");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showAlert("Validation Error", "Passwords do not match");
            return;
        }

        if (userService.userExists(username)) {
            showAlert("Registration Failed", "Username already exists");
            return;
        }

        try {
            userService.createUser(
                    username,
                    password,
                    User.UserRole.user,
                    User.UserStatus.inactive,
                    securityQuestion,
                    securityAnswer
            );
            showAlert("Success", "Registration successful! Please wait for admin to activate your account.");
            close();
        } catch (Exception e) {
            showAlert("Error", "Registration failed: " + e.getMessage());
        }
    }

    @FXML
    public void handleCancel() {
        close();
    }

    private void close() {
        Stage stage = (Stage) usernameField.getScene().getWindow();
        stage.close();
    }

    private void showAlert(String title, String message) {
        Alert alert = new com.finance.util.AppAlert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
