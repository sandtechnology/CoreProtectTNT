package com.mcsunnyside.coreprotecttnt;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
            .concurrencyLevel(2) // Sync and Async threads
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = e.getClickedBlock();
        Location locationHead = clickedBlock.getLocation();

        if (clickedBlock.getBlockData() instanceof Bed) {
            Bed bed = (Bed) clickedBlock.getBlockData();
            Location locationFoot = locationHead.clone().subtract(bed.getFacing().getDirection());
            if (bed.getPart() == Bed.Part.FOOT) {
                locationHead.add(bed.getFacing().getDirection());
            }
            String reason = "#bed-" + e.getPlayer().getName();
            //api.logRemoval(reason, locationHead, clickedBlock.getType(), bed); // head
            //api.logRemoval(reason, locationFoot, clickedBlock.getType(), clickedBlock.getBlockData());

            probablyCache.put(locationHead, reason);
            probablyCache.put(locationFoot, reason);
        }

        if (clickedBlock.getBlockData() instanceof RespawnAnchor) {
            probablyCache.put(clickedBlock.getLocation(), "#respawn-anchor-" + e.getPlayer().getName());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Creeper) {
            probablyCache.put(e.getRightClicked(), "#ignite-creeper-" + e.getPlayer().getName());
        }
    }

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
                e.setCancelled(true);
                Util.broadcastNearPlayers(location, section.getString("alert"));
            }
        }

        // Found causes, let's begin for logging
        for (Block block : e.blockList()) {
            api.logRemoval(probablyCauses, block.getLocation(), block.getType(), block.getBlockData());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFireballLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() != null) {
            if (e.getEntityType() == EntityType.FIREBALL) {
                String source = "#" + ((Entity) e.getEntity().getShooter()).getType().name() + "-fireball";
                if (e.getEntity().getShooter() instanceof Ghast && ((Ghast) e.getEntity().getShooter()).getTarget() != null) {
                    Entity target = ((Ghast) e.getEntity().getShooter()).getTarget();
                    String targetName = target.getType() == EntityType.PLAYER ? target.getName() : target.getType().name();
                    source += "-" + targetName;
                }
                probablyCache.put(e.getEntity(), source);
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgnite(EntitySpawnEvent e) {
        Entity tnt = e.getEntity();
        if (e.getEntity() instanceof TNTPrimed) {
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
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEndCrystalHit(EntityDamageByEntityEvent e) {
        if (e.getEntityType() == EntityType.ENDER_CRYSTAL) {
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

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplodableHit(ProjectileHitEvent e) {
        if (e.getHitEntity() != null) {
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
        // Entity or EnderCrystal
        if (entity instanceof TNTPrimed || entity instanceof EnderCrystal) {
            if (probablyCache.getIfPresent(entity) != null) {
                String reason = "#" + entityName + "-" + probablyCache.getIfPresent(entity);
                for (Block block : blockList) {
                    api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                    probablyCache.put(block.getLocation(), reason);
                }
                pendingRemoval.add(entity);
            } else {
                //Notify players this tnt or end crystal won't break any blocks
                if (!section.getBoolean("disable-unknown", true))
                    return;
                e.setCancelled(true);
                e.setYield(0.0f);
                Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
            }
        }
        // Creeper... aww man
        if (entity instanceof Creeper) {
            // New added: Player ignite creeper
            if (probablyCache.getIfPresent(entity) != null) {
                for (Block block : blockList) {
                    api.logRemoval(probablyCache.getIfPresent(entity), block.getLocation(), block.getType(), block.getBlockData());
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
                    e.setCancelled(true);
                    e.setYield(0.0f);
                    Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
                }
            }
        }
        if (entity instanceof Fireball) {
            if (probablyCache.getIfPresent(entity) != null) {
                String reason = "#fireball-" + probablyCache.getIfPresent(entity);
                for (Block block : blockList) {
                    api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                    probablyCache.put(block.getLocation(), reason);
                }
                pendingRemoval.add(entity);
            } else {
                if (section.getBoolean("disable-unknown")) {
                    e.setCancelled(true);
                    e.setYield(0.0f);
                    Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                } else {
                    for (Block block : blockList) {
                        api.logRemoval("#fireball-" + "MissingNo", block.getLocation(), block.getType(), block.getBlockData());
                    }
                    blockList.forEach(b -> probablyCache.put(b.getLocation(), "#fireball-" + "MissingNo"));
                }
            }
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
                    e.setCancelled(true);
                    e.setYield(0.0f);
                    Util.broadcastNearPlayers(entity.getLocation(), section.getString("alert"));
                }
            }
        }
        pendingRemoval.forEach(probablyCache::invalidate);
    }
}