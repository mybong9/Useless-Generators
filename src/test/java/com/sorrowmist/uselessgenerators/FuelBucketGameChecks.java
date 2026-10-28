package com.sorrowmist.uselessgenerators;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Requires an actual Forge GameTest runtime; a plain JVM cannot bootstrap Forge event transforms. */
@GameTestHolder(UselessGenerators.ID)
@PrefixGameTestTemplate(false)
public final class FuelBucketGameChecks {
    @GameTest(template = "empty")
    public static void allFuelBucketsExchangeWithoutLoss(GameTestHelper helper) {
        for (GeneratorFuels.Entry entry : GeneratorFuels.FUELS) {
            ItemStack bucket = new ItemStack(entry.bucket.get());
            helper.assertTrue(bucket.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent(), entry.kind.id + " missing bucket capability");
            FluidTank tank = new FluidTank(16000);
            var emptied = FluidUtil.tryEmptyContainer(bucket, tank, 1000, null, true);
            helper.assertTrue(emptied.isSuccess() && emptied.getResult().is(Items.BUCKET) && tank.getFluidAmount() == 1000,
                    entry.kind.id + " must insert exactly one bucket");
            var filled = FluidUtil.tryFillContainer(emptied.getResult(), tank, 1000, null, true);
            helper.assertTrue(filled.isSuccess() && filled.getResult().is(entry.bucket.get()) && tank.isEmpty(),
                    entry.kind.id + " must return the correct filled bucket");
            tank.setFluid(new FluidStack(entry.source.get(), 15500));
            var rejected = FluidUtil.tryEmptyContainer(bucket, tank, 1000, null, true);
            helper.assertTrue(!rejected.isSuccess() && tank.getFluidAmount() == 15500,
                    entry.kind.id + " must not partially empty or lose a bucket");
        }
        helper.succeed();
    }
}
