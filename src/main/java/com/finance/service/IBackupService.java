package com.finance.service;

import java.io.File;

public interface IBackupService {

    /** Runs mysqldump into backup_data.sql in targetFolder; returns its path. */
    String exportToLocal(File targetFolder) throws Exception;

    /** Imports the .sql file into the configured database. */
    String importFromLocal(File sqlFile) throws Exception;
}
