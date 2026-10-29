package de.citybuild;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class CityBuildPlugin extends JavaPlugin implements Listener {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private EconomyManager economy;
    private PlotManager plots;
    private WarpManager warps;
    private HomeManager homes;
    private TeleportManager teleports;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        economy = new EconomyManager(this);
        plots = new PlotManager(this);
        warps = new WarpManager(this);
        homes = new HomeManager(this);
        teleports = new TeleportManager(this);

        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new PlotListener(this), this);

        EconomyCommands eco = new EconomyCommands(this);
        register("money", eco);
        register("pay", eco);
        register("baltop", eco);
        register("eco", eco);

        register("plot", new PlotCommand(this));

        HomeCommands homeCommands = new HomeCommands(this);
        register("sethome", homeCommands);
        register("home", homeCommands);
        register("delhome", homeCommands);
        register("homes", homeCommands);

        WarpCommands warpCommands = new WarpCommands(this);
        register("spawn", warpCommands);
        register("setspawn", warpCommands);
        register("warp", warpCommands);
        register("warps", warpCommands);
        register("setwarp", warpCommands);
        register("delwarp", warpCommands);

        register("sell", new SellCommand(this));

        // Alle 5 Minuten Kontostaende speichern
        getServer().getScheduler().runTaskTimer(this, () -> economy.save(), 6000L, 6000L);

        getLogger().info("CityBuild aktiviert.");
    }

    @Override
    public void onDisable() {
        if (economy != null) {
            economy.save();
        }
    }

    private void register(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("citybuild")) {
            if (!sender.hasPermission("citybuild.admin")) {
                send(sender, "&cDazu hast du keine Berechtigung.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                send(sender, "&aKonfiguration neu geladen.");
            } else {
                send(sender, "&7Benutzung: &e/citybuild reload");
            }
            return true;
        }
        return false;
    }

    // ---------- Hilfsmethoden ----------

    public Component color(String text) {
        return LEGACY.deserialize(text);
    }

    /** Nachricht mit Prefix senden. */
    public void send(CommandSender target, String message) {
        target.sendMessage(color(getConfig().getString("prefix", "&8[&bCityBuild&8] &7") + message));
    }

    /** Nachricht ohne Prefix senden. */
    public void sendRaw(CommandSender target, String message) {
        target.sendMessage(color(message));
    }

    // ---------- Events ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        economy.ensureAccount(player.getUniqueId());

        if (!player.hasPlayedBefore() && getConfig().getBoolean("spawn.teleport-on-first-join", true)) {
            Location spawn = warps.getSpawn();
            if (spawn != null) {
                getServer().getScheduler().runTaskLater(this, () -> player.teleportAsync(spawn), 2L);
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (!getConfig().getBoolean("spawn.respawn-at-spawn", true)) {
            return;
        }
        if (event.isBedSpawn() || event.isAnchorSpawn()) {
            return;
        }
        Location spawn = warps.getSpawn();
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
    }

    // ---------- Zugriff ----------

    public EconomyManager economy() {
        return economy;
    }

    public PlotManager plots() {
        return plots;
    }

    public WarpManager warps() {
        return warps;
    }

    public HomeManager homes() {
        return homes;
    }

    public TeleportManager teleports() {
        return teleports;
    }
}
