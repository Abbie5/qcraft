package com.acikek.qcraft.data;

import com.acikek.qcraft.QCraft;
import com.acikek.qcraft.block.QuantumOre;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class WorldgenProvider extends FabricDynamicRegistryProvider {
    public WorldgenProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup registries, Entries entries) {
        entries.add(RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, QCraft.id("ore_quantum")), QuantumOre.QUANTUM_ORE_CONFIGURED_FEATURE);
        entries.add(RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, QCraft.id("ore_quantum_lower")), QuantumOre.DEEPSLATE_QUANTUM_ORE_CONFIGURED_FEATURE);

        entries.add(RegistryKey.of(RegistryKeys.PLACED_FEATURE, QCraft.id("ore_quantum")), QuantumOre.QUANTUM_ORE_PLACED_FEATURE);
        entries.add(RegistryKey.of(RegistryKeys.PLACED_FEATURE, QCraft.id("ore_quantum_lower")), QuantumOre.DEEPSLATE_QUANTUM_ORE_PLACED_FEATURE);
    }

    @Override
    public String getName() {
        return "Worldgen";
    }
}
