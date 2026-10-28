package com.sorrowmist.uselessgenerators;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;

/** An explicit administrator tool for preview worlds; preflights without replacing blocks. */
final class ReactorCommands {
    private ReactorCommands() {}
    static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("cosmicheart").requires(source -> source.hasPermission(2))
                .then(Commands.literal("build").then(Commands.argument("origin", BlockPosArgument.blockPos())
                        .executes(context -> build(context.getSource(), BlockPosArgument.getBlockPos(context, "origin"))))));
    }
    private static CommandSyntaxException error(String key, Object... args) {
        return new SimpleCommandExceptionType(Component.translatable("command.useless_generators." + key, args)).create();
    }
    private static int build(CommandSourceStack source, BlockPos origin) throws CommandSyntaxException {
        var level = source.getLevel();
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + ReactorLayout.HEIGHT > level.getMaxBuildHeight()) throw error("height");
        for(int x=origin.getX()>>4;x<=(origin.getX()+ReactorLayout.WIDTH-1)>>4;x++)
            for(int z=origin.getZ()>>4;z<=(origin.getZ()+ReactorLayout.WIDTH-1)>>4;z++)
                if(!level.hasChunkAt(new BlockPos(x<<4,origin.getY(),z<<4)))throw error("unloaded");
        // Check the entire envelope before writing: no existing terrain, structures or BEs.
        for (int y = 0; y < ReactorLayout.HEIGHT; y++) for (int z = 0; z < ReactorLayout.WIDTH; z++) for (int x = 0; x < ReactorLayout.WIDTH; x++) {
            BlockPos pos = origin.offset(x, y, z);
            if (!level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).isAir())
                throw error("obstructed", pos.getX(), pos.getY(), pos.getZ());
        }
        for (var cell : ReactorLayout.CELLS) {
            if (cell.part() == ReactorLayout.Part.AIR) continue;
            var state = ReactorStructure.block(cell.part()).defaultBlockState();
            if (cell.part() == ReactorLayout.Part.CONTROLLER) state = state.setValue(GeneratorBlock.FACING, Direction.NORTH);
            level.setBlock(origin.offset(cell.x(), cell.y(), cell.z()), state, 3);
            if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                    && level.getBlockEntity(origin.offset(cell.x(), cell.y(), cell.z())) instanceof MeHatchBlockEntity hatch) hatch.setOwner(player);
        }
        BlockPos controller = origin.offset(ReactorLayout.CONTROLLER.x(), ReactorLayout.CONTROLLER.y(), ReactorLayout.CONTROLLER.z());
        if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player
                && level.getBlockEntity(controller) instanceof GeneratorBlockEntity generator) generator.setOwner(player);
        source.sendSuccess(() -> Component.translatable("command.useless_generators.built", controller.getX(), controller.getY(), controller.getZ()), true);
        return ReactorLayout.MATERIAL_COUNT;
    }
}
