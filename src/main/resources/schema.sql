CREATE TABLE IF NOT EXISTS users (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role ENUM('admin', 'user') NOT NULL DEFAULT 'user',
    status ENUM('active', 'inactive') NOT NULL DEFAULT 'inactive',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
    );

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'security_question'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE users ADD COLUMN security_question VARCHAR(255) NULL, ADD COLUMN security_answer_hash VARCHAR(255) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS account_types (
                                             id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                             user_id BIGINT NOT NULL,
                                             name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_type (user_id, name)
    );

CREATE TABLE IF NOT EXISTS transfer_types (
                                              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                              user_id BIGINT NOT NULL,
                                              name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_transfer_type (user_id, name)
    );

CREATE TABLE IF NOT EXISTS accounts (
                                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        name VARCHAR(100) NOT NULL,
    account_type_id BIGINT NOT NULL,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    maturity_date DATE,
    installment_amount DECIMAL(15, 2),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (account_type_id) REFERENCES account_types(id),
    INDEX idx_user_id (user_id)
    );

-- show_in_goals was added for a Dashboard "Goal accounts" card that has since been removed
-- entirely; drop the column on any existing install that still has it (no-op on a fresh install).
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'show_in_goals'
);
SET @alter_stmt = IF(@col_exists > 0,
    'ALTER TABLE accounts DROP COLUMN show_in_goals',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- low_balance_threshold is an optional per-account floor: when an account's balance drops below
-- it, the Dashboard shows a warning toast. NULL (the default for every existing account) means
-- no alert is configured for that account - opt-in only, never changes existing behavior.
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'accounts' AND COLUMN_NAME = 'low_balance_threshold'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE accounts ADD COLUMN low_balance_threshold DECIMAL(15,2) NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS categories (
                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                          user_id BIGINT NOT NULL,
                                          name VARCHAR(100) NOT NULL,
    type ENUM('INCOME', 'EXPENSE', 'BOTH') NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    UNIQUE KEY uk_user_category (user_id, name, type)
    );

ALTER TABLE categories MODIFY COLUMN type ENUM('INCOME', 'EXPENSE', 'BOTH') NOT NULL;

CREATE TABLE IF NOT EXISTS transactions (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            user_id BIGINT NOT NULL,
                                            date DATE NOT NULL,
                                            type ENUM('INCOME', 'EXPENSE', 'TRANSFER') NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    category_id BIGINT,
    from_account_id BIGINT,
    to_account_id BIGINT,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    FOREIGN KEY (from_account_id) REFERENCES accounts(id) ON DELETE SET NULL,
    FOREIGN KEY (to_account_id) REFERENCES accounts(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_date (date),
    INDEX idx_from_account (from_account_id),
    INDEX idx_to_account (to_account_id)
    );

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions' AND COLUMN_NAME = 'transfer_type_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE transactions ADD COLUMN transfer_type_id BIGINT NULL, ADD FOREIGN KEY (transfer_type_id) REFERENCES transfer_types(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS monthly_expense_overrides (
                                                          id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                                          user_id BIGINT NOT NULL,
                                                          account_type_name VARCHAR(100) NOT NULL,
    expense_year INT NOT NULL,
    expense_month INT NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_type_year_month (user_id, account_type_name, expense_year, expense_month)
    );

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'monthly_expense_overrides' AND COLUMN_NAME = 'is_manual'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE monthly_expense_overrides ADD COLUMN is_manual BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- income_amount is the month's total Bank + Cash income, stored on both the CASH and BANK rows of
-- that month (same figure on both) - the base for the dashboard's expense % of income. NULL means
-- not filled yet (the startup backfill fills it from transactions).
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'monthly_expense_overrides' AND COLUMN_NAME = 'income_amount'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE monthly_expense_overrides ADD COLUMN income_amount DECIMAL(15, 2) NULL, ADD COLUMN is_income_manual BOOLEAN NOT NULL DEFAULT FALSE',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS loans (
                                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                     user_id BIGINT NOT NULL,
                                     person_name VARCHAR(150) NOT NULL,
    type ENUM('LENT', 'BORROWED') NOT NULL,
    principal_amount DECIMAL(15, 2) NOT NULL,
    account_id BIGINT NOT NULL,
    loan_date DATE NOT NULL,
    due_date DATE,
    note VARCHAR(500),
    status ENUM('OPEN', 'SETTLED') NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (account_id) REFERENCES accounts(id),
    INDEX idx_user_id (user_id)
    );

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'transactions' AND COLUMN_NAME = 'loan_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE transactions ADD COLUMN loan_id BIGINT NULL, ADD FOREIGN KEY (loan_id) REFERENCES loans(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loans' AND COLUMN_NAME = 'transfer_type_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE loans ADD COLUMN transfer_type_id BIGINT NULL, ADD FOREIGN KEY (transfer_type_id) REFERENCES transfer_types(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loans' AND COLUMN_NAME = 'category_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE loans ADD COLUMN category_id BIGINT NULL, ADD FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- initial_transaction_id lets deleting a loan also reverse/delete the original lend/borrow
-- transaction (previously only repayments were tagged, so deleting a loan silently left the
-- account balance out of sync with what the loans table showed).
SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loans' AND COLUMN_NAME = 'initial_transaction_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE loans ADD COLUMN initial_transaction_id BIGINT NULL, ADD FOREIGN KEY (initial_transaction_id) REFERENCES transactions(id) ON DELETE SET NULL',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- loan_persons is a "loan account" per person, so the same person can be picked from a dropdown
-- across multiple loans over time instead of retyping their name (which used to create confusing
-- duplicate-looking rows for the same person in the loans table).
CREATE TABLE IF NOT EXISTS loan_persons (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            user_id BIGINT NOT NULL,
                                            name VARCHAR(150) NOT NULL,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_loan_person (user_id, name)
    );

SET @col_exists = (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'loans' AND COLUMN_NAME = 'loan_person_id'
);
SET @alter_stmt = IF(@col_exists = 0,
    'ALTER TABLE loans ADD COLUMN loan_person_id BIGINT NULL, ADD FOREIGN KEY (loan_person_id) REFERENCES loan_persons(id)',
    'SELECT 1'
);
PREPARE stmt FROM @alter_stmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS account_logs (
                                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                                            account_id BIGINT NOT NULL,
                                            user_id BIGINT NOT NULL,
                                            change_type ENUM('CREDIT', 'DEBIT', 'ADJUSTMENT') NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    balance_before DECIMAL(15, 2) NOT NULL,
    balance_after DECIMAL(15, 2) NOT NULL,
    reference_type ENUM('INCOME', 'EXPENSE', 'TRANSFER', 'MANUAL') NOT NULL,
    reference_id BIGINT,
    note VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_account_id (account_id),
    INDEX idx_user_id (user_id)
    );