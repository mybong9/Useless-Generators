package com.sorrowmist.uselessgenerators;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public final class ReactorBlock extends GeneratorBlock {
    public ReactorBlock() { super(GeneratorTier.T1); }
    @Override public long generationRate() { return ReactorLayout.RATE; }
    @Override public long energyCapacity() { return ReactorLayout.RATE; }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && stack.hasTag() && stack.getTag().contains("CosmicData")
                && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) generator.restorePortable(stack.getTag().getCompound("CosmicData"));
    }
    @Override public List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        ItemStack result = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof GeneratorBlockEntity generator)
            result.getOrCreateTag().put("CosmicData", generator.portableContents());
        return List.of(result);
    }
    @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) { return net.minecraft.world.level.material.PushReaction.BLOCK; }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_generators.reactor_rate"));
        tooltip.add(Component.translatable("tooltip.useless_generators.reactor_structure"));
        tooltip.add(Component.translatable("tooltip.useless_generators.reactor_fuel"));
        tooltip.add(Component.translatable("tooltip.useless_generators.reactor_alignment"));
        tooltip.add(Component.translatable("tooltip.useless_generators.reactor_output"));
        tooltip.add(Component.translatable("gui.useless_generators.cosmic.staff_hint"));
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(this)) ReactorStructure.changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!replacement.is(this)) ReactorStructure.changed(level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
}
