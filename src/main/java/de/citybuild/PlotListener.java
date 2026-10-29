package de.citybuild;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Openable;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class PlotListener implements Listener {

    private final CityBuildPlugin plugin;
    private final PlotManager plots;
    private final Map<UUID, Long> lastDeny = new HashMap<>();

    public PlotListener(CityBuildPlugin plugin) {
        this.plugin = plugin;
        this.plots = plugin.plots();
    }

    private void deny(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastDeny.get(player.getUniqueId());
        if (last != null && now - last < 1500) {
            return;
        }
        lastDeny.put(player.getUniqueId(), now);
        plugin.send(player, "&cDas ist nicht dein Grundstueck. Kaufe eines mit &e/plot claim&c.");
    }

    // ---------- Bauen / Abbauen ----------

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    // ---------- Interaktion ----------

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Block block = e.getClickedBlock();
        if (block == null || !plots.isPlotWorld(block.getWorld())) {
            return;
        }
        Player player = e.getPlayer();

        if (e.getAction() == Action.PHYSICAL) {
            // Ackerland zertrampeln / Eier zerstoeren verhindern
            Material type = block.getType();
            if ((type == Material.FARMLAND || type == Material.TURTLE_EGG)
                    && !plots.canBuild(player, block.getLocation())) {
                e.setCancelled(true);
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (plots.canBuild(player, block.getLocation())) {
            return;
        }

        boolean inForeignPlot = plots.getPlot(block.getLocation()) != null;
        if (isPlacingItem(e.getItem()) || isContainer(block) || (inForeignPlot && isUsable(block))) {
            e.setCancelled(true);
            deny(player);
        }
    }

    private boolean isContainer(Block block) {
        return block.getState(false) instanceof Container;
    }

    private boolean isUsable(Block block) {
        Material type = block.getType();
        return block.getBlockData() instanceof Openable
                || Tag.BUTTONS.isTagged(type)
                || Tag.ANVIL.isTagged(type)
                || type == Material.LEVER
                || type == Material.COMPARATOR
                || type == Material.REPEATER
                || type == Material.DAYLIGHT_DETECTOR
                || type == Material.NOTE_BLOCK
                || type == Material.JUKEBOX
                || type == Material.LECTERN
                || type == Material.FLOWER_POT
                || type == Material.CAKE;
    }

    private boolean isPlacingItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        Material type = item.getType();
        String name = type.name();
        return name.endsWith("_BUCKET")
                || name.endsWith("_SPAWN_EGG")
                || type == Material.FLINT_AND_STEEL
                || type == Material.FIRE_CHARGE
                || type == Material.ARMOR_STAND
                || type == Material.ITEM_FRAME
                || type == Material.GLOW_ITEM_FRAME
                || type == Material.PAINTING
                || type == Material.END_CRYSTAL;
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        Entity entity = e.getRightClicked();
        if (!(entity instanceof Hanging) && !(entity instanceof ArmorStand)) {
            return;
        }
        if (!plots.canBuild(e.getPlayer(), entity.getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent e) {
        if (!plots.canBuild(e.getPlayer(), e.getRightClicked().getLocation())) {
            e.setCancelled(true);
            deny(e.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent e) {
        Player remover = null;
        if (e.getRemover() instanceof Player p1) {
            remover = p1;
        } else if (e.getRemover() instanceof Projectile proj && proj.getShooter() instanceof Player p2) {
            remover = p2;
        }
        if (remover != null && !plots.canBuild(remover, e.getEntity().getLocation())) {
            e.setCancelled(true);
            deny(remover);
        }
    }

    // ---------- Kampf ----------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        Entity victim = e.getEntity();
        if (!plots.isPlotWorld(victim.getWorld())) {
            return;
        }
        Player attacker = null;
        if (e.getDamager() instanceof Player p1) {
            attacker = p1;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2) {
            attacker = p2;
        }
        if (attacker == null) {
            return;
        }

        if (victim instanceof Player) {
            if (!plugin.getConfig().getBoolean("pvp", false)) {
                e.setCancelled(true);
            }
            return;
        }
        if (victim instanceof Enemy) {
            return;
        }
        if (!plots.canBuild(attacker, victim.getLocation())) {
            e.setCancelled(true);
            deny(attacker);
        }
    }

    // ---------- Explosionen, Feuer, Fluessigkeiten, Kolben ----------

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plots.isPlotWorld(e.getLocation().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plots.isPlotWorld(e.getBlock().getWorld())) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent e) {
        if (!plots.isPlotWorld(e.getBlock().getWorld())) {
            return;
        }
        BlockIgniteEvent.IgniteCause cause = e.getCause();
        if (cause == BlockIgniteEvent.IgniteCause.SPREAD
                || cause == BlockIgniteEvent.IgniteCause.LAVA
                || cause == BlockIgniteEvent.IgniteCause.LIGHTNING) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (plots.isPlotWorld(e.getBlock().getWorld())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        if ((from.getX() >> 4) == (to.getX() >> 4) && (from.getZ() >> 4) == (to.getZ() >> 4)) {
            return;
        }
        if (!plots.isPlotWorld(from.getWorld())) {
            return;
        }
        // Wasser/Lava darf nicht von einem Grundstueck ins andere fliessen
        if (!Objects.equals(plots.getPlot(from.getLocation()), plots.getPlot(to.getLocation()))) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (isCrossingPlots(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (isCrossingPlots(e.getBlock(), e.getBlocks(), e.getDirection())) {
            e.setCancelled(true);
        }
    }

    private boolean isCrossingPlots(Block piston, List<Block> moved, BlockFace direction) {
        if (!plots.isPlotWorld(piston.getWorld())) {
            return false;
        }
        PlotManager.Plot home = plots.getPlot(piston.getLocation());
        // Auch der Kolbenkopf landet direkt vor dem Kolben
        if (!Objects.equals(home, plots.getPlot(piston.getRelative(direction).getLocation()))) {
            return true;
        }
        for (Block block : moved) {
            if (!Objects.equals(home, plots.getPlot(block.getLocation()))
                    || !Objects.equals(home, plots.getPlot(block.getRelative(direction).getLocation()))) {
                return true;
            }
        }
        return false;
    }
}
