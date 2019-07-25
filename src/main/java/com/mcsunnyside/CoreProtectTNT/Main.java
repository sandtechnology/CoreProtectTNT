package com.mcsunnyside.CoreProtectTNT;

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
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("Duplicates")
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
            if (source == null)
                return;
            if (source instanceof Player) {
                set.add(new ExplodeChain(source.getName(), tntPrimed));
                return;
            } else if (source instanceof TNTPrimed) {
                for (ExplodeChain chain : set) {
                    if (chain.getTntEntity().getUniqueId() == tnt.getUniqueId()) {
                        set.add(new ExplodeChain(chain.getUser(), tnt));
                        return;
                    }
                }
                return;
            } else {
                set.add(new ExplodeChain(source.getName(), tntPrimed));
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
            return;
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
            if(!getConfig().getBoolean("tnt"))
                return;
            for (ExplodeChain chain : set) {
                if (chain.getTntEntity().getUniqueId() != tnt.getUniqueId())
                    continue;
                for (Block block : blockList) {
                    api.logRemoval("#[TNT]" + chain.getUser(), block.getLocation(), block.getType(), block.getBlockData());
                }
                pendingRemoval.add(chain);
                break;
            }
        }
        if(tnt instanceof Creeper){
            if(!getConfig().getBoolean("creeper"))
                return;
            Creeper creeper = (Creeper) tnt;
            LivingEntity creeperTarget = creeper.getTarget();
            if (creeperTarget != null)
                for (Block block : blockList) {
                    api.logRemoval("#[Creeper]" + creeperTarget.getName(), block.getLocation(), block.getType(), block.getBlockData());
                }
        }
        set.removeAll(pendingRemoval);
        pendingRemoval.clear();
    }
}
