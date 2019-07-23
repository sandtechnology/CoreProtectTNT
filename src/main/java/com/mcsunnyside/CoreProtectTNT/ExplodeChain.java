package com.mcsunnyside.CoreProtectTNT;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@Builder
@AllArgsConstructor
@Getter
@Setter
public class ExplodeChain {
    private String user;
    private Entity tntEntity;
}
