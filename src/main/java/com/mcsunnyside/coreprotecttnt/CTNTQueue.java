//package com.mcsunnyside.coreprotecttnt;
//
//import net.coreprotect.consumer.Queue;
//import org.bukkit.Location;
//import org.bukkit.Material;
//import org.bukkit.block.Block;
//import org.bukkit.block.BlockState;
//import org.bukkit.entity.EntityType;
//
//import java.lang.reflect.InvocationTargetException;
//import java.lang.reflect.Method;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//public class CTNTQueue {
//    private static Map<String,Method> methodMapping = new HashMap<>();
//    static{
//        Class<?> clazz = Queue.class;
//        for (Method declaredMethod : clazz.getDeclaredMethods()) {
//            declaredMethod.setAccessible(true);
//            methodMapping.put(declaredMethod.getName(),declaredMethod);
//        }
//    }
//
//    protected static synchronized int getChestId(String id) {
//        try {
//            return (int)methodMapping.get("getChestId").invoke(null,id);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//        return 0;
//    }
//
//    protected static synchronized int getItemId(String id) {
//        try {
//            return (int)methodMapping.get("getItemId").invoke(null,id);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//        return 0;
//    }
//    protected static synchronized void queueContainerTransaction(String user, Location location, Material type, Object inventory, int chestId) {
//        try {
//            methodMapping.get("queueContainerTransaction").invoke(null,user,location,type,inventory,chestId);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueItemTransaction(String user, Location location, int time, int itemId) {
//        try {
//            methodMapping.get("queueItemTransaction").invoke(null,user,location,time,itemId);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueEntityInsert(int id, String name) {
//        try {
//            methodMapping.get("queueEntityInsert").invoke(null,id,name);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//    protected static void queueNaturalBlockBreak(String user, BlockState block, Block relative, Material type, int data) {
//        try {
//            methodMapping.get("queueNaturalBlockBreak").invoke(null,user,block,relative,type,data);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//    protected static void queueEntityKill(String user, Location location, List<Object> data, EntityType type) {
//        try {
//            methodMapping.get("queueEntityKill").invoke(null,user,location,data,type);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueEntitySpawn(String user, BlockState block, EntityType type, int data) {
//        try {
//            methodMapping.get("queueEntitySpawn").invoke(null,user,block,type,data);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueHangingRemove(String user, BlockState block, int delay) {
//        try {
//            methodMapping.get("queueHangingRemove").invoke(null,user,block,delay);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueHangingSpawn(String user, BlockState block, Material type, int data, int delay) {
//        try {
//            methodMapping.get("queueHangingSpawn").invoke(null,user,block,delay);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queuePlayerInteraction(String user, BlockState block) {
//        try {
//            methodMapping.get("queuePlayerInteraction").invoke(null,user,block);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queuePlayerKill(String user, Location location, String player) {
//        try {
//            methodMapping.get("queuePlayerKill").invoke(null,user,location,player);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//
//    protected static void queueRollbackUpdate(String user, Location location, List<Object[]> list, int action) {
//        try {
//            methodMapping.get("queueRollbackUpdate").invoke(null,user,location,list,action);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueSignText(String user, Location location, int action, int color, boolean glowing, String line1, String line2, String line3, String line4, int offset) {
//        try {
//            methodMapping.get("queueSignText").invoke(null,user,location,action,color,glowing,line1,line2,line3,line4,offset);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueSignUpdate(String user, BlockState block, int action, int time) {
//        try {
//            methodMapping.get("queueSignUpdate").invoke(null,user,block,action,time);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueSkullUpdate(String user, BlockState block, int rowId) {
//        try {
//            methodMapping.get("queueSkullUpdate").invoke(null,block,rowId);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueStructureGrow(String user, BlockState block, List<BlockState> blockList, int replacedListSize) {
//        try {
//            methodMapping.get("queueStructureGrow").invoke(null,block,blockList,replacedListSize);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//
//    protected static void queueWorldInsert(int id, String world) {
//        try {
//            methodMapping.get("queueWorldInsert").invoke(null,id,world);
//        } catch (IllegalAccessException | InvocationTargetException e) {
//            e.printStackTrace();
//        }
//    }
//}
