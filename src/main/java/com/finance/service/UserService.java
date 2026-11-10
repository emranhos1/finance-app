package com.finance.service;

import com.finance.entity.User;
import com.finance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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

    /** Sets the security question for accounts that don't have one yet. */
    public void setSecurityQuestion(Long userId, String question, String answer) {
        if (question == null || question.trim().isEmpty() || answer == null || answer.trim().isEmpty())
            throw new IllegalArgumentException("Security question and answer are required");
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        user.setSecurityQuestion(question.trim());
        user.setSecurityAnswerHash(passwordEncoder.encode(normalizeAnswer(answer)));
        userRepository.save(user);
    }

    /** Null/empty means no question set; reject self-service reset then. */
    public String getSecurityQuestion(String username) {
        return userRepository.findByUsername(username)
                .map(User::getSecurityQuestion)
                .orElse(null);
    }

    /** Wrong answers per username; after MAX_WRONG_ANSWERS the username is locked for LOCK_SECONDS. Kept in memory. */
    private static final int MAX_WRONG_ANSWERS = 5;
    private static final long LOCK_SECONDS = 30;
    private final Map<String, int[]> wrongAnswers = new ConcurrentHashMap<>();
    private final Map<String, Long> lockedUntil = new ConcurrentHashMap<>();

    /** Throws IllegalStateException while the username is locked after too many wrong answers. */
    public boolean verifySecurityAnswer(String username, String answer) {
        Long until = lockedUntil.get(username);
        if (until != null) {
            long left = (until - System.currentTimeMillis() + 999) / 1000;
            if (left > 0) {
                throw new IllegalStateException("Too many wrong answers. Try again in " + left + " seconds.");
            }
            lockedUntil.remove(username);
            wrongAnswers.remove(username);
        }

        boolean correct = userRepository.findByUsername(username)
                .filter(user -> user.getSecurityAnswerHash() != null)
                .filter(user -> passwordEncoder.matches(normalizeAnswer(answer == null ? "" : answer), user.getSecurityAnswerHash()))
                .isPresent();

        if (correct) {
            wrongAnswers.remove(username);
        } else if (wrongAnswers.computeIfAbsent(username, k -> new int[1])[0]++ + 1 >= MAX_WRONG_ANSWERS) {
            lockedUntil.put(username, System.currentTimeMillis() + LOCK_SECONDS * 1000);
            wrongAnswers.remove(username);
        }
        return correct;
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

    /** Password change for a logged-in user; needs the current password. */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}