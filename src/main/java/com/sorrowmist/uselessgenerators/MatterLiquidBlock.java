package com.sorrowmist.uselessgenerators;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.joml.Vector3f;

/** Sparse surface-only ambience; no entities, packets or server tick work. */
final class MatterLiquidBlock extends LiquidBlock {
    private final DustParticleOptions mote;
    MatterLiquidBlock(Supplier<? extends FlowingFluid> fluid, Properties properties, int color) {
        super(fluid, properties);
        mote = new DustParticleOptions(new Vector3f((color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f, (color & 255) / 255f), .65f);
    }
    @Override public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(12) != 0 || !level.isEmptyBlock(pos.above())) return;
        double x = pos.getX() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        double y = pos.getY() + state.getFluidState().getHeight(level, pos) + .04;
        level.addParticle(mote, x, y, z, 0, .015, 0);
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.END_ROD, x, y + .025, z,
                (random.nextDouble() - .5) * .012, .018, (random.nextDouble() - .5) * .012);
    }
}
