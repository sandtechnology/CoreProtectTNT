package com.mcsunnyside.coreprotecttnt;

import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@SuppressWarnings("ConstantConditions")
public class Main extends JavaPlugin implements Listener {
    private Set<ExplodeChain> set = new HashSet<>();
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
        new BukkitRunnable(){
            @Override
            public void run() {
                set.clear();
            }
        }.runTaskTimerAsynchronously(this, 0, 20*60*60);
    }

    @Override
    public void onDisable() {
        set.clear();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgnite(EntitySpawnEvent e) {
        Entity tnt = e.getEntity();
        if (e.getEntity() instanceof TNTPrimed) {
            TNTPrimed tntPrimed = (TNTPrimed) e.getEntity();
            Entity source = tntPrimed.getSource();
            if (source == null) {
                return;
            }else{
                //Bukkti given the args for tnt, direct track it.
                if (source instanceof Player) {
                    set.add(new ExplodeChain(source.getName(), tntPrimed));
                } else if (source instanceof TNTPrimed) {
                    for (ExplodeChain chain : set) {
                        if (chain.getTntEntity().getUniqueId() == tnt.getUniqueId()) {
                            set.add(new ExplodeChain(chain.getUser(), tnt));
                            return;
                        }
                    }
                } else {
                    set.add(new ExplodeChain(source.getName(), tntPrimed));
                }
            }

        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onIgnite(BlockIgniteEvent e) {
        Entity tnt = e.getIgnitingEntity();
        if (tnt == null)
            return;
        if(tnt instanceof TNTPrimed){
            ArrayList<ExplodeChain> pendingRemove = new ArrayList<>();
            for (ExplodeChain chain : set) {
                if (chain.getTntEntity().getUniqueId() == tnt.getUniqueId()) {
                    set.add(new ExplodeChain(chain.getUser(), tnt));
                    pendingRemove.add(chain);
                    return;
                }
            }
            set.removeAll(pendingRemove);
            pendingRemove.clear();
            return;
        }
        Player player = e.getPlayer();
        if (player != null) {
            set.add(new ExplodeChain(player.getName(), tnt));
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onExplode(EntityExplodeEvent e) {
        Entity tnt = e.getEntity();
        List<Block> blockList = e.blockList();
        if (blockList.isEmpty())
            return;
        List<ExplodeChain> pendingRemoval = new ArrayList<>();
        if(tnt instanceof TNTPrimed){
            if(!getConfig().getBoolean("tnt.log"))
                return;
            boolean isLogged = false;
            for (ExplodeChain chain : set) {
                if (chain.getTntEntity().getUniqueId() != tnt.getUniqueId())
                    continue;
                for (Block block : blockList) {
                    api.logRemoval("#[TNT]" + chain.getUser(), block.getLocation(), block.getType(), block.getBlockData());
                }
                pendingRemoval.add(chain);
                isLogged = true;
                break;
            }
            if(!isLogged){
                //Notify players this tnt won't break any blocks
                if(!getConfig().getBoolean("tnt.disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(),15,15,15);
                for (Entity entity : entityCollections){
                    if(entity instanceof Player)
                        entity.sendMessage(getConfig().getString("msgs.tnt-wont-break-blocks"));
                }
            }
        }
        if(tnt instanceof Creeper){
            if(!getConfig().getBoolean("creeper.log"))
                return;
            Creeper creeper = (Creeper) tnt;
            LivingEntity creeperTarget = creeper.getTarget();
            if (creeperTarget != null) {
                for (Block block : blockList) {
                    api.logRemoval("#[Creeper]" + creeperTarget.getName(), block.getLocation(), block.getType(), block.getBlockData());
                }
            }else{
                //Notify players this creeper won't break any blocks
                if(!getConfig().getBoolean("creeper.disable-when-target-not-found"))
                    return;
                e.setCancelled(true);
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(),15,15,15);
                for (Entity entity : entityCollections){
                    if(entity instanceof Player)
                        entity.sendMessage(getConfig().getString("msgs.creeper-wont-break-blocks"));
                }
            }
        }
        if(tnt instanceof Fireball){
            if(!getConfig().getBoolean("fireball.log"))
                return;
            ProjectileSource source = ((Fireball) tnt).getShooter();
            if(source == null){
                if(!getConfig().getBoolean("fireball.disable-when-target-not-found"))
                    return;
                Collection<Entity> entityCollections = e.getLocation().getWorld().getNearbyEntities(e.getLocation(),15,15,15);
                for (Entity entity : entityCollections){
                    if(entity instanceof Player)
                        entity.sendMessage(getConfig().getString("msgs.fireball-wont-break-blocks"));
                }
            }
            if(source instanceof Entity) {
                for (Block block : blockList) {
                    api.logRemoval("#[Fireball]" + source, block.getLocation(), block.getType(), block.getBlockData());
                }
            }else{
                for (Block block : blockList) {
                    api.logRemoval("#[Fireball]" + "MissingNo", block.getLocation(), block.getType(), block.getBlockData());
                }
            }
        }
        set.removeAll(pendingRemoval);
        pendingRemoval.clear();
    }
}
