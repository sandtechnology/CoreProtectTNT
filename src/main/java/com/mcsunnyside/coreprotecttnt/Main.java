package com.mcsunnyside.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin implements Listener {
    private CoreProtectAPI api;
    private final Cache<Object, String> probablyCache = CacheBuilder
            .newBuilder()
            .expireAfterAccess(1, TimeUnit.HOURS)
            .concurrencyLevel(4) // Sync and Async threads
            .maximumSize(50000) // Drop objects if too much, because it will cost expensive lookup.
            .recordStats()
            .build();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        Plugin depend = Bukkit.getPluginManager().getPlugin("CoreProtect");
        if (depend == null) {
            getPluginLoader().disablePlugin(this);
            return;
        }
        api = ((CoreProtect) depend).getAPI();
    }

    // Bed/RespawnAnchor explosion (tracing)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractBedOrRespawnAnchorExplosion(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK)
            return;
        Block clickedBlock = e.getClickedBlock();
        Location locationHead = clickedBlock.getLocation();
        if (clickedBlock.getBlockData() instanceof Bed) {
            Bed bed = (Bed) clickedBlock.getBlockData();
            Location locationFoot = locationHead.clone().subtract(bed.getFacing().getDirection());
            if (bed.getPart() == Bed.Part.FOOT) {
                locationHead.add(bed.getFacing().getDirection());
            }
            String reason = "#bed-" + e.getPlayer().getName();
            probablyCache.put(locationHead, reason);
            probablyCache.put(locationFoot, reason);
        }
        if (clickedBlock.getBlockData() instanceof RespawnAnchor) {
            probablyCache.put(clickedBlock.getLocation(), "#respawnanchor-" + e.getPlayer().getName());
        }
    }

    // Creeper ignite (tracing)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Creeper))
            return;
        probablyCache.put(e.getRightClicked(), "#ignitecreeper-" + e.getPlayer().getName());
    }

    // Block explode (logger)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "block-explosion");
        if (!section.getBoolean("enable", true))
            return;
        Location location = e.getBlock().getLocation();
        String probablyCauses = probablyCache.getIfPresent(e.getBlock());
        if (probablyCauses == null)
            probablyCauses = probablyCache.getIfPresent(location);
        if (probablyCauses == null) {
            if (section.getBoolean("disable-unknown", true)) {
                e.blockList().clear();
                Util.broadcastNearPlayers(location, section.getString("alert"));
            }
        }
        // Found causes, let's begin for logging
        for (Block block : e.blockList()) {
            api.logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlaceOnHanging(BlockPlaceEvent event) {
        // We can't check the hanging in this event, may cause server lagging, just store it
        probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockPlaceEvent event) {
        // We can't check the hanging in this event, may cause server lagging, just store it
        // Maybe a player break the tnt and a plugin igniting it?
        probablyCache.put(event.getBlock().getLocation(), event.getPlayer().getName());
    }

    // Player item put into ItemFrame / Rotate ItemFrame (logger)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onClickItemFrame(PlayerInteractEntityEvent e) { // Add item to item-frame or rotating
        if (!(e.getRightClicked() instanceof ItemFrame))
            return;
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "itemframe");
        if (!section.getBoolean("enable", true))
            return;
        ItemFrame itemFrame = (ItemFrame) e.getRightClicked();
        // Player interacted itemframe
        api.logInteraction(e.getPlayer().getName(), e.getRightClicked().getLocation());
        // Check item I/O
        if (itemFrame.getItem().getType().isAir()) { // Probably put item now
            ItemStack mainItem = e.getPlayer().getInventory().getItemInMainHand();
            ItemStack offItem = e.getPlayer().getInventory().getItemInOffHand();
            ItemStack putIn = mainItem.getType().isAir() ? offItem : mainItem;
            if (!putIn.getType().isAir()) {
                // Put in item
                api.logPlacement("#additem-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), putIn.getType(), null);
                return;
            }
        }
        // Probably rotating ItemFrame
        api.logRemoval("#rotate-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), itemFrame.getItem().getType(), null);
        api.logPlacement("#rotate-" + e.getPlayer().getName(), e.getRightClicked().getLocation(), itemFrame.getItem().getType(), null);
    }

    // Any projectile shoot (listener)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onProjectileLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() == null)
            return;
        ProjectileSource projectileSource = e.getEntity().getShooter();
        String source = "";
        if (!(projectileSource instanceof Player))
            source += "#"; // We only hope non-player object use hashtag
        source += e.getEntity().getName() + "-";
        if (projectileSource instanceof Entity) {
            if (projectileSource instanceof Mob && ((Mob) projectileSource).getTarget() != null) {
                source += ((Mob) projectileSource).getTarget().getName();
            } else {
                source += ((Entity) projectileSource).getName();
            }
            probablyCache.put(projectileSource, source);
        } else {
            if (projectileSource instanceof Block) {
                source += ((Block) projectileSource).getType().name();
                probablyCache.put(((Block) projectileSource).getLocation(), source);
            } else {
                source += projectileSource.getClass().getName();
                probablyCache.put(projectileSource, source);
            }
        }
    }

    // TNT ignites by Player (listener)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgniteTNT(EntitySpawnEvent e) {
        Entity tnt = e.getEntity();
        if (!(e.getEntity() instanceof TNTPrimed))
            return;
        TNTPrimed tntPrimed = (TNTPrimed) e.getEntity();
        Entity source = tntPrimed.getSource();
        if (source != null) {
            //Bukkit has given the ignition source, track it directly.
            if (probablyCache.getIfPresent(source) != null) {
                probablyCache.put(tnt, probablyCache.getIfPresent(source));
            }
            if (source.getType() == EntityType.PLAYER) {
                probablyCache.put(tntPrimed, source.getName());
                return;
            }
        }
        Location blockCorner = tnt.getLocation().clone().subtract(0.5, 0, 0.5);
        for (Map.Entry<Object, String> entry : probablyCache.asMap().entrySet()) {
            if (entry.getKey() instanceof Location) {
                Location loc = (Location) entry.getKey();
                if (loc.getWorld().equals(blockCorner.getWorld()) && loc.distance(blockCorner) < 0.5) {
                    probablyCache.put(tnt, entry.getValue());
                    break;
                }
            }
        }
    }

    // HangingBreak (logger)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingBreak(HangingBreakEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "hanging");
        if (!section.getBoolean("enable", true))
            return;
        if (e.getCause() == HangingBreakEvent.RemoveCause.PHYSICS || e.getCause() == HangingBreakEvent.RemoveCause.DEFAULT)
            return; // We can't track them tho.

        Block hangingPosBlock = e.getEntity().getLocation().getBlock();
        String reason = probablyCache.getIfPresent(hangingPosBlock.getLocation());
        if (reason != null) {
            api.logRemoval("#" + e.getCause().name() + "-" + reason, hangingPosBlock.getLocation(), Material.matchMaterial(e.getEntity().getType().name()), null);
        }
    }

    // EndCrystal rigged by entity (listener)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEndCrystalHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof EnderCrystal))
            return;
        if (e.getDamager() instanceof Player) {
            probablyCache.put(e.getEntity(), e.getDamager().getName());
        } else {
            if (probablyCache.getIfPresent(e.getDamager()) != null) {
                probablyCache.put(e.getEntity(), probablyCache.getIfPresent(e.getDamager()));
            } else if (e.getDamager() instanceof Projectile) {
                Projectile projectile = (Projectile) e.getDamager();
                if (projectile.getShooter() != null && projectile.getShooter() instanceof Player) {
                    probablyCache.put(e.getEntity(), ((Player) projectile.getShooter()).getName());
                }
            }
        }
    }

    // Haning hit by entity (logger)
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHangingHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Hanging))
            return;
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "itemframe");
        if (!section.getBoolean("enable", true))
            return;
        ItemFrame itemFrame = (ItemFrame) e.getEntity();
        if (itemFrame.getItem().getType().isAir() || itemFrame.isInvulnerable())
            return;
        if (e.getDamager() instanceof Player) {
            probablyCache.put(e.getEntity(), e.getDamager().getName());
            api.logInteraction(e.getDamager().getName(), itemFrame.getLocation());
            api.logRemoval(e.getDamager().getName(), itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        } else {
            String cause = probablyCache.getIfPresent(e.getDamager());
            if (cause != null) {
                String reason = "#" + e.getDamager().getName() + "-" + cause;
                probablyCache.put(e.getEntity(), reason);
                api.logRemoval(reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPaintingHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Painting))
            return;
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "painting");
        if (!section.getBoolean("enable", true))
            return;
        ItemFrame itemFrame = (ItemFrame) e.getEntity();
        if (itemFrame.getItem().getType().isAir() || itemFrame.isInvulnerable())
            return;

        if (e.getDamager() instanceof Player) {
            api.logInteraction(e.getDamager().getName(), itemFrame.getLocation());
            api.logRemoval(e.getDamager().getName(), itemFrame.getLocation(), itemFrame.getItem().getType(), null);
        } else {
            String reason = probablyCache.getIfPresent(e.getDamager());
            if (reason != null) {
                api.logInteraction("#" + e.getDamager().getName() + "-" + reason, itemFrame.getLocation());
                api.logRemoval("#" + e.getDamager().getName() + "-" + reason, itemFrame.getLocation(), itemFrame.getItem().getType(), null);
            } else {
                if (section.getBoolean("disable-unknown")) {
                    e.setCancelled(true);
                    e.setDamage(0.0d);
                    Util.broadcastNearPlayers(e.getEntity().getLocation(), section.getString("alert"));
                }
            }
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onEntityHitByProjectile(EntityDamageByEntityEvent e) {
       if (e.getDamager() instanceof Projectile) {
           Projectile projectile = (Projectile)e.getDamager();
           if(projectile.getShooter() instanceof Player){
               probablyCache.put(e.getEntity(),((Player) projectile.getShooter()).getName());
               return;
           }
            String reason = probablyCache.getIfPresent(e.getDamager());
            if (reason != null) {
               probablyCache.put(e.getEntity(),reason);
               return;
            }
            probablyCache.put(e.getEntity(),e.getDamager().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getIgnitingEntity() != null) {
            if (e.getIgnitingEntity().getType() == EntityType.PLAYER) {
                probablyCache.put(e.getBlock().getLocation(), e.getPlayer().getName());
                // Don't add it to probablyIgnitedThisTick because it's the simplest case and is logged by Core Protect
                return;
            }
            if (probablyCache.getIfPresent(e.getIgnitingEntity()) != null) {
                probablyCache.put(e.getBlock().getLocation(), probablyCache.getIfPresent(e.getIgnitingEntity()));
                return;
            } else if (e.getIgnitingEntity() instanceof Projectile) {
                if (((Projectile) e.getIgnitingEntity()).getShooter() != null) {
                    ProjectileSource shooter = ((Projectile) e.getIgnitingEntity()).getShooter();
                    if (shooter instanceof Player) {
                        probablyCache.put(e.getBlock().getLocation(), ((Player) shooter).getName());
                        return;
                    }
                }
            }
        }
        if (e.getIgnitingBlock() != null) {
            if (probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()) != null) {
                probablyCache.put(e.getBlock().getLocation(), probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()));
                return;
            }
        }
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "fire");
        if (!section.getBoolean("enable", true))
            return;
        if (!section.getBoolean("disable-unknown", true))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "fire");
        if (!section.getBoolean("enable", true))
            return;
        if (e.getIgnitingBlock() != null) {
            if (probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()) != null) {
                probablyCache.put(e.getBlock().getLocation(), probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()));
                api.logRemoval("#fire-" + probablyCache.getIfPresent(e.getIgnitingBlock().getLocation()), e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
            } else if (section.getBoolean("disable-unknown", true)) {
                e.setCancelled(true);
                Util.broadcastNearPlayers(e.getIgnitingBlock().getLocation(), section.getString("alert"));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBombHit(ProjectileHitEvent e) {
        if (e.getHitEntity() instanceof ExplosiveMinecart || e.getEntityType() == EntityType.ENDER_CRYSTAL) {
            if (e.getEntity().getShooter() != null && e.getEntity().getShooter() instanceof Player) {
                if (probablyCache.getIfPresent(e.getEntity()) != null) {
                    probablyCache.put(e.getHitEntity(), probablyCache.getIfPresent(e.getEntity()));
                } else {
                    if (e.getEntity().getShooter() != null && e.getEntity().getShooter() instanceof Player) {
                        probablyCache.put(e.getHitEntity(), ((Player) e.getEntity().getShooter()).getName());
                    }
                }
            }
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        Entity entity = e.getEntity();
        List<Block> blockList = e.blockList();
        if (blockList.isEmpty())
            return;
        List<Entity> pendingRemoval = new ArrayList<>();
        String entityName = e.getEntityType().name().toLowerCase(Locale.ROOT);
        ConfigurationSection section = Util.bakeConfigSection(getConfig(), "entity-explosion");
        if (!section.getBoolean("enable", true))
            return;
        String track = probablyCache.getIfPresent(entity);
        // TNT or EnderCrystal
        if (entity instanceof TNTPrimed || entity instanceof EnderCrystal) {
            if (track != null) {
                String reason = "#" + entityName + "-" + track;
                for (Block block : blockList) {
                    api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                    probablyCache.put(block.getLocation(), reason);
                }
                pendingRemoval.add(entity);
            } else {
                //Notify players this tnt or end crystal won't break any blocks
                if (!section.getBoolean("disable-unknown", true))
                    return;
                e.blockList().clear();
                e.getEntity().remove();
                Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
            }
            pendingRemoval.forEach(probablyCache::invalidate);
            return;
        }
        // Creeper... aww man
        if (entity instanceof Creeper) {
            // New added: Player ignite creeper
            if (track != null) {
                for (Block block : blockList) {
                    api.logRemoval(track, block.getLocation(), block.getType(), block.getBlockData());
                }
            } else {
                Creeper creeper = (Creeper) entity;
                LivingEntity creeperTarget = creeper.getTarget();
                if (creeperTarget != null) {
                    for (Block block : blockList) {
                        api.logRemoval("#creeper-" + creeperTarget.getName(), block.getLocation(), block.getType(), block.getBlockData());
                        probablyCache.put(block.getLocation(), "#creeper-" + creeperTarget.getName());
                    }
                } else {
                    //Notify players this creeper won't break any blocks
                    if (!section.getBoolean("disable-unknown"))
                        return;
                    e.blockList().clear();
                    e.getEntity().remove();
                    Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
                    return;
                }
            }
            return;
        }
        if (entity instanceof Fireball) {
            if (track != null) {
                String reason = "#fireball-" + track;
                for (Block block : blockList) {
                    api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                    probablyCache.put(block.getLocation(), reason);
                }
                pendingRemoval.add(entity);
            } else {
                if (section.getBoolean("disable-unknown")) {
                    e.blockList().clear();
                    e.getEntity().remove();
                    Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                }
            }
            pendingRemoval.forEach(probablyCache::invalidate);
            return;
        }
        if (entity instanceof ExplosiveMinecart) {
            boolean isLogged = false;
            Location blockCorner = entity.getLocation().clone().subtract(0.5, 0, 0.5);
            for (Map.Entry<Object, String> entry : probablyCache.asMap().entrySet()) {
                if (entry.getKey() instanceof Location) {
                    Location loc = (Location) entry.getKey();
                    if (loc.getWorld().equals(blockCorner.getWorld()) && loc.distance(blockCorner) < 1) {
                        for (Block block : blockList) {
                            api.logRemoval("#tntminecart-" + entry.getValue(), block.getLocation(), block.getType(), block.getBlockData());
                            probablyCache.put(block.getLocation(), "#tntminecart-" + entry.getValue());
                        }
                        isLogged = true;
                        break;
                    }
                }
            }
            if (!isLogged) {
                if (probablyCache.getIfPresent(entity) != null) {
                    String reason = "#tntminecart-" + probablyCache.getIfPresent(entity);
                    for (Block block : blockList) {
                        api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                        probablyCache.put(block.getLocation(), reason);
                    }
                    pendingRemoval.add(entity);
                } else if (section.getBoolean("disable-unknown")) {
                    e.blockList().clear();
                    Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                }
            }
            pendingRemoval.forEach(probablyCache::invalidate);
            return;
        }
        if (track == null || track.isEmpty()) {
            if (e.getEntity() instanceof Mob && ((Mob) e.getEntity()).getTarget() != null)
                track = ((Mob) e.getEntity()).getTarget().getName();
        }
        // No matches, plugin explode or cannot to track?
        if (track == null || track.isEmpty()) {
            EntityDamageEvent cause = e.getEntity().getLastDamageCause();
            if (cause != null) {
                if (cause instanceof EntityDamageByEntityEvent) {
                    track = "#" + e.getEntity().getName() + "-" + ((EntityDamageByEntityEvent) cause).getDamager().getName();
                }
            }
        }

        if (track != null && !track.isEmpty()) {
            for (Block block : e.blockList()) {
                api.logRemoval(track, block.getLocation(), block.getType(), block.getBlockData());
            }
        } else if (section.getBoolean("disable-unknown")) {
            e.blockList().clear();
            e.getEntity().remove();
            Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
        }
    }
}