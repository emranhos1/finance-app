package com.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializationService {
    private final DataSource dataSource;
    private final UserService userService;
    private final MonthlyExpenseOverrideService monthlyExpenseOverrideService;

    @Value("${admin.username}")
    private String adminUsername;

    @Value("${admin.password}")
    private String adminPassword;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeDatabase() {
        try {
            log.info("Starting database initialization...");

            try (Connection connection = dataSource.getConnection()) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
                log.info("Database schema initialized successfully");
            } catch (SQLException e) {
                log.error("Failed to initialize database schema", e);
                throw new RuntimeException("Database connection failed. Ensure MySQL is running and configured in application.properties", e);
            }

            if (!userService.userExists(adminUsername)) {
                userService.createUser(
                        adminUsername,
                        adminPassword,
                        com.finance.entity.User.UserRole.admin,
                        com.finance.entity.User.UserStatus.active
                );
                log.info("Default admin user created: {}", adminUsername);
            } else {
                log.info("Admin user already exists, skipping creation");
            }

            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            log.error("Database initialization failed", e);
            throw new RuntimeException("Failed to initialize database", e);
        }

        try {
            monthlyExpenseOverrideService.backfillHistoricalMonths();
        } catch (Exception e) {
            log.error("Monthly expense backfill failed - dashboard will still work, only historical months may show 0 until manually corrected", e);
        }

        try {
            monthlyExpenseOverrideService.backfillIncome();
        } catch (Exception e) {
            log.error("Monthly income backfill failed - dashboard will still work, only historical months may show no income until manually corrected", e);
        }
    }
}