package com.mcsunnyside.coreprotecttnt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

@Builder
@AllArgsConstructor
@Getter
@Setter
@NotNull
public class ExplodeChain {
    private String user;
    private Entity tntEntity;
}
