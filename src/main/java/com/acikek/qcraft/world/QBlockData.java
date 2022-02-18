package com.acikek.qcraft.world;

import com.acikek.qcraft.QCraft;
import com.acikek.qcraft.blocks.qblock.InertQBlock;
import com.acikek.qcraft.blocks.qblock.QBlock;
import com.acikek.qcraft.blocks.qblock.QBlockRecipe;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.*;
import java.util.stream.Collectors;

public class QBlockData extends PersistentState {

    public static Codec<List<QBlockLocation>> CODEC = Codec.list(QBlockLocation.CODEC);
    public static final String DATA = "qblocklocations";
    public static final String KEY = QCraft.ID + "_" + DATA;

    public List<QBlockLocation> locations = new ArrayList<>();
    public boolean settingBlock = false;

    public QBlockData() {
    }

    public static QBlockData get(World world) {
        QBlockData data = ((ServerWorld) world).getPersistentStateManager().getOrCreate(QBlockData::fromNbt, QBlockData::new, KEY);
        data.filterBlocks(world);
        return data;
    }

    public static QBlockData fromNbt(NbtCompound nbt) {
        QBlockData blockData = new QBlockData();
        List<QBlockLocation> locations = CODEC.parse(NbtOps.INSTANCE, nbt.getList(DATA, NbtElement.COMPOUND_TYPE))
                .result()
                .orElse(Collections.emptyList());
        if (!locations.isEmpty()) {
            blockData.locations.addAll(locations);
            QCraft.LOGGER.info("Loaded " + blockData.locations.size() + " qBlocks");
        }
        return blockData;
    }

    public void filterBlocks(World world) {
        int size = locations.size();
        locations.removeIf(location -> !location.checkBlockState(world.getBlockState(location.pos)));
        if (locations.size() < size) {
            QCraft.LOGGER.error("Removed " + (size - locations.size()) + " invalid qBlocks");
        }
    }

    public List<QBlockLocation> getLoadedLocations(ServerWorld world) {
        return locations.stream()
                .filter(location -> world.getChunkManager().isChunkLoaded(location.pos.getX() / 16, location.pos.getZ() / 16))
                .collect(Collectors.toList());
    }

    public List<QBlockLocation> getLocalLocations(List<QBlockLocation> loaded, PlayerEntity player) {
        return loaded.stream()
                .filter(location -> location.pos.isWithinDistance(player.getEyePos(), 160))
                .collect(Collectors.toList());
    }

    public QBlockLocation addBlock(QBlock.Type type, BlockPos blockPos, ItemStack stack) {
        String[] faces = QBlock.getFaces(stack);
        if (faces == null || getBlock(blockPos).isPresent()) {
            return null;
        }
        QBlockLocation result = new QBlockLocation(type, blockPos, List.of(faces), false);
        locations.add(result);
        markDirty();
        return result;
    }

    public Optional<QBlockLocation> getBlock(BlockPos blockPos) {
        return locations.stream()
                .filter(loc -> loc.pos.asLong() == blockPos.asLong())
                .findFirst();
    }

    public void removeBlock(BlockPos blockPos) {
        this.getBlock(blockPos).ifPresent(this::removeBlock);
    }

    public void removeBlock(QBlockLocation location) {
        if (locations.remove(location)) {
            markDirty();
        }
    }

    public boolean hasBlock(BlockPos blockPos) {
        return getBlock(blockPos).isPresent();
    }

    public void setBlockState(World world, BlockPos pos, BlockState state) {
        settingBlock = true;
        world.setBlockState(pos, state);
    }

    public void setFaceBlock(World world, QBlockLocation location, QBlock.Face face) {
        BlockState state = location.getFaceBlock(face).getDefaultState();
        setBlockState(world, location.pos, state);
    }

    public void observe(QBlockLocation location, World world, PlayerEntity player) {
        switch (location.type) {
            case OBSERVER_DEPENDENT -> setFaceBlock(world, location, location.getClosestFace(player.getEyePos()));
            case QUANTUM -> setFaceBlock(world, location, location.getClosestAxis(location.getBetween(player.getEyePos())).getRandomFace(world.random));
        };
        location.observed = true;
    }

