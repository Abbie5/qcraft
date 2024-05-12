package com.acikek.qcraft.advancement;

import com.acikek.qcraft.QCraft;
import com.acikek.qcraft.block.qblock.QBlock;
import com.acikek.qcraft.predicate.EnumPredicate;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.advancement.criterion.AbstractCriterionConditions;
import net.minecraft.predicate.BlockPredicate;
import net.minecraft.predicate.entity.AdvancementEntityPredicateDeserializer;
import net.minecraft.predicate.entity.AdvancementEntityPredicateSerializer;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class QuantumObservationCriterion extends AbstractCriterion<QuantumObservationCriterion.Conditions> {

    public static Identifier ID = QCraft.id("quantum_observation");

    @Override
    protected Conditions conditionsFromJson(JsonObject obj, LootContextPredicate playerPredicate, AdvancementEntityPredicateDeserializer predicateDeserializer) {
        BlockPredicate face = BlockPredicate.fromJson(obj.get("face"));
        EnumPredicate<QBlock.Observation> observation = EnumPredicate.fromJson(obj.get("observation"), QBlock.Observation::valueOf);
        EnumPredicate<QBlock.Type> type = EnumPredicate.fromJson(obj.get("type"), QBlock.Type::valueOf);
        JsonPrimitive entangled = obj.getAsJsonPrimitive("entangled");
        return new Conditions(playerPredicate, face, observation, type, entangled != null && entangled.getAsBoolean());
    }

    @Override
    public Identifier getId() {
        return ID;
    }

    public void trigger(ServerPlayerEntity player, BlockPos pos, QBlock.Observation observationType, QBlock.Type qBlockType, boolean entangled) {
        trigger(player, (Conditions conditions) -> conditions.matches(player.getServerWorld(), pos, observationType, qBlockType, entangled));
    }

    public static class Conditions extends AbstractCriterionConditions {

        public BlockPredicate face;
        public EnumPredicate<QBlock.Observation> observation;
        public EnumPredicate<QBlock.Type> type;
        public boolean entangled;

        public Conditions(LootContextPredicate playerPredicate, BlockPredicate face, EnumPredicate<QBlock.Observation> observation, EnumPredicate<QBlock.Type> type, boolean entangled) {
            super(ID, playerPredicate);
            this.face = face;
            this.observation = observation;
            this.type = type;
            this.entangled = entangled;
        }

        public boolean matches(ServerWorld world, BlockPos pos, QBlock.Observation observation, QBlock.Type type, boolean entangled) {
            return face.test(world, pos)
                    && this.observation.test(observation)
                    && this.type.test(type)
                    && this.entangled == entangled;
        }

        @Override
        public JsonObject toJson(AdvancementEntityPredicateSerializer predicateSerializer) {
            JsonObject obj = super.toJson(predicateSerializer);
            obj.add("face", face.toJson());
            obj.add("observation", observation.toJson());
            obj.add("type", type.toJson());
            obj.add("entangled", new JsonPrimitive(entangled));
            return obj;
        }
    }
}
