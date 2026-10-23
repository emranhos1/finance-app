# Daily Finance Management System (DFMS)

Desktop app for tracking personal finances, built with Java 21, Spring Boot 2.7, JavaFX 17 and MySQL 8.

## Features

- **Dashboard**
  - Balance per account type.
  - Given/Taken loan summary and recent transactions.
  - Expense % of income ring, top expense and expense pie chart.
  - **Monthly Income & Expense** table with a year picker. Columns: Income (Bank + Cash), Cash Expense, Bank Expense, Cash + Bank Expense, and % = (Cash + Bank) ÷ Income × 100. Income, Cash and Bank can be corrected by hand (✎) or recalculated from transactions (↻).
- **Accounts:** your own account types (Bank, Cash, DPS, FDR, Plot…). Every balance change is written to an account log.
- **Transaction:**
  - Income and expense with categories.
  - Transfers between your own accounts, with transfer types. Transfers never count as income or expense.
  - Loans given/taken per person, with repayments, bulk settlement and due-date reminders.
- **Reports:** filters, category breakdown, account balances, trend chart, paged account log, CSV export.
- **Calculator:** add up category entries over a date range.
- **Admin Panel** (admin only):
  - Activate or deactivate users, and set a user's security question.
  - Local backup/restore through `mysqldump`/`mysql`, and Google Drive backup/restore.
  - Data integrity check.
- **Security:**
  - Passwords and security answers are hashed with BCrypt.
  - New users start inactive until an admin activates them.
  - Forgot Password requires the security question.
- **Logout backup:** on logout the app offers to back up to Google Drive (Backup Now / Skip & Logout / Cancel).
- **Auto-update:** the login screen checks GitHub Releases. **Update now** downloads the installer (SHA-256 verified) and opens it.

## Requirements

- JDK 21
- Maven 3.6+
- MySQL 8 running on `localhost:3306`. The MySQL client tools (`mysqldump`, `mysql`) are needed only for backups.

## Setup

1. Set the database login in `src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/finance_db?createDatabaseIfNotExist=true&...
   spring.datasource.username=root
   spring.datasource.password=Your Password
   admin.username=admin
   admin.password=admin123
   ```
2. For Google Drive backup in a dev run, put your OAuth client file at `src/main/resources/credentials.json`. This path is git-ignored. Never commit it.
3. Run the app:
   ```bash
   mvn javafx:run
   ```

On every start the app does the following:
- Creates the database if needed.
- Runs `schema.sql`. It is idempotent: tables and columns are added only when missing.
- Creates the admin user if it doesn't exist.
- Back-fills monthly expense and income figures.

Default login: `admin` / `admin123`. Change it after the first login.

## Development

```bash
mvn compile                 # build
mvn test                    # TransactionService balance tests
mvn -DskipTests package     # target/dfms.jar
java -jar target/dfms.jar --app.version=1.0.0   # run as an older version to test the update flow
```

## Project structure

```
src/main/java/com/finance/
  controller/   JavaFX controllers
  service/      business logic (impl/ = backup and Google Drive)
  repository/   Spring Data JPA
  entity/       JPA entities
  context/      logged-in user session
  config/       BCrypt PasswordEncoder
src/main/resources/
  fxml/  css/  images/  schema.sql  application.properties
release-notes/  one v<version>.md per release (English, <!-- bn -->, Bangla)
installer/      Inno Setup script (dfms.iss) and icon
.github/workflows/release.yml
```

## Database

Main tables:
- `users`
- `account_types`, `accounts`, `account_logs`
- `categories`, `transactions`, `transfer_types`
- `loans`, `loan_persons`
- `monthly_expense_overrides`: the saved monthly expense per account type, plus the month's income.

## Release

Steps:
1. Bump `<version>` in `pom.xml`.
2. Add `release-notes/v<version>.md`.
3. Push to the `production` branch.

GitHub Actions then does the following:
- Builds the jar with JDK 21.
- Makes an app image with its own Java runtime (`jpackage`).
- Builds `DFMS-Update-<version>.exe` with Inno Setup.
- Publishes the GitHub Release.

See [release-notes/README.md](release-notes/README.md) for the full steps.

On an installed PC, place `credentials.json` next to `DFMS.exe` once. Updates never delete it.
