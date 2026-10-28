package com.sorrowmist.uselessgenerators;

import com.sorrowmist.uselesscore.api.capability.CoreCapabilities;
import com.sorrowmist.uselesscore.api.logistics.LongEnergyHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;

/** Optional native long FE bridge. Foreign API types stay inside the guarded nested class. */
final class FluxNetworksCompat {
    private FluxNetworksCompat() { }
    static void register() {
        if (ModList.get().isLoaded("fluxnetworks")) Loaded.register();
    }
    static boolean push(BlockEntity target, Direction side, GeneratorEnergy source) {
        return ModList.get().isLoaded("fluxnetworks") && Loaded.push(target, side, source);
    }
    private static final class Loaded {
        static void register() {
            CoreCapabilities.registerBlockProvider(GeneratorBlockEntity.class,
                    sonar.fluxnetworks.api.FluxCapabilities.FN_ENERGY_STORAGE,
                    (generator, side) -> generator.reactor != null || side == null ? null
                            : new Storage(generator.longEnergyPort(side)));
            CoreCapabilities.registerBlockProvider(MeHatchBlockEntity.class,
                    sonar.fluxnetworks.api.FluxCapabilities.FN_ENERGY_STORAGE,
                    (hatch, side) -> hatch.kind.energy() ? new Storage(hatch.longEnergyHandler()) : null);
        }
        static boolean push(BlockEntity target, Direction side, GeneratorEnergy source) {
            var storage = target.getCapability(sonar.fluxnetworks.api.FluxCapabilities.FN_ENERGY_STORAGE, side).resolve().orElse(null);
            if (storage == null) return false;
            if (storage.canReceive()) FluxEnergyTransfer.transfer(source, amount -> storage.receiveEnergyL(amount, false));
            // Falling back to FE here would bypass the same port's native limits.
            return true;
        }
    }
    private record Storage(LongEnergyHandler energy) implements sonar.fluxnetworks.api.energy.IFNEnergyStorage {
        @Override public long receiveEnergyL(long amount, boolean simulate) { return energy.receive(amount, simulate); }
        @Override public long extractEnergyL(long amount, boolean simulate) { return energy.extract(amount, simulate); }
        @Override public long getEnergyStoredL() { return energy.stored(); }
        @Override public long getMaxEnergyStoredL() { return energy.capacity(); }
        @Override public boolean canExtract() { return energy.canExtract(); }
        @Override public boolean canReceive() { return energy.canReceive(); }
    }
}
