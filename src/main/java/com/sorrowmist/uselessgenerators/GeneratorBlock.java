package com.sorrowmist.uselessgenerators;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

public class GeneratorBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public final GeneratorTier tier;
    public GeneratorBlock(GeneratorTier tier) {
        super(Properties.of().mapColor(MapColor.METAL).strength(4.0F, 6.0F).requiresCorrectToolForDrops());
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }
    public long generationRate() { return tier.perTick(); }
    public long energyCapacity() { return tier.capacity(); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }
    @Override public BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) generator.setOwner(player);
    }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new GeneratorBlockEntity(pos, state); }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, UselessGenerators.GENERATOR.get(), GeneratorBlockEntity::tick);
    }
    @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        boolean fluidContainer = player.getItemInHand(hand).getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
        if (fluidContainer && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator && generator.reactor == null) {
            if (!level.isClientSide && !net.minecraftforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, generator.fluidPort(hit.getDirection()))) {
                player.displayClientMessage(FluidInteractionFeedback.explain(player.getItemInHand(hand), generator, hit.getDirection()), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator) {
            net.minecraftforge.network.NetworkHooks.openScreen((net.minecraft.server.level.ServerPlayer) player, generator, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
    @Override public void appendHoverText(ItemStack stack, BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.useless_generators.rate", Long.toString(tier.perSecond), Long.toString(tier.perTick())));
        tooltip.add(Component.translatable("tooltip.useless_generators.passive"));
        tooltip.add(Component.translatable("tooltip.useless_generators.ae"));
        tooltip.add(Component.translatable(tier == GeneratorTier.T1 ? "tooltip.useless_generators.useful_only"
                : tier.ordinal() <= 2 ? "tooltip.useless_generators.accepts_lava" : "tooltip.useless_generators.matter_fuels"));
    }
}
