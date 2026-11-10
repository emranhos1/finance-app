# Bug Tracker

Status: `[ ]` open, `[x]` done. Mark each point done right after it is finished.

## From the review (verified against the code)

- [x] **1. [High] Zero/negative amount is not blocked** — DONE: `requirePositiveAmount` in `TransactionService`; unit tests pass; owner's final test passed.
  - Files: `TransactionService.java:33-121`, `TransactionController.java:509-514`, `TransferController.java:233`
  - Problem: a negative expense increases the balance; a negative transfer runs backwards.
  - Fix: in `recordIncome/Expense/Transfer` and `updateTransaction`, throw `IllegalArgumentException` if `amount == null || amount <= 0`.

- [x] **2. [High] Account delete has no guard** — DONE: `AccountService.deleteAccount(accountId, userId)` blocks on transactions, loans, non-zero balance; also adds the ownership check from point 15 for this method. Build and unit tests pass; owner's final test passed.
  - Files: `AccountService.java:108`, `AccountsController.java:164`, `schema.sql`
  - Problem: transactions are orphaned (FK `SET NULL`) and drop out of reports; with loans the user sees a raw SQL error.
  - Fix: block delete if the account has transactions, loans or a non-zero balance, with a clear message.

- [x] **3. [Medium] Edit/Delete can make a balance negative** — DONE: `requireNotNegative` in `TransactionService`: income edit, income delete, transfer delete to-account, transfer edit to-account. Build and unit tests pass; owner's tests passed. Not done (not listed): transfer edit lowering the to-account balance (`TransactionService.java:165`) — ask the owner first.
  - File: `TransactionService.java:135` (income reduced), `:193` (income delete), `:217` (transfer delete, to-account)
  - Problem: no "Insufficient balance" check like expenses have.
  - Fix: check `< 0` before lowering a balance.

- [x] **4. [Medium] Account/Type changes in the edit form are silently ignored** — DONE (owner chose "disable"): in edit mode the Income/Expense radios and Account dropdown (Transaction form) and the From/To dropdowns (Transfer form) are disabled; enabled again on reset/cancel. Build and unit tests pass; owner's test passed.
  - Files: `TransactionService.java:123`, `TransactionController.java:555`, `TransferController.java:262`
  - Problem: `updateTransaction` only changes date, amount, category, note. The form allows changing account/type and shows success.
  - Fix: support the change, or disable those fields in edit mode.

- [x] **5. [Medium] AVG is always divided by 12** — No action needed (owner decision: ignore).
  - File: `DashboardHomeController.java:761`
  - Problem: in the current year the average looks too low.
  - Fix: divide by elapsed months in the current year, or make the label clear.

- [x] **6. [Medium] Past months are frozen** — No action needed (intentional design, decided by the owner).

- [x] **7. [Medium] Backup `.sql` stays in temp** — DONE: dump is deleted in `finally` after Drive upload (`AutoBackupService`, `AdminPanelController` upload) and the downloaded file is deleted after restore; a half-downloaded file is deleted on failure (`GoogleDriveServiceImpl`). Build and unit tests pass; owner's test passed; owner deleted the old temp files.
  - Files: `AutoBackupService.java:31`, `AdminPanelController.java:223,238`
  - Problem: the full DB dump (with password hashes) stays in `%TEMP%` after upload; the file downloaded from Drive stays too.
  - Fix: delete the file in `finally` after upload or restore.

