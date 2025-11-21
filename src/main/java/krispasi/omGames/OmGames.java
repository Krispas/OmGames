package krispasi.omGames;

import krispasi.omGames.bedwars.BedWarsModule;
import org.bukkit.plugin.java.JavaPlugin;

public final class OmGames extends JavaPlugin {

    private BedWarsModule bedWarsModule;

    @Override
    public void onEnable() {
        // boot BedWars systems with safety nets so we don't crash the server
        try {
            bedWarsModule = new BedWarsModule(this);
            bedWarsModule.enable();
        } catch (Exception ex) {
            getLogger().severe("Failed to start BedWars module: " + ex.getMessage());
        }
    }

    @Override
    public void onDisable() {
        if (bedWarsModule != null) {
            try {
                bedWarsModule.disable();
            } catch (Exception ex) {
                getLogger().warning("Issue while shutting down BedWars: " + ex.getMessage());
            }
        }
    }
}
