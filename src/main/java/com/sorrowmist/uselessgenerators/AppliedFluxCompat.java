package com.sorrowmist.uselessgenerators;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import net.minecraftforge.fml.ModList;

/** Optional AppFlux types are resolved only after its presence has been checked. */
final class AppliedFluxCompat {
    private AppliedFluxCompat() {}

    static long insertFe(IGrid grid, long amount, IActionSource source) {
        if (amount <= 0 || !ModList.get().isLoaded("appflux")) return 0;
        return Loaded.insertFe(grid, amount, source);
    }

    private static final class Loaded {
        static long insertFe(IGrid grid, long amount, IActionSource source) {
            return grid.getStorageService().getInventory().insert(
                    com.glodblock.github.appflux.common.me.key.FluxKey.of(
                            com.glodblock.github.appflux.common.me.key.type.EnergyType.FE),
                    amount, Actionable.MODULATE, source);
        }
    }
}
