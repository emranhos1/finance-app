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
        String localPath = backupService.exportToLocal(tempDir);
        // Cancel in the logout popup interrupts this thread - stop here rather than upload anyway
        if (Thread.currentThread().isInterrupted()) {
            new File(localPath).delete();
            throw new InterruptedException("Backup cancelled");
        }
        googleDriveService.upload(new File(localPath));
        log.info("Backup completed and uploaded to Google Drive");
    }
}
