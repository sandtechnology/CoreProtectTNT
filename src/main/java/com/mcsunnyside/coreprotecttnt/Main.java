package com.mcsunnyside.coreprotecttnt;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class Main extends JavaPlugin implements Listener {
    private HashMap<Entity, String> explosionSources = new HashMap<>();
    private Map<Location, String> ignitedBlocks = new HashMap<>();
    private CoreProtectAPI api;

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
        new BukkitRunnable() {
            @Override
            public void run() {
                if (ignitedBlocks.size() > 1000) {
                    ignitedBlocks.clear();
                }
                if (explosionSources.size() > 1000) {
                    ArrayList<Entity> toRemove = new ArrayList<>();
                    explosionSources.keySet().forEach(entity -> {
                        if (!entity.isValid() || entity.isDead()) {
                            toRemove.add(entity);
                        }
                    });
                    // These entities may still trigger some events or act as a tnt source, so make a 30-second delay
                    getServer().getScheduler().scheduleSyncDelayedTask(Main.this, () -> {
                        toRemove.forEach(explosionSources::remove);
                    }, 20 * 30);
                }
            }
        }.runTaskTimerAsynchronously(this, 0, 20 * 60);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onFireballLaunch(ProjectileLaunchEvent e) {
        if (e.getEntity().getShooter() != null) {
            if (e.getEntityType() == EntityType.FIREBALL) {
                String source = ((Entity) e.getEntity().getShooter()).getType().name();
                if (e.getEntity().getShooter() instanceof Ghast && ((Ghast) e.getEntity().getShooter()).getTarget() != null) {
                    Entity target = ((Ghast) e.getEntity().getShooter()).getTarget();
                    String targetName = target.getType() == EntityType.PLAYER ? target.getName() : target.getType().name();
                    source += "->" + targetName;
                }
                explosionSources.put(e.getEntity(), source);
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
                if (explosionSources.containsKey(source)) {
                    explosionSources.put(tnt, explosionSources.get(source));
                    return;
                }
                if (source.getType() == EntityType.PLAYER) {
                    explosionSources.put(tntPrimed, source.getName());
                    return;
                }
            }
            Location blockCorner = tnt.getLocation().clone().subtract(0.5, 0, 0.5);
            for (Map.Entry<Location, String> entry : ignitedBlocks.entrySet()) {
                if (entry.getKey().distance(blockCorner) < 0.5) {
                    explosionSources.put(tnt, entry.getValue());
                    break;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEndCrystalHit(EntityDamageByEntityEvent e) {
        if (e.getEntityType() == EntityType.ENDER_CRYSTAL) {
            if (e.getDamager().getType() == EntityType.PLAYER) {
                explosionSources.put(e.getEntity(), e.getDamager().getName());
            } else {
                if (explosionSources.containsKey(e.getDamager())) {
                    explosionSources.put(e.getEntity(), explosionSources.get(e.getDamager()));
                } else if (e.getDamager() instanceof Projectile) {
                    Projectile projectile = (Projectile) e.getDamager();
                    if (projectile.getShooter() != null && projectile.getShooter() instanceof Player) {
                        explosionSources.put(e.getEntity(), ((Player) projectile.getShooter()).getName());
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockIgnite(BlockIgniteEvent e) {
        if (e.getIgnitingEntity() != null) {
            if (e.getIgnitingEntity().getType() == EntityType.PLAYER) {
                ignitedBlocks.put(e.getBlock().getLocation(), e.getPlayer().getName());
                return;
            }
            if (explosionSources.containsKey(e.getIgnitingEntity())) {
                ignitedBlocks.put(e.getBlock().getLocation(), explosionSources.get(e.getIgnitingEntity()));
            } else if (e.getIgnitingEntity() instanceof Projectile) {
                if (((Projectile) e.getIgnitingEntity()).getShooter() != null) {
                    ProjectileSource shooter = ((Projectile) e.getIgnitingEntity()).getShooter();
                    if (shooter instanceof Player) {
                        ignitedBlocks.put(e.getBlock().getLocation(), ((Player) shooter).getName());
                        return;
                    }
                }
            }
        }
        if (e.getIgnitingBlock() != null) {
            if (ignitedBlocks.containsKey(e.getIgnitingBlock().getLocation())) {
                ignitedBlocks.put(e.getBlock().getLocation(), ignitedBlocks.get(e.getIgnitingBlock().getLocation()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        if (e.getIgnitingBlock() != null) {
            if (ignitedBlocks.containsKey(e.getIgnitingBlock().getLocation())) {
                ignitedBlocks.put(e.getBlock().getLocation(), ignitedBlocks.get(e.getIgnitingBlock().getLocation()));
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplodableHit(ProjectileHitEvent e) {
        if (e.getHitEntity() != null) {
            if (e.getHitEntity() instanceof ExplosiveMinecart || e.getEntityType() == EntityType.ENDER_CRYSTAL) {
                if (e.getEntity().getShooter() != null && e.getEntity().getShooter() instanceof Player) {
                    if (explosionSources.containsKey(e.getEntity())) {
                        explosionSources.put(e.getHitEntity(), explosionSources.get(e.getEntity()));
                    } else {
                        if (e.getEntity().getShooter() != null && e.getEntity().getShooter() instanceof Player) {
                            explosionSources.put(e.getHitEntity(), ((Player) e.getEntity().getShooter()).getName());
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        Entity tnt = e.getEntity();
        List<Block> blockList = e.blockList();
        if (blockList.isEmpty())
            return;
        List<Entity> pendingRemoval = new ArrayList<>();
        String configTntName = e.getEntityType() == EntityType.ENDER_CRYSTAL ? "endercrystal" : "tnt";
        if (tnt instanceof TNTPrimed || tnt instanceof EnderCrystal) {
            if (!getConfig().getBoolean(configTntName + ".log"))
                return;
            String tntName = tnt.getType() == EntityType.PRIMED_TNT ? "TNT" : "EndCrystal";

            if (explosionSources.containsKey(tnt)) {
                for (Block block : blockList) {
                    api.logRemoval("#[" + tntName + "]" + explosionSources.get(tnt), block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), explosionSources.get(tnt));
                }
                pendingRemoval.add(tnt);
            } else {
                //Notify players this tnt or end crystal won't break any blocks
                if (!getConfig().getBoolean(configTntName + ".disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                for (Entity entity : entityCollections) {
                    if (entity instanceof Player)
                        entity.sendMessage(getConfig().getString("msgs." + configTntName + "-wont-break-blocks"));
                }
            }
        }
        if (tnt instanceof Creeper) {
            if (!getConfig().getBoolean("creeper.log"))
                return;
            Creeper creeper = (Creeper) tnt;
            LivingEntity creeperTarget = creeper.getTarget();
            if (creeperTarget != null) {
                for (Block block : blockList) {
                    api.logRemoval("#[Creeper]" + creeperTarget.getName(), block.getLocation(), block.getType(), block.getBlockData());
                }
            } else {
                //Notify players this creeper won't break any blocks
                if (!getConfig().getBoolean("creeper.disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                for (Entity entity : entityCollections) {
                    if (entity instanceof Player)
                        entity.sendMessage(getConfig().getString("msgs.creeper-wont-break-blocks"));
                }
            }
        }
        if (tnt instanceof Fireball) {
            if (!getConfig().getBoolean("fireball.log"))
                return;
            if (explosionSources.containsKey(tnt)) {
                for (Block block : blockList) {
                    api.logRemoval("#[Fireball]" + explosionSources.get(tnt), block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), explosionSources.get(tnt));
                }
                pendingRemoval.add(tnt);
            } else {
                if (getConfig().getBoolean("fireball.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(getConfig().getString("msgs.fireball-wont-break-blocks"));
                    }
                } else {
                    for (Block block : blockList) {
                        api.logRemoval("#[Fireball]" + "MissingNo", block.getLocation(), block.getType(), block.getBlockData());
                    }
                }
            }
        }
        if (tnt instanceof ExplosiveMinecart) {
            boolean isLogged = false;
            Location blockCorner = tnt.getLocation().clone().subtract(0.5, 0, 0.5);
            for (Map.Entry<Location, String> entry : ignitedBlocks.entrySet()) {
                if (entry.getKey().distance(blockCorner) < 1) {
                    for (Block block : blockList) {
                        api.logRemoval("#[MinecartTNT]" + entry.getValue(), block.getLocation(), block.getType(), block.getBlockData());
                        ignitedBlocks.put(block.getLocation(), entry.getValue());
                    }
                    isLogged = true;
                    break;
                }
            }
            if (!isLogged) {
                if (explosionSources.containsKey(tnt)) {
                    for (Block block : blockList) {
                        api.logRemoval("#[MinecartTNT]" + explosionSources.get(tnt), block.getLocation(), block.getType(), block.getBlockData());
                        ignitedBlocks.put(block.getLocation(), explosionSources.get(tnt));
                    }
                    pendingRemoval.add(tnt);
                } else if (getConfig().getBoolean("tntminecart.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(getConfig().getString("msgs.tntminecart-wont-break-blocks"));
                    }
                }
            }
        }
        pendingRemoval.forEach(explosionSources::remove);
    }
}