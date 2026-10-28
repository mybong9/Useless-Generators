package com.sorrowmist.uselessgenerators;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class GeneratorFuels {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(ForgeRegistries.Keys.FLUID_TYPES, UselessGenerators.ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(ForgeRegistries.FLUIDS, UselessGenerators.ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, UselessGenerators.ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, UselessGenerators.ID);
    public static final List<Entry> FUELS = new ArrayList<>();
    public static final List<RegistryObject<Block>> MATERIAL_BLOCKS = new ArrayList<>();
    // Particle colors only: the liquid itself retains the original tier texture.
    private static final int[] MATTER_COLORS = {0x32e991, 0x9954ef, 0xf43d9e, 0x9ce8ff,
            0xffb4fc, 0xd197ff, 0x8947be, 0x398aff, 0xffce4b, 0x9163cf};
    static {
        for (FuelKind kind : FuelKind.values()) {
            if (kind == FuelKind.LAVA) continue;
            FUELS.add(new Entry(kind));
        }
        for (int i = 1; i <= 9; i++) {
            String id = "useless_block_" + i;
            var block = BLOCKS.register(id, () -> new Block(Block.Properties.copy(Blocks.IRON_BLOCK)));
            MATERIAL_BLOCKS.add(block);
            ITEMS.register(id, () -> new net.minecraft.world.item.BlockItem(block.get(), new Item.Properties()));
        }
    }
    public static final class Entry {
        public final FuelKind kind;
        public final RegistryObject<FluidType> type;
        public final RegistryObject<ForgeFlowingFluid.Source> source;
        public final RegistryObject<ForgeFlowingFluid.Flowing> flowing;
        public final RegistryObject<LiquidBlock> block;
        public final RegistryObject<Item> bucket;
        Entry(FuelKind kind) {
            this.kind = kind;
            int color = MATTER_COLORS[kind.ordinal()];
            type = TYPES.register(kind.id, () -> new FluidType(FluidType.Properties.create().density(1500).viscosity(2500).lightLevel(6)) {
                @Override public void initializeClient(Consumer<IClientFluidTypeExtensions> consumer) {
                    consumer.accept(new IClientFluidTypeExtensions() {
                        @Override public ResourceLocation getStillTexture() { return new ResourceLocation(UselessGenerators.ID, "block/" + kind.id + "_still"); }
                        @Override public ResourceLocation getFlowingTexture() { return new ResourceLocation(UselessGenerators.ID, "block/" + kind.id + "_flow"); }
                    });
                }
            });
            source = FLUIDS.register(kind.id, () -> new ForgeFlowingFluid.Source(properties()));
            flowing = FLUIDS.register("flowing_" + kind.id, () -> new ForgeFlowingFluid.Flowing(properties()));
            block = BLOCKS.register(kind.id, () -> new MatterLiquidBlock(source,
                    Block.Properties.copy(Blocks.WATER).lightLevel(state -> 6).noLootTable(), color));
            bucket = ITEMS.register(kind.id + "_bucket", () -> new FuelBucketItem(source,
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1), kind));
        }
        private ForgeFlowingFluid.Properties properties() {
            return new ForgeFlowingFluid.Properties(type, source, flowing).block(block).bucket(bucket)
                    .tickRate(10).slopeFindDistance(2).levelDecreasePerBlock(2);
        }
    }
    public static FuelKind kind(Fluid fluid) {
        if (fluid == Fluids.LAVA || fluid == Fluids.FLOWING_LAVA) return FuelKind.LAVA;
        for (Entry entry : FUELS) if (fluid == entry.source.get() || fluid == entry.flowing.get()) return entry.kind;
        return null;
    }
    public static void register(IEventBus bus) { TYPES.register(bus); FLUIDS.register(bus); BLOCKS.register(bus); ITEMS.register(bus); }
    private GeneratorFuels() {}
}
