package com.acikek.qcraft;

import com.acikek.qcraft.advancement.Criteria;
import com.acikek.qcraft.block.Blocks;
import com.acikek.qcraft.block.QuantumOre;
import com.acikek.qcraft.block.quantum_computer.QuantumComputerBlockEntity;
import com.acikek.qcraft.block.quantum_computer.QuantumComputerGuiDescription;
import com.acikek.qcraft.command.QCraftCommand;
import com.acikek.qcraft.item.Items;
import com.acikek.qcraft.recipe.CoolantCellRefillRecipe;
import com.acikek.qcraft.recipe.EntangledPairRecipe;
import com.acikek.qcraft.recipe.QBlockRecipe;
import com.acikek.qcraft.sound.Sounds;
import com.acikek.qcraft.world.QBlockTickListener;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class QCraft implements ModInitializer {

    public static final String ID = "qcraft";

    public static final RegistryKey<ItemGroup> ITEM_GROUP_KEY = RegistryKey.of(RegistryKeys.ITEM_GROUP, id("main"));
    private static final ItemGroup ITEM_GROUP = FabricItemGroup.builder().displayName(Text.translatable("itemGroup.qcraft.main"))
            .icon(() -> new ItemStack(Items.QUANTUM_DUST))
            .build();

    public static final Logger LOGGER = LogManager.getLogger();

    public static Identifier id(String name) {
        return new Identifier(ID, name);
    }

    public static String uid(String item) {
        return ID + "_" + item;
    }

    @Override
    public void onInitialize() {
        QCraft.LOGGER.info("Initializing qCraft");
        Sounds.registerAll();
        Blocks.registerAll();
        Items.registerAll();
        Criteria.registerAll();
        QBlockRecipe.register();
        EntangledPairRecipe.register();
        CoolantCellRefillRecipe.register();
        QuantumOre.createFeatures();
        QuantumOre.registerFeatures();
        QuantumComputerBlockEntity.register();
        QuantumComputerGuiDescription.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                new QCraftCommand().register(dispatcher));
        ServerTickEvents.START_WORLD_TICK.register(new QBlockTickListener());
        Registry.register(Registries.ITEM_GROUP, ITEM_GROUP_KEY, ITEM_GROUP);
    }
}
