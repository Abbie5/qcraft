package com.acikek.qcraft.block.qblock;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;

public class InertQBlock extends Block {

    public static final Settings DEFAULT_SETTINGS = FabricBlockSettings.of().strength(5.0f, 5.0f);

    public InertQBlock() {
        super(DEFAULT_SETTINGS);
    }
}
