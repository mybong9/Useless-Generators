package com.sorrowmist.uselessgenerators;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(UselessGenerators.ID)
public final class UselessGenerators {
    public static final String ID = "useless_generators";
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
    private static final DeferredRegister<net.minecraft.world.inventory.MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ID);
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<GeneratorMenu>> MENU = MENUS.register("generator",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(GeneratorMenu::new));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<GeneratorMenu>> COSMIC_MENU = MENUS.register("cosmic_heart",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create((id, inventory, buffer) -> new GeneratorMenu(id, inventory, buffer.readBlockPos(), true)));
    public static final RegistryObject<net.minecraft.world.inventory.MenuType<MeHatchMenu>> HATCH_MENU = MENUS.register("me_hatch",
            () -> net.minecraftforge.common.extensions.IForgeMenuType.create(MeHatchMenu::new));
    public static final java.util.EnumMap<MeHatchKind, RegistryObject<MeHatchBlock>> HATCHES = new java.util.EnumMap<>(MeHatchKind.class);
    public static final RegistryObject<BlockEntityType<MeHatchBlockEntity>> ME_HATCH;
    public static final List<RegistryObject<GeneratorBlock>> GENERATORS = new ArrayList<>();
    public static final RegistryObject<CosmicConstructionPatternItem> COSMIC_CONSTRUCTION_PATTERN =
            ITEMS.register("cosmic_construction_pattern",CosmicConstructionPatternItem::new);
    public static final RegistryObject<Item> COSMIC_CONSTRUCTION_RECEIPT =
            ITEMS.register("cosmic_construction_receipt",()->new Item(new Item.Properties()));
    public static final RegistryObject<ReactorBlock> COSMIC_HEART = BLOCKS.register("cosmic_heart", ReactorBlock::new);
    public static final RegistryObject<Block> REACTOR_CASING = reactorPart("cosmic_casing", 0);
    public static final RegistryObject<Block> REACTOR_COIL = reactorPart("cosmic_coil", 0);
    public static final RegistryObject<Block> REACTOR_CORE = reactorPart("cosmic_core", 0);
    public static final RegistryObject<Block> REACTOR_HULL = reactorPart("cosmic_hull", 0);
    public static final RegistryObject<BlockEntityType<GeneratorBlockEntity>> GENERATOR;
    static {
        for (MeHatchKind kind : MeHatchKind.values()) {
            var block = BLOCKS.register(kind.id, () -> new MeHatchBlock(kind)); HATCHES.put(kind, block);
            ITEMS.register(kind.id, () -> new BlockItem(block.get(), new Item.Properties().stacksTo(1)));
        }
        ME_HATCH = ENTITIES.register("me_hatch", () -> BlockEntityType.Builder.of(MeHatchBlockEntity::new,
                HATCHES.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
        ITEMS.register("cosmic_heart", () -> new BlockItem(COSMIC_HEART.get(), new Item.Properties()));
        for (GeneratorTier tier : GeneratorTier.values()) {
            var block = BLOCKS.register(tier.id, () -> new GeneratorBlock(tier));
            GENERATORS.add(block);
            ITEMS.register(tier.id, () -> new BlockItem(block.get(), new Item.Properties()));
        }
        GENERATOR = ENTITIES.register("generator", () -> BlockEntityType.Builder.of(
                GeneratorBlockEntity::new, java.util.stream.Stream.concat(GENERATORS.stream().map(RegistryObject::get),
                        java.util.stream.Stream.of(COSMIC_HEART.get())).toArray(Block[]::new)).build(null));
        TABS.register("generators", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.useless_generators"))
                .icon(() -> GENERATORS.get(0).get().asItem().getDefaultInstance())
                .displayItems((parameters, output) -> {
                    GENERATORS.forEach(block -> output.accept(block.get()));
                    output.accept(COSMIC_CONSTRUCTION_PATTERN.get()); output.accept(COSMIC_CONSTRUCTION_RECEIPT.get());
                    output.accept(COSMIC_HEART.get()); output.accept(REACTOR_CASING.get());
                    output.accept(REACTOR_COIL.get()); output.accept(REACTOR_CORE.get());
                    output.accept(REACTOR_HULL.get());
                    HATCHES.values().forEach(block -> output.accept(block.get()));
                    GeneratorFuels.MATERIAL_BLOCKS.forEach(block -> output.accept(block.get()));
                    GeneratorFuels.FUELS.forEach(fuel -> output.accept(fuel.bucket.get()));
                })
                .build());
    }
    private static RegistryObject<Block> reactorPart(String id, int light) {
        RegistryObject<Block> block = BLOCKS.register(id, () -> new ReactorPartBlock(light));
        ITEMS.register(id, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }
    public UselessGenerators() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); TABS.register(bus);
        GeneratorFuels.register(bus);
        MENUS.register(bus);
        MeHatchNetwork.register();
        bus.addListener(com.sorrowmist.uselesscore.api.capability.CoreCapabilities::registerNetworkCapabilities);
        bus.addListener((net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) ->
                event.enqueueWork(FluxNetworksCompat::register));
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ReactorCommands::register);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ReactorStructure::chunkUnloaded);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(ReactorStructure::blockChanged);
    }
}
