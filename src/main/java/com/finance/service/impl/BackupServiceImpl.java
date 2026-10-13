package com.finance.service.impl;

import com.finance.service.IBackupService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class BackupServiceImpl implements IBackupService {

    private static final DateTimeFormatter BACKUP_NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'.'HHmm");

    private static final Pattern URL_PATTERN = Pattern.compile("jdbc:mysql://([^:/]+):(\\d+)/([^?]+)");
    private static final Pattern REG_LOCATION_PATTERN = Pattern.compile("Location\\s+REG_SZ\\s+(.+)");

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    private final DataSource dataSource;

    /** tool name -> full path found on this machine, so the drive scan runs only once per app start */
    private final Map<String, String> resolvedCommands = new ConcurrentHashMap<>();

    public BackupServiceImpl(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String exportToLocal(File targetFolder) throws Exception {
        if (targetFolder == null || !targetFolder.isDirectory()) {
            throw new IllegalArgumentException("Please select a valid folder");
        }
        String[] conn = parseConnection();
        File outputFile = new File(targetFolder, generateBackupFileName());

        ProcessBuilder pb = new ProcessBuilder(
                resolveMysqlCommand("mysqldump"),
                "-h", conn[0],
                "-P", conn[1],
                "-u", dbUsername,
                conn[2]
        );
        pb.environment().put("MYSQL_PWD", dbPassword);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new Exception("Could not start mysqldump: " + e.getMessage());
        }

        try (InputStream in = process.getInputStream();
             OutputStream out = new FileOutputStream(outputFile)) {
            in.transferTo(out);
        }
        String errorOutput = readStream(process.getErrorStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            outputFile.delete();
            throw new Exception("mysqldump failed: " + (errorOutput.isBlank() ? "exit code " + exitCode : errorOutput.trim()));
        }
        if (!outputFile.exists() || outputFile.length() == 0) {
            throw new Exception("Backup file was not created or is empty");
        }
        return outputFile.getAbsolutePath();
    }

    @Override
    public String importFromLocal(File sqlFile) throws Exception {
        if (sqlFile == null || !sqlFile.isFile()) {
            throw new IllegalArgumentException("Please select a valid .sql file");
        }
        String[] conn = parseConnection();

        ProcessBuilder pb = new ProcessBuilder(
                resolveMysqlCommand("mysql"),
                "-h", conn[0],
                "-P", conn[1],
                "-u", dbUsername,
                conn[2]
        );
        pb.environment().put("MYSQL_PWD", dbPassword);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            throw new Exception("Could not start mysql: " + e.getMessage());
        }

        try (InputStream fileIn = new FileInputStream(sqlFile);
             OutputStream procIn = process.getOutputStream()) {
            fileIn.transferTo(procIn);
        }
        String errorOutput = readStream(process.getErrorStream());
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new Exception("mysql import failed: " + (errorOutput.isBlank() ? "exit code " + exitCode : errorOutput.trim()));
        }
        return "Database restored successfully from " + sqlFile.getName();
    }

    /**
     * Finds the full path of a MySQL client tool (mysqldump / mysql) on the client's own machine -
     * MySQL may be installed on any drive, not only C:. Looked up in this order, first hit wins:
     * <ol>
     *   <li>the running MySQL server's own install folder ({@code SELECT @@basedir} over the app's
     *       existing DB connection) - exactly the MySQL this app uses, wherever it was installed;</li>
     *   <li>the PATH;</li>
     *   <li>the locations MySQL's Windows installer registered (64-bit and 32-bit registry views);</li>
     *   <li>common MySQL / XAMPP / WAMP / Laragon folders on every drive.</li>
     * </ol>
     * The full path is passed as the command itself because ProcessBuilder.environment() PATH
     * changes do NOT affect how Windows/Java resolves a bare command name.
     */
    private String resolveMysqlCommand(String name) throws Exception {
        String cached = resolvedCommands.get(name);
        if (cached != null && new File(cached).isFile()) return cached;

        String exeName = name + ".exe";
        for (File binDir : candidateBinDirs()) {
            File exe = new File(binDir, exeName);
            if (exe.isFile()) {
                resolvedCommands.put(name, exe.getAbsolutePath());
                return exe.getAbsolutePath();
            }
        }
        throw new Exception(exeName + " was not found. Looked in the MySQL server's own folder, the PATH, "
                + "the Windows registry and the usual MySQL folders on every drive. Add the 'bin' folder of "
                + "your MySQL installation to the system PATH, then restart the app.");
    }

    private Set<File> candidateBinDirs() {
        Set<File> dirs = new LinkedHashSet<>();
        File serverBin = binDirFromServer();
        if (serverBin != null) dirs.add(serverBin);
        dirs.addAll(binDirsFromPath());
        dirs.addAll(binDirsFromRegistry("/reg:64"));
        dirs.addAll(binDirsFromRegistry("/reg:32"));
        dirs.addAll(binDirsOnAllDrives());
        return dirs;
    }

    /** The bin folder of the MySQL server the app is connected to, e.g. D:\MySQL\MySQL Server 8.0\bin. */
    private File binDirFromServer() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT @@basedir")) {
            if (rs.next() && rs.getString(1) != null) {
                return new File(rs.getString(1).trim(), "bin");
            }
        } catch (Exception e) {
            // fall through to the other lookups
        }
        return null;
    }

    private List<File> binDirsFromPath() {
        List<File> dirs = new ArrayList<>();
        String path = System.getenv("Path");
        if (path == null) path = System.getenv("PATH");
        if (path == null) return dirs;
        for (String dir : path.split(File.pathSeparator)) {
            if (!dir.isBlank()) dirs.add(new File(dir.trim().replace("\"", "")));
        }
        return dirs;
    }

    /** MySQL's Windows installer registers HKLM\SOFTWARE\MySQL AB\<product>\Location; a 32-bit
     *  installer writes it to the 32-bit view (WOW6432Node), so both views are read. */
    private List<File> binDirsFromRegistry(String view) {
        List<File> dirs = new ArrayList<>();
        try {
            Process regProcess = new ProcessBuilder("reg", "query", "HKLM\\SOFTWARE\\MySQL AB", "/s", view).start();
            String output = readStream(regProcess.getInputStream());
            regProcess.waitFor();
            Matcher m = REG_LOCATION_PATTERN.matcher(output);
            while (m.find()) {
                dirs.add(new File(m.group(1).trim(), "bin"));
            }
        } catch (Exception e) {
            // registry not readable - the drive scan below still runs
        }
        return dirs;
    }

    /** Usual install folders on every drive (C:, D:, E:, ...), e.g. D:\Program Files\MySQL\MySQL Server 8.0\bin,
     *  E:\xampp\mysql\bin, or any top-level folder with "mysql" in its name (D:\mysql-8.0.36\bin). */
    private List<File> binDirsOnAllDrives() {
        List<File> dirs = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots == null) return dirs;
        for (File root : roots) {
            if (!root.isDirectory()) continue;
            addVersionBins(dirs, new File(root, "Program Files\\MySQL"));
            addVersionBins(dirs, new File(root, "Program Files (x86)\\MySQL"));
            dirs.add(new File(root, "xampp\\mysql\\bin"));
            addVersionBins(dirs, new File(root, "wamp64\\bin\\mysql"));
            addVersionBins(dirs, new File(root, "wamp\\bin\\mysql"));
            addVersionBins(dirs, new File(root, "laragon\\bin\\mysql"));
            File[] topLevel = root.listFiles(File::isDirectory);
            if (topLevel == null) continue;
            for (File folder : topLevel) {
                if (folder.getName().toLowerCase().contains("mysql")) {
                    dirs.add(new File(folder, "bin"));
                    addVersionBins(dirs, folder);
                }
            }
        }
        return dirs;
    }

    /** Adds <parent>\<every subfolder>\bin, e.g. MySQL\MySQL Server 8.0\bin. */
    private void addVersionBins(List<File> dirs, File parent) {
        File[] children = parent.listFiles(File::isDirectory);
        if (children == null) return;
        for (File child : children) {
            dirs.add(new File(child, "bin"));
        }
    }

    /** e.g. backup_data_20260131.1155.sql - date before the dot, time after it, so repeated backups never collide. */
    private String generateBackupFileName() {
        return "backup_data_" + LocalDateTime.now().format(BACKUP_NAME_FORMAT) + ".sql";
    }

    private String[] parseConnection() throws Exception {
        Matcher m = URL_PATTERN.matcher(datasourceUrl);
        if (!m.find()) {
            throw new Exception("Could not parse database connection details from application.properties");
        }
        return new String[]{m.group(1), m.group(2), m.group(3)};
    }

    private String readStream(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toString();
    }
}
