package com.sorrowmist.uselessgenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public final class MeHatchBlock extends BaseEntityBlock {
    public final MeHatchKind kind;
    public MeHatchBlock(MeHatchKind kind) {
        super(Properties.of().mapColor(MapColor.METAL).strength(5, 12).requiresCorrectToolForDrops());
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(GeneratorBlock.FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(GeneratorBlock.FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return defaultBlockState().setValue(GeneratorBlock.FACING, context.getHorizontalDirection().getOpposite()); }
    @Override public BlockState rotate(BlockState state, Rotation rotation) { return state.setValue(GeneratorBlock.FACING, rotation.rotate(state.getValue(GeneratorBlock.FACING))); }
    @Override public BlockState mirror(BlockState state, Mirror mirror) { return state.rotate(mirror.getRotation(state.getValue(GeneratorBlock.FACING))); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MeHatchBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, UselessGenerators.ME_HATCH.get(), MeHatchBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeHatchBlockEntity hatch)
            net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, hatch, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof MeHatchBlockEntity hatch) {
            // Survival drops keep all buffered resources and marks in a single item.
            if (stack.hasTag() && stack.getTag().contains("HatchData")) hatch.restoreContents(stack.getTag().getCompound("HatchData"));
            if (placer instanceof Player player) hatch.setOwner(player);
            ReactorStructure.changed(level, pos);
        }
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (!old.is(state.getBlock())) ReactorStructure.changed(level, pos);
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moving) {
        if (!state.is(replacement.getBlock())) ReactorStructure.changed(level, pos);
        super.onRemove(state, level, pos, replacement, moving);
    }
    @Override public java.util.List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder builder) {
        ItemStack drop = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY) instanceof MeHatchBlockEntity hatch)
            drop.getOrCreateTag().put("HatchData", hatch.contentsTag());
        return java.util.List.of(drop);
    }
    @Override public net.minecraft.world.level.material.PushReaction getPistonPushReaction(BlockState state) { return net.minecraft.world.level.material.PushReaction.BLOCK; }
    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.BlockGetter level, java.util.List<net.minecraft.network.chat.Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        tooltip.add(net.minecraft.network.chat.Component.translatable("gui.useless_generators.me." + (kind.input() ? "tooltip" : kind == MeHatchKind.ENERGY ? "energy_hint" : kind == MeHatchKind.CONNECTOR ? "connector_hint" : "output_hint")));
        tooltip.add(net.minecraft.network.chat.Component.translatable("gui.useless_generators.me.stored"));
        tooltip.add(net.minecraft.network.chat.Component.translatable("gui.useless_generators.cosmic.hatch_overlay_hint"));
    }
}
