package krispasi.omGames.storage;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Stream;

public final class OmGamesDatabaseBackupService {
    private static final DateTimeFormatter BACKUP_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String BACKUP_FILE_PREFIX = "OmGames-";
    private static final String BACKUP_FILE_SUFFIX = ".db";
    private static final int MAX_BACKUP_COUNT = 7;
    private static final long CHECK_INTERVAL_TICKS = 20L * 60L * 60L;

    private final JavaPlugin plugin;
    private final File databaseFile;
    private final File backupFolder;
    private BukkitTask checkTask;
    private boolean backupRunning;

    public OmGamesDatabaseBackupService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.databaseFile = OmGamesDatabaseFiles.getMainDatabaseFile(plugin.getDataFolder());
        this.backupFolder = new File(new File(plugin.getDataFolder(), "backups"), "database");
    }

    public synchronized void start() {
        backupIfDue();
        if (checkTask != null) {
            return;
        }
        checkTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::backupIfDue,
                CHECK_INTERVAL_TICKS,
                CHECK_INTERVAL_TICKS
        );
    }

    public synchronized void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    private synchronized void backupIfDue() {
        if (backupRunning) {
            return;
        }
        backupRunning = true;
        try {
            if (!databaseFile.isFile()) {
                pruneOldBackups();
                return;
            }
            if (!backupFolder.exists()) {
                backupFolder.mkdirs();
            }

            File backupFile = backupFileFor(LocalDate.now());
            if (backupFile.isFile()) {
                pruneOldBackups();
                return;
            }

            File tempBackupFile = new File(backupFolder, backupFile.getName() + ".tmp");
            Files.deleteIfExists(tempBackupFile.toPath());
            createSqliteBackup(tempBackupFile);
            moveBackupIntoPlace(tempBackupFile.toPath(), backupFile.toPath());
            plugin.getLogger().info("Created OmGames database backup: " + backupFile.getName());
            pruneOldBackups();
        } catch (IOException | SQLException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to create OmGames database backup.", ex);
        } finally {
            backupRunning = false;
        }
    }

    private File backupFileFor(LocalDate date) {
        return new File(backupFolder, BACKUP_FILE_PREFIX + BACKUP_DATE_FORMAT.format(date) + BACKUP_FILE_SUFFIX);
    }

    private void createSqliteBackup(File targetFile) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.getAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 5000");
            statement.execute("VACUUM main INTO '" + escapeSqliteString(targetFile.getAbsolutePath()) + "'");
        }
    }

    private String escapeSqliteString(String value) {
        return value.replace("'", "''");
    }

    private void moveBackupIntoPlace(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(source, target);
        }
    }

    private void pruneOldBackups() throws IOException {
        if (!backupFolder.isDirectory()) {
            return;
        }
        try (Stream<Path> paths = Files.list(backupFolder.toPath())) {
            List<Path> backups = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> isBackupFile(path.getFileName().toString()))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .toList();
            for (int index = MAX_BACKUP_COUNT; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        }
    }

    private boolean isBackupFile(String fileName) {
        if (!fileName.startsWith(BACKUP_FILE_PREFIX) || !fileName.endsWith(BACKUP_FILE_SUFFIX)) {
            return false;
        }
        String datePart = fileName.substring(BACKUP_FILE_PREFIX.length(), fileName.length() - BACKUP_FILE_SUFFIX.length());
        try {
            LocalDate.parse(datePart, BACKUP_DATE_FORMAT);
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
