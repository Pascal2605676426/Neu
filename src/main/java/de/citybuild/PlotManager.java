package de.citybuild;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlotManager {

    /** Ein Grundstueck entspricht genau einem Chunk (16x16 Bloecke). */
    public static class Plot {
        private final String world;
        private final int x;
        private final int z;
        private final UUID owner;
        private final Set<UUID> trusted = new HashSet<>();

        public Plot(String world, int x, int z, UUID owner) {
            this.world = world;
            this.x = x;
            this.z = z;
            this.owner = owner;
        }

        public String world() {
            return world;
        }

        public int x() {
            return x;
        }

        public int z() {
            return z;
        }

        public UUID owner() {
            return owner;
        }

        public Set<UUID> trusted() {
            return trusted;
        }

        public boolean isMember(UUID uuid) {
            return owner.equals(uuid) || trusted.contains(uuid);
        }
    }

    private final CityBuildPlugin plugin;
    private final File file;
    private final Map<String, Plot> plots = new HashMap<>();

    public PlotManager(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plots.yml");
        load();
    }

    private static String key(String world, int x, int z) {
        return world + ";" + x + ";" + z;
    }

    private void load() {
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (Map<?, ?> map : yml.getMapList("plots")) {
            try {
                String world = String.valueOf(map.get("world"));
                int x = ((Number) map.get("x")).intValue();
                int z = ((Number) map.get("z")).intValue();
                UUID owner = UUID.fromString(String.valueOf(map.get("owner")));
                Plot plot = new Plot(world, x, z, owner);
                Object trusted = map.get("trusted");
                if (trusted instanceof List<?> list) {
                    for (Object o : list) {
                        plot.trusted().add(UUID.fromString(String.valueOf(o)));
                    }
                }
                plots.put(key(world, x, z), plot);
            } catch (Exception e) {
                plugin.getLogger().warning("Ungueltiger Eintrag in plots.yml uebersprungen: " + e.getMessage());
            }
        }
    }

    public void save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Plot plot : plots.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("world", plot.world());
            map.put("x", plot.x());
            map.put("z", plot.z());
            map.put("owner", plot.owner().toString());
            List<String> trusted = new ArrayList<>();
            for (UUID uuid : plot.trusted()) {
                trusted.add(uuid.toString());
            }
            map.put("trusted", trusted);
            out.add(map);
        }
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("plots", out);
        try {
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Konnte plots.yml nicht speichern: " + e.getMessage());
        }
    }

    public boolean isPlotWorld(World world) {
        return plugin.getConfig().getStringList("plots.worlds").contains(world.getName());
    }

    public Plot getPlot(Location loc) {
        if (loc.getWorld() == null) {
            return null;
        }
        return plots.get(key(loc.getWorld().getName(), loc.getBlockX() >> 4, loc.getBlockZ() >> 4));
    }

    public Plot getPlot(Chunk chunk) {
        return plots.get(key(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
    }

    public List<Plot> getOwned(UUID owner) {
        List<Plot> list = new ArrayList<>();
        for (Plot plot : plots.values()) {
            if (plot.owner().equals(owner)) {
                list.add(plot);
            }
        }
        return list;
    }

    public Plot claim(Chunk chunk, UUID owner) {
        Plot plot = new Plot(chunk.getWorld().getName(), chunk.getX(), chunk.getZ(), owner);
        plots.put(key(plot.world(), plot.x(), plot.z()), plot);
        save();
        return plot;
    }

    public void unclaim(Plot plot) {
        plots.remove(key(plot.world(), plot.x(), plot.z()));
        save();
    }

    /** Darf der Spieler an dieser Stelle bauen/abbauen/interagieren? */
    public boolean canBuild(Player player, Location loc) {
        if (player.hasPermission("citybuild.bypass")) {
            return true;
        }
        if (loc.getWorld() == null || !isPlotWorld(loc.getWorld())) {
            return true;
        }
        Plot plot = getPlot(loc);
        if (plot != null) {
            return plot.isMember(player.getUniqueId());
        }
        return plugin.getConfig().getBoolean("plots.build-outside-plots", false);
    }
}
