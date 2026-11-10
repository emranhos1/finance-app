package com.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/** Backs up to Google Drive on logout so the session's data is included.
 *  Caller must connect to Drive first; the logout flow asks the user. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoBackupService {
    private final IBackupService backupService;
    private final IGoogleDriveService googleDriveService;

    public boolean hasDriveConnection() {
        return googleDriveService.hasStoredCredentials() || googleDriveService.isConnected();
    }

    /** Exports the DB and uploads to Drive. Throws on failure so the caller can show the reason. */
    public void performBackup() throws Exception {
        File tempDir = new File(System.getProperty("java.io.tmpdir"));
        File dump = new File(backupService.exportToLocal(tempDir));
        try {
            // Cancel in the logout popup interrupts this thread - stop here rather than upload anyway
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Backup cancelled");
            }
            googleDriveService.upload(dump);
            log.info("Backup completed and uploaded to Google Drive");
        } finally {
            // the dump holds the whole DB including password hashes; never leave it in temp
            dump.delete();
        }
    }
}
