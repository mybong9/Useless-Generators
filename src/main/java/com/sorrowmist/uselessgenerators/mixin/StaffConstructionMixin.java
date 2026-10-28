package com.sorrowmist.uselessgenerators.mixin;

import com.sorrowmist.uselessgenerators.CosmicConstruction;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Resolve the controller gesture before the staff's generic wall/undo operation. */
@Pseudo
@Mixin(targets = "com.sorrowmist.useless.UselessMod", remap = false)
public abstract class StaffConstructionMixin {
    @Inject(method = "onRightClickBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private void cosmicConstruction(PlayerInteractEvent.RightClickBlock event, CallbackInfo callback) {
        if (CosmicConstruction.interact(event)) callback.cancel();
    }
}
