package com.finance.service;

import com.finance.entity.User;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<User> authenticateUser(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getStatus() == User.UserStatus.active)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()));
    }

    public User createUser(String username, String password, User.UserRole role, User.UserStatus status) {
        return createUser(username, password, role, status, null, null);
    }

    public User createUser(String username, String password, User.UserRole role, User.UserStatus status,
                            String securityQuestion, String securityAnswer) {
        User.UserBuilder builder = User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .role(role)
                .status(status);
        if (securityQuestion != null && !securityQuestion.trim().isEmpty()
                && securityAnswer != null && !securityAnswer.trim().isEmpty()) {
            builder.securityQuestion(securityQuestion.trim())
                    .securityAnswerHash(passwordEncoder.encode(normalizeAnswer(securityAnswer)));
        }
        return userRepository.save(builder.build());
    }

    /** Lets an admin (or the user themselves, if such a screen is ever added) set/replace the
     *  security question for an account that doesn't have one yet - e.g. accounts created before
     *  this feature existed. */
    public void setSecurityQuestion(Long userId, String question, String answer) {
        if (question == null || question.trim().isEmpty() || answer == null || answer.trim().isEmpty())
            throw new IllegalArgumentException("Security question and answer are required");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setSecurityQuestion(question.trim());
        user.setSecurityAnswerHash(passwordEncoder.encode(normalizeAnswer(answer)));
        userRepository.save(user);
    }

    /** Null/empty if this account has no security question set - self-service reset must be
     *  refused in that case rather than falling back to an unguarded reset. */
    public String getSecurityQuestion(String username) {
        return userRepository.findByUsername(username)
                .map(User::getSecurityQuestion)
                .orElse(null);
    }

    public boolean verifySecurityAnswer(String username, String answer) {
        return userRepository.findByUsername(username)
                .filter(user -> user.getSecurityAnswerHash() != null)
                .filter(user -> passwordEncoder.matches(normalizeAnswer(answer == null ? "" : answer), user.getSecurityAnswerHash()))
                .isPresent();
    }

    private String normalizeAnswer(String answer) {
        return answer.trim().toLowerCase();
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUserStatus(Long userId, User.UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setStatus(status);
        return userRepository.save(user);
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public User resetPassword(String username, String newPassword) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("No account found with that username"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /** Self-service password change for a logged-in user - requires the current password,
     *  unlike resetPassword() which is used by the security-question recovery flow. */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 4) {
            throw new IllegalArgumentException("New password must be at least 4 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}