    public void unobserve(QBlockLocation location, World world) {
        BlockState state = location.type.resolveInert().getDefaultState();
        setBlockState(world, location.pos, state);
        location.observed = false;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        CODEC.encodeStart(NbtOps.INSTANCE, locations)
                .result()
                .ifPresent(tag -> nbt.put(DATA, tag));
        return nbt;
    }

    public static class QBlockLocation {

        public static Codec<QBlockLocation> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        QBlock.Type.CODEC.fieldOf("type").forGetter(l -> l.type),
                        BlockPos.CODEC.fieldOf("pos").forGetter(l -> l.pos),
                        Codec.list(Codec.STRING).fieldOf("faces").forGetter(l -> l.faces),
                        Codec.BOOL.fieldOf("observed").forGetter(l -> l.observed)
                )
                        .apply(instance, QBlockLocation::new)
        );

        public QBlock.Type type;
        public BlockPos pos;
        public List<String> faces;
        public boolean observed;

        public QBlockLocation(QBlock.Type type, BlockPos pos, List<String> faces, boolean observed) {
            this.type = type;
            this.pos = pos;
            this.faces = faces;
            this.observed = observed;
        }

        public Block getFaceBlock(int index) {
            return Registry.BLOCK.get(Identifier.tryParse(faces.get(index)));
        }

        public Block getFaceBlock(QBlock.Face face) {
            return getFaceBlock(face.index);
        }

        public ItemStack getItemStack() {
            ItemStack stack = new ItemStack(this.type.resolveBlock());
            return QBlockRecipe.applyFaces(stack, faces);
        }

        public Vec3d getBetween(Vec3d eyePos) {
            return eyePos.subtract(Vec3d.ofCenter(this.pos));
        }

        public QBlock.Axis getClosestAxis(Vec3d dists) {
            double absX = Math.abs(dists.x);
            double absY = Math.abs(dists.y);
            double absZ = Math.abs(dists.z);
            if (absX > absY && absX > absZ) {
                return QBlock.Axis.X;
            }
            else if (absY > absX && absY > absZ) {
                return QBlock.Axis.Y;
            }
            else {
                return QBlock.Axis.Z;
            }
        }

        public QBlock.Face getClosestFace(Vec3d eyePos) {
            Vec3d dists = getBetween(eyePos);
            QBlock.Axis axis = getClosestAxis(dists);
            return switch (axis) {
                case X -> dists.x > 0 ? QBlock.Face.EAST : QBlock.Face.WEST;
                case Y -> dists.y > 0 ? QBlock.Face.UP : QBlock.Face.DOWN;
                case Z -> dists.z > 0 ? QBlock.Face.SOUTH : QBlock.Face.NORTH;
            };
        }

        public boolean checkBlockState(BlockState state) {
            Block block = state.getBlock();
            if (block instanceof InertQBlock) {
                return true;
            }
            String id = Registry.BLOCK.getId(block).toString();
            return this.faces.contains(id);
        }

        // These methods took 2 days to write.
        // Massive thanks to sssubtlety, nHail, and dirib!

        public double getPitchDifference(PlayerEntity player, Vec3d between) {
            double pitch = Math.atan(between.y / Math.sqrt(between.x * between.x + between.z * between.z));
            //System.out.println("BlockPitch: " + Math.toDegrees(pitch));
            return Math.abs(player.getPitch() - Math.toDegrees(pitch));
        }

        public double getYawDifference(PlayerEntity player, Vec3d between) {
            Vec3d rotated = between.rotateY((float) Math.toRadians(270));
            double yaw = Math.atan2(rotated.z, rotated.x);
            //System.out.println("BlockYaw: " + Math.toDegrees(yaw));
            return 180.0 - Math.abs(Math.abs(player.getYaw() - Math.toDegrees(yaw)) - 180.0);
        }
    }
}
