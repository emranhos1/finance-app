package com.finance.service;

import java.io.File;

public interface IGoogleDriveService {

    /** True after a successful OAuth2 connect in this session. */
    boolean isConnected();

    /** True if a stored token lets connect() work without a browser; gates auto backups. */
    boolean hasStoredCredentials();

    /** Runs the OAuth2 flow (opens the browser) and stores the token. */
    void connect() throws Exception;

    /** Revokes the token and clears local state, so the next connect() needs sign-in. */
    void disconnect() throws Exception;

    /** Uploads the dump to the Drive folder as backup_data.sql. */
    String upload(File localDumpFile) throws Exception;

    /** Downloads the latest backup_data.sql from Drive into targetFolder. */
    File downloadLatest(File targetFolder) throws Exception;

    /** Display text for the latest backup on Drive, read live so other computers' uploads count. */
    String getLatestBackupDisplay() throws Exception;
}
