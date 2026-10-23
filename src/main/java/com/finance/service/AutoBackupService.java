package com.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;

/** Backs up to Google Drive on logout (not on startup), so it captures the data entered during
 *  the session that is about to end rather than yesterday's state. The logout flow asks the user
 *  every time whether to back up, rather than deciding silently based on a schedule - this service
 *  just checks whether Google Drive is already connected and performs the actual export/upload;
 *  the logout flow itself prompts to connect first if it isn't. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoBackupService {
    private final IBackupService backupService;
    private final IGoogleDriveService googleDriveService;

    public boolean hasDriveConnection() {
        return googleDriveService.hasStoredCredentials() || googleDriveService.isConnected();
    }

    /** Exports the current database and uploads it to Google Drive. Assumes Google Drive is
     *  already connected (caller's job to connect first). Throws on any failure instead of
     *  swallowing it - the caller must show the real reason to the user rather than silently
     *  continuing as if the backup succeeded. */
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
