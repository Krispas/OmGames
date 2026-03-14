package krispasi.omGames.storage;

import java.io.File;

public final class OmGamesDatabaseFiles {
    public static final String MAIN_DATABASE_NAME = "OmGames.db";

    private OmGamesDatabaseFiles() {
    }

    public static File getMainDatabaseFile(File pluginDataFolder) {
        if (pluginDataFolder != null && !pluginDataFolder.exists()) {
            pluginDataFolder.mkdirs();
        }
        return new File(pluginDataFolder, MAIN_DATABASE_NAME);
    }
}
