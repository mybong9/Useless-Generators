package com.sorrowmist.uselessgenerators;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidUtil;

final class FluidInteractionFeedback {
    private FluidInteractionFeedback() {}
    static Component explain(ItemStack stack, GeneratorBlockEntity generator, Direction side) {
        ItemStack single = stack.copy(); single.setCount(1);
        var contained = FluidUtil.getFluidContained(single);
        if (side != null && !(contained.isPresent() ? generator.fluidMode(side).input : generator.fluidMode(side).output))
            return Component.translatable("gui.useless_generators.bucket_port_blocked");
        if (contained.isPresent()) {
            var fluid = contained.get(); FuelKind kind = GeneratorFuels.kind(fluid.getFluid());
            GeneratorTier tier = ((GeneratorBlock) generator.getBlockState().getBlock()).tier;
            if (tier == GeneratorTier.T1 && kind != FuelKind.USEFUL)
                return Component.translatable("tooltip.useless_generators.useful_only");
            if (kind == null || !kind.allowedIn(tier))
                return Component.translatable("gui.useless_generators.wrong_fuel", fluid.getDisplayName());
            if (!generator.fuelTank.isEmpty() && !generator.fuelTank.getFluid().isFluidEqual(fluid))
                return Component.translatable("gui.useless_generators.mixed_fuel");
            if (generator.fuelTank.getCapacity() - generator.fuelTank.getFluidAmount() < fluid.getAmount())
                return Component.translatable("gui.useless_generators.tank_full");
        } else if (generator.fuelTank.getFluidAmount() < 1000) {
            return Component.translatable("gui.useless_generators.not_enough_bucket");
        }
        return Component.translatable("gui.useless_generators.bucket_rejected");
    }
}
