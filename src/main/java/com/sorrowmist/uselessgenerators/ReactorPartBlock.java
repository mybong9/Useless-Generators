package com.sorrowmist.uselessgenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class ReactorPartBlock extends Block {
    public ReactorPartBlock(int light) {
        super(Properties.of().mapColor(MapColor.QUARTZ).strength(5, 12).requiresCorrectToolForDrops()
                .pushReaction(PushReaction.BLOCK).lightLevel(state -> light));
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
