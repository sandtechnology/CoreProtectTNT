package com.mcsunnyside.coreprotecttnt;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.RespawnAnchor;
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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements Listener {
    private final HashMap<Entity, String> explosionSources = new HashMap<>();
    private final HashMap<Location, String> ignitedBlocks = new HashMap<>();
    private final HashMap<Location, String> beds = new HashMap<>();
    private final HashMap<Location, String> respawnAnchors = new HashMap<>();
    private final ArrayList<Block> probablyIgnitedThisTick = new ArrayList<>();
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
                List<Location> blocks = ignitedBlocks.keySet()
                        .stream()
                        .filter(block -> block.getBlock().getType() != Material.FIRE)
                        .collect(Collectors.toList());
                if (!blocks.isEmpty()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            blocks
                                    .stream()
                                    .filter(block -> block.getBlock().getType() != Material.FIRE)
                                    .forEach(ignitedBlocks::remove);
                        }
                    }.runTaskLater(Main.this, 42);
                }
                if (ignitedBlocks.size() > 10000) {
                    List<Location> toRemove = new ArrayList<>();
                    ignitedBlocks.keySet().stream().filter(block -> block.getBlock().getType() != Material.FIRE).forEach(toRemove::add);
                    toRemove.forEach(ignitedBlocks::remove);

                    if (ignitedBlocks.size() > 9000) {
                        ignitedBlocks.clear();
                    }
                }
                if (explosionSources.size() > 1000) {
                    ArrayList<Entity> toRemove = new ArrayList<>();
                    explosionSources.keySet().forEach(entity -> {
                        if (!entity.isValid() || entity.isDead()) {
                            toRemove.add(entity);
                        }
                    });
                    // These entities may still trigger some events or act as a tnt source, so make a 30-second delay
                    getServer().getScheduler().scheduleSyncDelayedTask(Main.this, () -> toRemove.forEach(explosionSources::remove), 20 * 30);
                }
            }
        }.runTaskTimer(this, 0, 20 * 60);
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (getConfig().getBoolean("fire.log")) {
                        for (Block block : probablyIgnitedThisTick) {
                            if (block.getType() == Material.FIRE) {
                                api.logPlacement("[Fire]" + ignitedBlocks.get(block.getLocation()), block.getLocation(), Material.FIRE, block.getBlockData());
                            }
                        }
                    }
                    probablyIgnitedThisTick.clear();
                }
            }.runTaskTimer(this, 1, 1);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Block clickedBlock = e.getClickedBlock();
        Location locationHead = clickedBlock.getLocation();

        if (clickedBlock.getLocation().getWorld().getEnvironment() != World.Environment.NORMAL
                && clickedBlock.getBlockData() instanceof Bed
                && getConfig().getBoolean("bed.log")) {
            Bed data = (Bed) clickedBlock.getBlockData();
            Location locationFoot = locationHead.clone().subtract(data.getFacing().getDirection());
            if (data.getPart() == Bed.Part.FOOT) {
                locationHead.add(data.getFacing().getDirection());
            }

            beds.put(locationHead, e.getPlayer().getName());
            probablyIgnitedThisTick.add(locationHead.getBlock());
            probablyIgnitedThisTick.add(locationFoot.getBlock());
            api.logRemoval("#[Bed]" + e.getPlayer().getName(), locationHead, clickedBlock.getType(), data); // head
            api.logRemoval("#[Bed]" + e.getPlayer().getName(), locationFoot, clickedBlock.getType(), clickedBlock.getBlockData()); // foot
        } else if (clickedBlock.getType().name().equals("RESPAWN_ANCHOR") // support old versions
                && clickedBlock.getLocation().getWorld().getEnvironment() != World.Environment.NETHER
                && getConfig().getBoolean("respawn-anchor.log")) {
            RespawnAnchor data = (RespawnAnchor) clickedBlock.getBlockData();
            if (data.getCharges() == data.getMaximumCharges()) {
                respawnAnchors.put(clickedBlock.getLocation(), e.getPlayer().getName());
                probablyIgnitedThisTick.add(clickedBlock);
                api.logRemoval("#[RespawnAnchor]" + e.getPlayer().getName(), locationHead, clickedBlock.getType(), data);
            }
        }

    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (beds.containsKey(e.getBlock().getLocation())) {
            if (getConfig().getBoolean("bed.log")) {
                String source = beds.get(e.getBlock().getLocation());
                beds.remove(e.getBlock().getLocation());

                for (Block block : e.blockList()) {
                    api.logRemoval("#[Bed]" + source, block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), source);
                }
                probablyIgnitedThisTick.addAll(e.blockList());
            }
        } else if (respawnAnchors.containsKey(e.getBlock().getLocation())) {
            if (getConfig().getBoolean("respawn-anchor.log")) {
                String source = respawnAnchors.get(e.getBlock().getLocation());
                respawnAnchors.remove(e.getBlock().getLocation());

                for (Block block : e.blockList()) {
                    api.logRemoval("#[RespawnAnchor]" + source, block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), source);
                }
                probablyIgnitedThisTick.addAll(e.blockList());
            }
        } else {
            // No idea why this could happen, but why not to handle this 0.01%
            if (e.getBlock().getBlockData() instanceof Bed) {
                if (getConfig().getBoolean("bed.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("msgs.bed-wont-break-blocks")
                                    )
                            );
                    }
                }
            } else if (e.getBlock().getType() == Material.RESPAWN_ANCHOR) {
                if (getConfig().getBoolean("respawn-anchor.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("msgs.respawn-anchor-wont-break-blocks")
                                    )
                            );
                    }
                }
            } else {
                if (getConfig().getBoolean("other-explosive-blocks.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("msgs.other-explosive-block-wont-break-blocks")
                                    )
                            );
                    }
                }
            }
        }
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
                if (entry.getKey().getWorld().equals(blockCorner.getWorld()) && entry.getKey().distance(blockCorner) < 0.5) {
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
                // Don't add it to probablyIgnitedThisTick because it's the simplest case and is logged by Core Protect
                return;
            }
            if (explosionSources.containsKey(e.getIgnitingEntity())) {
                ignitedBlocks.put(e.getBlock().getLocation(), explosionSources.get(e.getIgnitingEntity()));
                probablyIgnitedThisTick.add(e.getBlock());
                return;
            } else if (e.getIgnitingEntity() instanceof Projectile) {
                if (((Projectile) e.getIgnitingEntity()).getShooter() != null) {
                    ProjectileSource shooter = ((Projectile) e.getIgnitingEntity()).getShooter();
                    if (shooter instanceof Player) {
                        ignitedBlocks.put(e.getBlock().getLocation(), ((Player) shooter).getName());
                        probablyIgnitedThisTick.add(e.getBlock());
                        return;
                    }
                }
            }
        }
        if (e.getIgnitingBlock() != null) {
            if (ignitedBlocks.containsKey(e.getIgnitingBlock().getLocation())) {
                ignitedBlocks.put(e.getBlock().getLocation(), ignitedBlocks.get(e.getIgnitingBlock().getLocation()));
                probablyIgnitedThisTick.add(e.getBlock());
                return;
            }
        }
        
        if(getConfig().getBoolean("fire.disable-spread-when-target-not-found")) {
            e.setCancelled(true);
            Collection<Entity> entityCollections = e.getBlock().getLocation().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
            for (Entity entity : entityCollections) {
                if (entity instanceof Player)
                    entity.sendMessage(
                            ChatColor.translateAlternateColorCodes('&',
                                    getConfig().getString("msgs.fire-wont-spread")
                            )
                    );
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBurn(BlockBurnEvent e) {
        if (e.getIgnitingBlock() != null) {
            if (ignitedBlocks.containsKey(e.getIgnitingBlock().getLocation())) {
                ignitedBlocks.put(e.getBlock().getLocation(), ignitedBlocks.get(e.getIgnitingBlock().getLocation()));
                api.logRemoval("[Fire]" + ignitedBlocks.get(e.getIgnitingBlock().getLocation()), e.getBlock().getLocation(), e.getBlock().getType(), e.getBlock().getBlockData());
            } else if(getConfig().getBoolean("fire.disable-burn-when-target-not-found")) {
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getBlock().getLocation().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
                for (Entity entity : entityCollections) {
                    if (entity instanceof Player)
                        entity.sendMessage(
                                ChatColor.translateAlternateColorCodes('&',
                                        getConfig().getString("msgs.fire-wont-burn-blocks")
                                )
                        );
                }
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
                probablyIgnitedThisTick.addAll(blockList);
                pendingRemoval.add(tnt);
            } else {
                //Notify players this tnt or end crystal won't break any blocks
                if (!getConfig().getBoolean(configTntName + ".disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                for (Entity entity : entityCollections) {
                    if (entity instanceof Player)
                        entity.sendMessage(
                                ChatColor.translateAlternateColorCodes('&',
                                        getConfig().getString("msgs." + configTntName + "-wont-break-blocks")
                                )
                        );
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
                probablyIgnitedThisTick.addAll(blockList);
            } else {
                //Notify players this creeper won't break any blocks
                if (!getConfig().getBoolean("creeper.disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                for (Entity entity : entityCollections) {
                    if (entity instanceof Player)
                        entity.sendMessage(
                                ChatColor.translateAlternateColorCodes('&',
                                        getConfig().getString("msgs.creeper-wont-break-blocks")
                                )
                        );
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
                probablyIgnitedThisTick.addAll(blockList);
            } else {
                if (getConfig().getBoolean("fireball.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("msgs.fireball-wont-break-blocks")
                                    )
                            );
                    }
                } else {
                    for (Block block : blockList) {
                        api.logRemoval("#[Fireball]" + "MissingNo", block.getLocation(), block.getType(), block.getBlockData());
                    }
                    probablyIgnitedThisTick.addAll(blockList);
                }
            }
        }
        if (tnt instanceof ExplosiveMinecart) {
            boolean isLogged = false;
            Location blockCorner = tnt.getLocation().clone().subtract(0.5, 0, 0.5);
            for (Map.Entry<Location, String> entry : ignitedBlocks.entrySet()) {
                if (entry.getKey().getWorld().equals(blockCorner.getWorld()) && entry.getKey().distance(blockCorner) < 1) {
                    for (Block block : blockList) {
                        api.logRemoval("#[MinecartTNT]" + entry.getValue(), block.getLocation(), block.getType(), block.getBlockData());
                        ignitedBlocks.put(block.getLocation(), entry.getValue());
                    }
                    probablyIgnitedThisTick.addAll(blockList);
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
                    probablyIgnitedThisTick.addAll(blockList);
                    pendingRemoval.add(tnt);
                } else if (getConfig().getBoolean("tntminecart.disable-when-target-not-found")) {
                    e.setCancelled(true);
                    Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(), 15, 15, 15);
                    for (Entity entity : entityCollections) {
                        if (entity instanceof Player)
                            entity.sendMessage(
                                    ChatColor.translateAlternateColorCodes('&',
                                            getConfig().getString("msgs.tntminecart-wont-break-blocks")
                                    )
                            );
                    }
                }
            }
        }
        pendingRemoval.forEach(explosionSources::remove);
    }
}