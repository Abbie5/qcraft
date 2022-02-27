package com.acikek.qcraft.world;

import com.acikek.qcraft.QCraft;
import com.acikek.qcraft.block.qblock.QBlock;
import com.acikek.qcraft.block.qblock.QBlockItem;
import com.acikek.qcraft.world.frequency.FrequencyMap;
import com.mojang.serialization.Codec;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class QBlockData extends PersistentState {

    public static final Codec<List<QBlockLocation>> CODEC = Codec.list(QBlockLocation.CODEC);
    public static final String DATA = "qblocklocations";
    public static final String KEY = QCraft.ID + "_" + DATA;

    public final List<QBlockLocation> locations = new ArrayList<>();
    public boolean settingBlock = false;
    public QBlockLocation removed = null;

    public final FrequencyMap<QBlockLocation, QBlockLocation.Pair> qBlockFrequencies = new FrequencyMap<>();

    public QBlockData() {
    }

    /**
     * Gets or creates {@link QBlockData} state from the specified world.
     *
     * @param world The {@link ServerWorld} to get the state from.
     * @return The {@link QBlockData} instance.
     */
    public static QBlockData get(World world, boolean filter) {
        QBlockData data = ((ServerWorld) world).getPersistentStateManager().getOrCreate(QBlockData::fromNbt, QBlockData::new, KEY);
        if (filter) {
            data.filterLocations(world);
            data.qBlockFrequencies.filter(data.locations);
        }
        return data;
    }

    public static QBlockData fromNbt(NbtCompound nbt) {
        QBlockData data = new QBlockData();
        List<QBlockLocation> locations = CODEC.parse(NbtOps.INSTANCE, nbt.getList(DATA, NbtElement.COMPOUND_TYPE))
                .result()
                .orElse(Collections.emptyList());
        if (!locations.isEmpty()) {
            data.locations.addAll(locations);
            QCraft.LOGGER.info("Loaded " + data.locations.size() + " qBlocks");
            data.qBlockFrequencies.fill(locations, QBlockLocation.Pair::new);
            QCraft.LOGGER.info("Loaded " + data.qBlockFrequencies.frequencies.size() + " frequencies");
        }
        return data;
    }

    /**
     * Filters the {@link QBlockLocation}s based on whether their current block state is possible.
     */
    public void filterLocations(World world) {
        int size = locations.size();
        locations.removeIf(location -> location.isStateImpossible(world.getBlockState(location.pos)));
        if (locations.size() < size) {
            QCraft.LOGGER.error("Removed " + (size - locations.size()) + " invalid qBlocks");
        }
    }

    /**
     * @return The {@link QBlockLocation}s that are in a loaded chunk.
     */
    public List<QBlockLocation> getLoadedLocations(ServerWorld world) {
        return locations.stream()
                .filter(location -> world.getChunkManager().isChunkLoaded(location.pos.getX() / 16, location.pos.getZ() / 16))
                .collect(Collectors.toList());
    }

    /**
     * @param loaded The locations that are in loaded chunks.
     * @return The {@link QBlockLocation}s that are within a close distance of the player.
     * @see QBlockData#getLoadedLocations(ServerWorld)
     */
    public List<QBlockLocation> getLocalLocations(List<QBlockLocation> loaded, PlayerEntity player) {
        return loaded.stream()
                .filter(location -> location.pos.isWithinDistance(player.getEyePos(), 160))
                .collect(Collectors.toList());
    }

    /**
     * Constructs and adds a {@link QBlockLocation} to this state's locations.
     *
     * @return The added location.
     */
    public QBlockLocation addBlock(QBlock.Type type, BlockPos blockPos, ItemStack stack) {
        if (getBlock(blockPos).isPresent()) {
            return null;
        }
        String[] faces = QBlockItem.getFaces(stack);
        if (faces == null) {
            return null;
        }
        NbtCompound stackNbt = stack.getOrCreateNbt();
        Optional<UUID> frequency = stackNbt.containsUuid("frequency")
                ? Optional.of(stackNbt.getUuid("frequency"))
                : Optional.empty();
        QBlockLocation result = new QBlockLocation(type, blockPos, List.of(faces), false, frequency);
        locations.add(result);
        frequency.ifPresent(f -> qBlockFrequencies.add(f, result, QBlockLocation.Pair::new));
        markDirty();
        return result;
    }

    /**
     * @return The location at the specified block position, if present.
     */
    public Optional<QBlockLocation> getBlock(BlockPos blockPos) {
        return locations.stream()
                .filter(loc -> loc.pos.asLong() == blockPos.asLong())
                .findFirst();
    }

    /**
     * Removes the block at the specified block position, if present.
     */
    public void removeBlock(BlockPos blockPos) {
        getBlock(blockPos).ifPresent(this::removeBlock);
    }

    /**
     * Removes the specified block location.
     *
     * @see QBlockData#removeBlock(BlockPos)
     */
    public void removeBlock(QBlockLocation location) {
        if (locations.remove(location)) {
            removed = location;
            qBlockFrequencies.remove(location);
            markDirty();
        }
    }

    public boolean hasBlock(BlockPos blockPos) {
        return getBlock(blockPos).isPresent();
    }

    /**
     * A wrapper for {@link World#setBlockState(BlockPos, BlockState)}.<br>
     * This sets {@link QBlockData#settingBlock} to true so that {@link com.acikek.qcraft.mixin.WorldMixin} functions properly.
     */
    public void setBlockState(World world, BlockPos pos, BlockState state) {
        settingBlock = true;
        world.setBlockState(pos, state);
    }

    public void setFaceBlock(World world, QBlockLocation location, QBlock.Face face) {
        BlockState state = location.getFaceBlock(face).getDefaultState();
        setBlockState(world, location.pos, state);
    }

    public void pseudoObserve(QBlockLocation location, World world, PlayerEntity player) {
        setFaceBlock(world, location, location.pickFace(player, world));
    }

    public void observe(QBlockLocation location, World world, PlayerEntity player) {
        QBlock.Face face = location.pickFace(player, world);
        observe(location, world, face);
        qBlockFrequencies.ifPresent(location, pair -> {
            QBlockLocation other = pair.getOther(location);
            if (other != null) {
                setFaceBlock(world, other, face);
            }
        });
    }

    public void observe(QBlockLocation location, World world, QBlock.Axis axis) {
        observe(location, world, axis.getRandomFace(world.random));
    }

    public void observe(QBlockLocation location, World world, QBlock.Face face) {
        setFaceBlock(world, location, face);
        location.observed = true;
    }

    public void unobserve(QBlockLocation location, World world, boolean checkFrequency) {
        BlockState state = location.type.resolveInert().getDefaultState();
        setBlockState(world, location.pos, state);
        location.observed = false;
        if (checkFrequency) {
            qBlockFrequencies.ifPresent(location, pair -> {
                QBlockLocation other = pair.getOther(location);
                if (other != null && !other.observed) {
                    unobserve(other, world, false);
                }
            });
        }
    }

    public boolean getOtherNotObserved(QBlockLocation location) {
        AtomicBoolean otherObserved = new AtomicBoolean(false);
        qBlockFrequencies.ifPresent(location, pair -> otherObserved.set(pair.getOtherObserved(location)));
        return !otherObserved.get();
    }

    public boolean canBeUnobserved(QBlockLocation location, Vec3d center) {
        return getOtherNotObserved(location) && !location.pos.isWithinDistance(center, 2.0);
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        CODEC.encodeStart(NbtOps.INSTANCE, locations)
                .result()
                .ifPresent(tag -> nbt.put(DATA, tag));
        return nbt;
    }
}
