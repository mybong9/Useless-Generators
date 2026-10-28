package com.sorrowmist.uselessgenerators;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fluids.capability.wrappers.FluidBucketWrapper;

public final class FuelBucketItem extends BucketItem {
    private final FuelKind kind;
    public FuelBucketItem(Supplier<? extends Fluid> fluid, Properties properties, FuelKind kind) {
        super(fluid, properties); this.kind = kind;
    }
    /** Forge's BucketItem intentionally supplies this capability only for its exact base class. */
    @Override public ICapabilityProvider initCapabilities(ItemStack stack, CompoundTag nbt) {
        return new FluidBucketWrapper(stack);
    }
    @Override public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.useless_generators.fuel_value",
                java.text.NumberFormat.getIntegerInstance(java.util.Locale.ROOT).format(kind.perBucket)));
    }
}