- [x] **8. [Medium] Weak Forgot Password** — DONE (owner's test passed): 5 wrong answers lock that username for 30 seconds (`UserService.verifySecurityAnswer`, in memory, resets on app restart); min password length is 6 in Register, Reset and Change (`UserService.changePassword`, `ForgotPasswordController`). Username-exists message left as is (owner decision: low risk on a desktop app). Build and unit tests pass.
  - Files: `ForgotPasswordController.java:55-62,91`, `UserService.java:103`, `RegisterController.java:56`
  - Problem: no limit on wrong answers; username existence is visible; min length differs (Register 6, Reset/Change 4).
  - Fix: add a limit or delay on wrong answers; use one minimum length everywhere.

- [x] **9. [Medium] Loan repayment can be edited above the principal** — DONE: `TransactionService.updateTransaction` rejects an edit when the new total repaid would exceed the loan principal. Build and unit tests pass; owner's test done. Note: editing a repayment needs a category to be chosen (existing behaviour, owner decided not to change it).
  - File: `TransactionService.java:133-181,231`
  - Problem: total repaid can exceed principal; `getRemaining` caps at 0 and hides it.
  - Fix: on edit, check new total repaid `<= principal`.

- [ ] **10. CSV import** — MOVED TO THE END of this list (see "Last: point 10" below). Do it last, after all other points.

- [x] **11. [Low] CSV export escaping / injection** — DONE: `csvEscape` in `ReportsController` (prefixes `'` for `=`, `+`, `-`, `@`, tab, CR; quotes values with `,`, `"`, newline). Owner's test passed.
  - File: `ReportsController.java:424`
  - Problem: only `,` is replaced; newline, quote and a leading `=`, `+`, `-`, `@` are not handled.
  - Fix: proper CSV escaping; prefix risky first characters with `'`.

- [x] **12. [Low] Backup/Restore process** — DONE: `--single-transaction` added to `mysqldump`; stderr is read on another thread (`drainErrors`) in export and import (`BackupServiceImpl`). `redirectErrorStream` not used because the export stdout is the dump itself. Build and unit tests pass; owner's test passed (bad file shows an error, real file restores).
  - File: `BackupServiceImpl.java:63-130`
  - Problem: no `--single-transaction`; stdout is read fully before stderr (possible deadlock).
  - Fix: add the flag; read stderr in another thread or use `redirectErrorStream`.

- [x] **13. [Low] Enum string literal in JPQL** — DONE: the 5 sum queries in `TransactionRepository` now take `t.type = :type` with a `Transaction.TransactionType` param; `TransactionService` passes `EXPENSE`/`INCOME`. Build and unit tests pass; owner's test passed.
  - File: `TransactionRepository.java:43-65`
  - Problem: `t.type = 'expense'` while the DB value is `EXPENSE`. Works only because MySQL compares ENUM case-insensitively.
  - Fix: use an enum parameter or the fully qualified enum constant.

- [x] **14. [Low] Weak defaults** — No action needed (owner decision: the app runs locally only, skip).
  - File: `application.properties`
  - Problem: `admin/admin123`, DB `root/root`.
  - Fix: force a password change on first login; read the DB password from env/config. (Needs owner approval before touching config.)

- [x] **15. [Low] No ownership check** — No action needed (owner decision: the UI only lists the logged-in user's own accounts and categories, so the check would never trigger). `deleteAccount` part stays, done inside point 2. A trial change to `updateAccount`/`deleteCategory` was reverted.
  - Files: `AccountService.java:47,56,108`, `CategoryService.java:57`
  - Fix: take `userId` and verify ownership in `deleteAccount`, `updateAccount`, `deleteCategory`.

- [x] **16. [Low] Session stays stale after restore** — No action needed (owner decision: only this app's own backups are restored, so user ids stay the same; the dump has `DROP TABLE` and fixed ids).
  - Files: `AdminPanelController.java:262-270`, `SessionContext.java`
  - Fix: log the user out (or reload) after a successful restore.

- [x] **17. [Low] Drive file name path risk** — DONE: `downloadLatest` keeps only the file-name part (`Paths.get(name).getFileName()`). Build passes; owner's test done.
  - File: `GoogleDriveServiceImpl.java:163`
  - Problem: `new File(targetFolder, latest.getName())`; a name with `..\` can write outside the folder.
  - Fix: use only the file-name part (`Path.getFileName`) or sanitize.

- [x] **18. [Low] Low test coverage; CI skips tests** — DONE: 10 new tests in `TransactionServiceTest` (points 1, 3, 9) and new `LoanServiceTest` (9 tests); 27 tests pass (`mvn test -DforkCount=0`, because the normal fork fails in this tool environment). `release.yml` now runs `mvn -B package` without `-DskipTests`. Owner's run on their machine: `mvn test -DforkCount=0` gives 27 passed, BUILD SUCCESS. Plain `mvn test` fails on the owner's PC with `CreateProcess error=740` (cmd.exe needs elevation; environment issue, not code), so use `-DforkCount=0` locally.
  - Files: `src/test`, `.github/workflows/release.yml:47`
  - Problem: 8 tests, all for `TransactionService`; CI uses `-DskipTests`.
  - Fix: add tests for bugs 1, 3, 9 and for `LoanService`; run `mvn test` in CI.

## Extra points found during verification

- [x] **19. [Medium] Monthly expense shows a wrong `0` after income is saved for a month with no row** — DONE: `writeIncome` now fills a missing row with the real expense of that month (`getEffectiveExpense`) instead of 0; existing rows keep their expense. Build passes; checked on `finance_test_db` with a temporary user (cleaned up afterwards). Old `0` rows are not fixed (use the refresh icon).
  - File: `MonthlyExpenseOverrideService.java:186`
  - Problem: `writeIncome` creates a row with expense `0`; `backfillHistoricalMonths` then sees the row and skips that month, so the real expense is never filled.
  - Fix: let the backfill fill expense for rows that were created by `writeIncome` (non-manual), or calculate the real expense when creating the row.

- [x] **20. [Low] Transfer update does not check a null date** — DONE: the DatePicker keeps its old value when the text is cleared, so a plain `null` check never fired. New `DatePickerUtil.readDate` treats blank text as no date; used in Transfer (save/update), Transaction (save/update) and Loan (create, repay, due date). Build passes; owner's test passed.
  - File: `TransferController.java:253`
  - Problem: `date` is `NOT NULL` in the DB; an empty date gives a raw DB error.
  - Fix: validate the date before calling the service (also in `handleSaveTransfer`).

## New request

- [x] **23. [UI] Show each account's balance in the Transfer form** — DONE: "Available balance: ৳ X" label under From and To (same style as the Transaction form), `Transfer.fxml` + `TransferController`. First version put the balance inside the dropdown text; replaced. Build passes; owner's test done.

- [x] **24. [UI] Low-balance banner on the Dashboard lists the account names when more than one is low** — DONE: `DashboardHomeController.showLowBalanceToast`, names joined with `, `. Click to open the Accounts tab was already there and is unchanged. Build passes; owner's test done.

- [x] **25. [UI] Show the selected account's balance in the Loan form** — DONE: "Available balance: ৳ X" label under the Account dropdown (`Loan.fxml`, `LoanController`), same style as the Transaction form. Not added to the repayment account dropdown (not requested). Build passes; owner's test done.

- [x] **26. [UI] Show the connected Google account email in Admin Panel Drive section, and make confirm dialogs show the full text** — DONE: `IGoogleDriveService.getConnectedEmail()` (read once at connect via Drive `about`); email shown in Connect/Upload/Restore status messages and the Upload/Download confirm dialogs; `AdminPanelController.confirm()` dialog has min width and pref height. Build passes; owner's test done.

- [x] **22. [UI] Show Maturity Date and Installment only for DPS/FDR account types** — DONE: `Accounts.fxml` box `maturityInstallmentBox`, listener in `AccountsController`. Matches type name `DPS` or `FDR` (case-insensitive). Build passes; owner's final test passed.

- [x] **21. [Medium] Profiling for `application.properties`** — DONE: `application.properties` holds `spring.profiles.active=dev` and the common settings; `application-dev.properties` (`finance_test_db`), `application-stage.properties` (same DB as dev for now) and `application-prod.properties` (`finance_db_v2`) hold only the DB connection. `release.yml` builds the installer with `-Dspring.profiles.active=prod`. README updated. Build passes; owner's test passed. Note: the DB name in the old text below (`finance_db`) was wrong; the committed name was `finance_db_v2`.
  - File: `src/main/resources/application.properties`
  - Problem: dev and production use the same DB name (`finance_db`) and the same file, so a dev run can touch production data.
  - Fix: split config with Spring profiles (for example `application-dev.properties` and `application-prod.properties`), keep shared keys in the base file, and select the profile at run time. Use a different DB name for dev.

## Last: point 10 (do after everything else)

- [ ] **10. CSV import: more than 2 decimals, plus importing 2-3 years of Google Sheet data**
  - Current problem: `TransactionController.java:433` reads the amount with `new BigDecimal(amountStr)` and never limits the decimals. Fix options: `setScale(2, HALF_UP)` or reject extra decimals.
  - Bigger goal (owner): 2-3 years of data now in Google Sheets must be imported into the app. This needs a plan first, so do not start coding before the plan is agreed.
  - Open questions for the plan:
    - Which Google Sheet columns exist, and do they match `Date, Type, Account, Category, Amount, Note`? The importer also handles only INCOME and EXPENSE, not transfers or loans.
    - Accounts and categories must already exist in the app (unknown ones are skipped, never auto-created).
    - Each row changes the account balances, and the import logs a balance change per row. Opening balances must be planned so the final balances are right.
    - Which database receives the data (`finance_test_db` first, then production)? Back up before importing.
    - Monthly Income & Expense table: old months are frozen; imported past months need a recalculation (the refresh icon) afterwards.
    - Rows are applied one by one in file order. An EXPENSE row fails with "Insufficient balance" if the account balance at that moment is lower than the amount, and that row is skipped. For old data, either set opening balances first or sort rows so income comes before the expenses it pays for.
    - There is no duplicate check: importing the same file twice creates every transaction twice.
    - Each row is saved on its own (no all-or-nothing), so a failed import leaves the earlier rows in the DB.
