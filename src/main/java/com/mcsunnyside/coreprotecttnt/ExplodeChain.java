package com.mcsunnyside.coreprotecttnt;

import org.bukkit.entity.Entity;

public class ExplodeChain {
    private String user;
    private Entity tntEntity;

    public ExplodeChain(String user, Entity tntEntity) {
        this.user = user;
        this.tntEntity = tntEntity;
    }

    public String getUser() {
        return user;
    }

    public Entity getTntEntity() {
        return tntEntity;
    }
}
