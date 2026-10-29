package de.citybuild;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlotCommand implements TabExecutor {

    private final CityBuildPlugin plugin;

    public PlotCommand(CityBuildPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "&cDieser Befehl ist nur fuer Spieler.");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "claim", "buy", "kaufen" -> claim(player);
            case "unclaim", "sell", "verkaufen" -> unclaim(player);
            case "info" -> info(player);
            case "trust" -> trust(player, args, true);
            case "untrust" -> trust(player, args, false);
            case "list" -> list(player);
            default -> help(player);
        }
        return true;
    }

    private void help(Player player) {
        plugin.sendRaw(player, "&8&m      &r &bGrundstuecke &8&m      ");
        plugin.sendRaw(player, "&e/plot claim &7- Grundstueck (Chunk) kaufen, auf dem du stehst");
        plugin.sendRaw(player, "&e/plot unclaim &7- Grundstueck verkaufen");
        plugin.sendRaw(player, "&e/plot info &7- Infos zum aktuellen Grundstueck");
        plugin.sendRaw(player, "&e/plot trust <Spieler> &7- Spieler darf hier bauen");
        plugin.sendRaw(player, "&e/plot untrust <Spieler> &7- Erlaubnis entziehen");
        plugin.sendRaw(player, "&e/plot list &7- Deine Grundstuecke");
    }

    private void claim(Player player) {
        PlotManager plots = plugin.plots();
        EconomyManager economy = plugin.economy();
        Location loc = player.getLocation();

        if (!plots.isPlotWorld(loc.getWorld())) {
            plugin.send(player, "&cIn dieser Welt kannst du keine Grundstuecke kaufen.");
            return;
        }
        Chunk chunk = loc.getChunk();
        PlotManager.Plot existing = plots.getPlot(chunk);
        if (existing != null) {
            if (existing.owner().equals(player.getUniqueId())) {
                plugin.send(player, "&cDieses Grundstueck gehoert dir bereits.");
            } else {
                plugin.send(player, "&cDieses Grundstueck gehoert &e" + nameOf(existing.owner()) + "&c.");
            }
            return;
        }

        int radius = plugin.getConfig().getInt("plots.spawn-protect-radius", 64);
        if (radius > 0) {
            Location spawn = loc.getWorld().getSpawnLocation();
            double dx = (chunk.getX() * 16 + 8) - spawn.getX();
            double dz = (chunk.getZ() * 16 + 8) - spawn.getZ();
            if (dx * dx + dz * dz < (double) radius * radius) {
                plugin.send(player, "&cIn der Naehe des Spawns darf nichts geclaimt werden.");
                return;
            }
        }

        int max = plugin.getConfig().getInt("plots.max-per-player", 5);
        int owned = plots.getOwned(player.getUniqueId()).size();
        if (!player.hasPermission("citybuild.plots.unlimited") && owned >= max) {
            plugin.send(player, "&cDu hast bereits das Maximum von &e" + max + "&c Grundstuecken.");
            return;
        }

        double price = plugin.getConfig().getDouble("plots.price", 2500.0);
        if (!economy.withdraw(player.getUniqueId(), price)) {
            plugin.send(player, "&cDu brauchst &e" + economy.format(price) + "&c, hast aber nur &e"
                    + economy.format(economy.get(player.getUniqueId())) + "&c.");
            return;
        }
        plots.claim(chunk, player.getUniqueId());
        plugin.send(player, "&aGrundstueck gekauft fuer &e" + economy.format(price) + "&a! Chunk "
                + chunk.getX() + " / " + chunk.getZ());
    }

    private void unclaim(Player player) {
        PlotManager plots = plugin.plots();
        EconomyManager economy = plugin.economy();
        PlotManager.Plot plot = plots.getPlot(player.getLocation());
        if (plot == null) {
            plugin.send(player, "&cHier gibt es kein Grundstueck.");
            return;
        }
        boolean admin = player.hasPermission("citybuild.bypass");
        if (!plot.owner().equals(player.getUniqueId()) && !admin) {
            plugin.send(player, "&cDas ist nicht dein Grundstueck.");
            return;
        }
        double percent = plugin.getConfig().getDouble("plots.refund-percent", 50);
        double refund = plugin.getConfig().getDouble("plots.price", 2500.0) * percent / 100.0;
        plots.unclaim(plot);
        economy.ensureAccount(plot.owner());
        economy.deposit(plot.owner(), refund);
        plugin.send(player, "&aGrundstueck verkauft. Rueckerstattung: &e" + economy.format(refund));
    }

    private void info(Player player) {
        PlotManager.Plot plot = plugin.plots().getPlot(player.getLocation());
        if (plot == null) {
            plugin.send(player, "&7Dieses Grundstueck ist &afrei&7. Kaufe es mit &e/plot claim&7.");
            return;
        }
        List<String> trusted = new ArrayList<>();
        for (UUID uuid : plot.trusted()) {
            trusted.add(nameOf(uuid));
        }
        plugin.sendRaw(player, "&8&m      &r &bGrundstueck &8&m      ");
        plugin.sendRaw(player, "&7Besitzer: &e" + nameOf(plot.owner()));
        plugin.sendRaw(player, "&7Vertraut: &e" + (trusted.isEmpty() ? "niemand" : String.join(", ", trusted)));
        plugin.sendRaw(player, "&7Chunk: &e" + plot.x() + " / " + plot.z());
    }

    private void trust(Player player, String[] args, boolean add) {
        if (args.length != 2) {
            plugin.send(player, "&7Benutzung: &e/plot " + (add ? "trust" : "untrust") + " <Spieler>");
            return;
        }
        PlotManager plots = plugin.plots();
        PlotManager.Plot plot = plots.getPlot(player.getLocation());
        if (plot == null || !plot.owner().equals(player.getUniqueId())) {
            plugin.send(player, "&cDu musst auf deinem eigenen Grundstueck stehen.");
            return;
        }
        OfflinePlayer target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            target = Bukkit.getOfflinePlayerIfCached(args[1]);
        }
        if (target == null) {
            plugin.send(player, "&cSpieler nicht gefunden.");
            return;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            plugin.send(player, "&cDas bist du selbst.");
            return;
        }
        if (add) {
            plot.trusted().add(target.getUniqueId());
            plugin.send(player, "&e" + args[1] + "&a darf jetzt auf diesem Grundstueck bauen.");
        } else {
            plot.trusted().remove(target.getUniqueId());
            plugin.send(player, "&e" + args[1] + "&7 darf hier nicht mehr bauen.");
        }
        plots.save();
    }

    private void list(Player player) {
        List<PlotManager.Plot> owned = plugin.plots().getOwned(player.getUniqueId());
        if (owned.isEmpty()) {
            plugin.send(player, "&7Du besitzt noch keine Grundstuecke.");
            return;
        }
        plugin.sendRaw(player, "&8&m      &r &bDeine Grundstuecke &8&m      ");
        for (PlotManager.Plot plot : owned) {
            plugin.sendRaw(player, "&7- &e" + plot.world() + "&7 bei X: &e" + (plot.x() * 16 + 8)
                    + "&7 Z: &e" + (plot.z() * 16 + 8));
        }
    }

    private String nameOf(UUID uuid) {
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.addAll(List.of("claim", "unclaim", "info", "trust", "untrust", "list"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust"))) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                result.add(p.getName());
            }
        }
        String prefix = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        result.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return result;
    }
}
