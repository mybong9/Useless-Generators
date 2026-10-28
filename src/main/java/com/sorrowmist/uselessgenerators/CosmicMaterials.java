package com.sorrowmist.uselessgenerators;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/** Counts stay separate from ItemStack.Count, including when copied or displayed. */
public final class CosmicMaterials {
    public record Material(ReactorLayout.Part part, int count) {
        public ItemStack stack() { return new ItemStack(ReactorStructure.block(part)); }
    }
    private static final List<Material> MATERIALS = create();
    private static List<Material> create() {
        var counts = new EnumMap<ReactorLayout.Part, Integer>(ReactorLayout.Part.class);
        for (var cell : ReactorLayout.CELLS) if (cell.part() != ReactorLayout.Part.AIR) counts.merge(cell.part(), 1, Integer::sum);
        return counts.entrySet().stream().map(e -> new Material(e.getKey(), e.getValue())).toList();
    }
    public static List<Material> all() { return MATERIALS; }
    public static BlockState state(ReactorLayout.Cell cell, Direction facing) {
        var state = ReactorStructure.block(cell.part()).defaultBlockState();
        if (state.hasProperty(GeneratorBlock.FACING)) state = state.setValue(GeneratorBlock.FACING, facing);
        return state;
    }
    public static BlockPos local(ReactorLayout.Cell cell) { return new BlockPos(cell.x(), cell.y(), cell.z()); }
    public static String textList() {
        StringBuilder text = new StringBuilder(Component.translatable("block.useless_generators.cosmic_heart").getString())
                .append(" — 80 × 80 × 32\n");
        for (var entry : MATERIALS) text.append(entry.stack().getHoverName().getString()).append(" × ").append(entry.count())
                .append(" [").append(BuiltInRegistries.ITEM.getKey(entry.stack().getItem())).append("]\n");
        return text.append(Component.translatable("gui.useless_generators.cosmic.material_note").getString()).toString();
    }
    private CosmicMaterials() {}
}
