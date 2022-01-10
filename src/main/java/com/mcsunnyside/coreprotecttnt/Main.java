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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Main extends JavaPlugin implements Listener {
    private final Map<Entity, String> explosionSources = new HashMap<>();
    private final Map<Location, String> ignitedBlocks = new HashMap<>();
    private final Map<Location, String> beds = new HashMap<>();
    private final Map<Location, String> respawnAnchors = new HashMap<>();
    private final List<Block> probablyIgnitedThisTick = new ArrayList<>();
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
        Block block;
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


//
//        if (clickedBlock.getLocation().getWorld().getEnvironment() != World.Environment.NORMAL
//                && clickedBlock.getBlockData() instanceof Bed
//                && getConfig().getBoolean("bed.log")) {
//            Bed data = (Bed) clickedBlock.getBlockData();
//            Location locationFoot = locationHead.clone().subtract(data.getFacing().getDirection());
//            if (data.getPart() == Bed.Part.FOOT) {
//                locationHead.add(data.getFacing().getDirection());
//            }
//
//            beds.put(locationHead, e.getPlayer().getName());
//            probablyIgnitedThisTick.add(locationHead.getBlock());
//            probablyIgnitedThisTick.add(locationFoot.getBlock());
//            api.logRemoval("#[Bed]" + e.getPlayer().getName(), locationHead, clickedBlock.getType(), data); // head
//            api.logRemoval("#[Bed]" + e.getPlayer().getName(), locationFoot, clickedBlock.getType(), clickedBlock.getBlockData()); // foot
//        } else if (clickedBlock.getType().name().equals("RESPAWN_ANCHOR") // support old versions
//                && clickedBlock.getLocation().getWorld().getEnvironment() != World.Environment.NETHER
//                && getConfig().getBoolean("respawn-anchor.log")) {
//            RespawnAnchor data = (RespawnAnchor) clickedBlock.getBlockData();
//            if (data.getCharges() == data.getMaximumCharges()) {
//                respawnAnchors.put(clickedBlock.getLocation(), e.getPlayer().getName());
//                probablyIgnitedThisTick.add(clickedBlock);
//                api.logRemoval("#[RespawnAnchor]" + e.getPlayer().getName(), locationHead, clickedBlock.getType(), data);
//            }
//        }

    }

    @EventHandler
    public void onPlayerInteractCreeper(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Creeper) {
            if (e.getPlayer().getInventory().getItemInMainHand().getType() == Material.FLINT_AND_STEEL || e.getPlayer().getInventory().getItemInOffHand().getType() == Material.FLINT_AND_STEEL) {
                probablyCache.put(e.getRightClicked(), "#ignite-creeper-" + e.getPlayer().getName());
            }
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


//        if (beds.containsKey(e.getBlock().getLocation())) {
//            if (getConfig().getBoolean("bed.log")) {
//                String source = beds.get(e.getBlock().getLocation());
//                beds.remove(e.getBlock().getLocation());
//
//                for (Block block : e.blockList()) {
//                    api.logRemoval("#[Bed]" + source, block.getLocation(), block.getType(), block.getBlockData());
//                    ignitedBlocks.put(block.getLocation(), source);
//                }
//                probablyIgnitedThisTick.addAll(e.blockList());
//            }
//        } else if (respawnAnchors.containsKey(e.getBlock().getLocation())) {
//            if (getConfig().getBoolean("respawn-anchor.log")) {
//                String source = respawnAnchors.get(e.getBlock().getLocation());
//                respawnAnchors.remove(e.getBlock().getLocation());
//
//                for (Block block : e.blockList()) {
//                    api.logRemoval("#[RespawnAnchor]" + source, block.getLocation(), block.getType(), block.getBlockData());
//                    ignitedBlocks.put(block.getLocation(), source);
//                }
//                probablyIgnitedThisTick.addAll(e.blockList());
//            }
//        } else {
//            // No idea why this could happen, but why not to handle this 0.01%
//            if (e.getBlock().getBlockData() instanceof Bed) {
//                if (getConfig().getBoolean("bed.disable-when-target-not-found")) {
//                    e.setCancelled(true);
//                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
//                    for (Entity entity : entityCollections) {
//                        if (entity instanceof Player)
//                            entity.sendMessage(
//                                    ChatColor.translateAlternateColorCodes('&',
//                                            getConfig().getString("msgs.bed-wont-break-blocks")
//                                    )
//                            );
//                    }
//                }
//            } else if (e.getBlock().getType() == Material.RESPAWN_ANCHOR) {
//                if (getConfig().getBoolean("respawn-anchor.disable-when-target-not-found")) {
//                    e.setCancelled(true);
//                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
//                    for (Entity entity : entityCollections) {
//                        if (entity instanceof Player)
//                            entity.sendMessage(
//                                    ChatColor.translateAlternateColorCodes('&',
//                                            getConfig().getString("msgs.respawn-anchor-wont-break-blocks")
//                                    )
//                            );
//                    }
//                }
//            } else {
//                if (getConfig().getBoolean("other-explosive-blocks.disable-when-target-not-found")) {
//                    e.setCancelled(true);
//                    Collection<Entity> entityCollections = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 15, 15, 15);
//                    for (Entity entity : entityCollections) {
//                        if (entity instanceof Player)
//                            entity.sendMessage(
//                                    ChatColor.translateAlternateColorCodes('&',
//                                            getConfig().getString("msgs.other-explosive-block-wont-break-blocks")
//                                    )
//                            );
//                    }
//                }
//            }
//        }
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
            for (Map.Entry<Location, String> entry : ignitedBlocks.entrySet()) {
                //noinspection ConstantConditions
                if (entry.getKey().getWorld().equals(blockCorner.getWorld()) && entry.getKey().distance(blockCorner) < 0.5) {
                    probablyCache.put(tnt, entry.getValue());
                    break;
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
                for (Block block : blockList) {
                    api.logRemoval("#" + entityName + "-" + probablyCache.getIfPresent(entity), block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), probablyCache.getIfPresent(entity));
                }
                probablyIgnitedThisTick.addAll(blockList);
                pendingRemoval.add(entity);
            } else {
                //Notify players this tnt or end crystal won't break any blocks
                if (!section.getBoolean("disable-unknown", true))
                    return;
                e.setCancelled(true);
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
                    }
                    probablyIgnitedThisTick.addAll(blockList);
                } else {
                    //Notify players this creeper won't break any blocks
                    if (!section.getBoolean("disable-unknown"))
                        return;
                    e.setCancelled(true);
                    Util.broadcastNearPlayers(e.getLocation(), section.getString("alert"));
                }
            }
        }
        if (entity instanceof Fireball) {
            if (probablyCache.getIfPresent(entity) != null) {
                String reason = probablyCache.getIfPresent(entity);
                for (Block block : blockList) {
                    api.logRemoval("#fireball-" + reason, block.getLocation(), block.getType(), block.getBlockData());
                    ignitedBlocks.put(block.getLocation(), reason);
                }
                pendingRemoval.add(entity);
                blockList.forEach(b -> probablyCache.put(b.getLocation(),reason));
            } else {
                if (section.getBoolean("disable-unknown")) {
                    e.setCancelled(true);
                    Util.broadcastNearPlayers(entity.getLocation(),section.getString("alert"));
                } else {
                    for (Block block : blockList) {
                        api.logRemoval("#fireball-" + "MissingNo", block.getLocation(), block.getType(), block.getBlockData());
                    }
                    blockList.forEach(b -> probablyCache.put(b.getLocation(),"#fireball-" + "MissingNo"));
                }
            }
        }
        if (entity instanceof ExplosiveMinecart) {
            boolean isLogged = false;
            Location blockCorner = entity.getLocation().clone().subtract(0.5, 0, 0.5);
            for (Map.Entry<Location, String> entry : ignitedBlocks.entrySet()) {
                if (entry.getKey().getWorld().equals(blockCorner.getWorld()) && entry.getKey().distance(blockCorner) < 1) {
                    for (Block block : blockList) {
                        api.logRemoval("#tntminecart-" + entry.getValue(), block.getLocation(), block.getType(), block.getBlockData());
                        ignitedBlocks.put(block.getLocation(), entry.getValue());
                    }
                    blockList.forEach(b -> probablyCache.put(b.getLocation(),"#tntminecart-" + entry.getValue()));
                    isLogged = true;
                    break;
                }
            }
            if (!isLogged) {
                if (probablyCache.getIfPresent(entity) != null) {
                    String reason = "#tntminecart-" + probablyCache.getIfPresent(entity);
                    for (Block block : blockList) {
                        api.logRemoval(reason, block.getLocation(), block.getType(), block.getBlockData());
                        ignitedBlocks.put(block.getLocation(), reason);
                    }
                    probablyIgnitedThisTick.addAll(blockList);
                    pendingRemoval.add(entity);
                } else if (section.getBoolean("disable-unknown")) {
                    e.setCancelled(true);
                    Util.broadcastNearPlayers(entity.getLocation(),section.getString("alert"));
                }
            }
        }
        pendingRemoval.forEach(explosionSources::remove);
    }
}