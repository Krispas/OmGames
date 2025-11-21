package krispasi.omGames.bedwars;

import krispasi.omGames.bedwars.command.BedWarsCommand;
import krispasi.omGames.bedwars.config.BedWarsConfigService;
import krispasi.omGames.bedwars.menu.BedWarsMenuFactory;
import krispasi.omGames.bedwars.menu.BedWarsMenuListener;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class BedWarsModule {
    private final JavaPlugin plugin;
    private BedWarsConfigService configService;

    public BedWarsModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        configService = new BedWarsConfigService(plugin);
        configService.load();

        // command + listeners live here so we can wire everything simply
        BedWarsMenuFactory menuFactory = new BedWarsMenuFactory();
        BedWarsCommand commandHandler = new BedWarsCommand(menuFactory, configService);
        registerCommandSafe(commandHandler);

        PluginManager pm = Bukkit.getPluginManager();
        pm.registerEvents(new BedWarsMenuListener(configService.getMaps(), configService.getDimensionName()), plugin);
    }

    public void disable() {
        // placeholder if we need cleanup later
    }

    public BedWarsConfigService getConfigService() {
        return configService;
    }

    private void registerCommandSafe(BedWarsCommand commandHandler) {
        // Paper wants commands registered in code; fall back to Bukkit-style wiring if reflection fails
        try {
            Constructor<PluginCommand> constructor = PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            constructor.setAccessible(true);
            PluginCommand command = constructor.newInstance("bw", plugin);

            command.setDescription("BedWars admin commands");
            command.setUsage("/bw start");
            command.setPermission("omgames.bw");
            command.setPermissionMessage("You need omgames.bw to manage BedWars.");
            command.setExecutor(commandHandler);
            command.setTabCompleter(commandHandler);

            Method getCommandMap = plugin.getServer().getClass().getMethod("getCommandMap");
            CommandMap commandMap = (CommandMap) getCommandMap.invoke(plugin.getServer());
            commandMap.register(plugin.getName().toLowerCase(), command);
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not register /bw command: " + ex.getMessage());
        }
    }
}
