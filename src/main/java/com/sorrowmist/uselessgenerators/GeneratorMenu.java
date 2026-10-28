package com.sorrowmist.uselessgenerators;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class GeneratorMenu extends AbstractContainerMenu {
    public static final int DATA_SIZE = 59;
    public static final int CONTAINER_ACTION = 12;
    public static final int RECHECK_ACTION = 13;
    public static final int WORK_ACTION = 14;
    public static final int CONSTRUCTION_PATTERN_ACTION = 15;
    private final BlockPos pos;
    private final GeneratorBlockEntity generator;
    private final ContainerData data;
    public GeneratorMenu(int id, Inventory inventory, FriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos()); }
    public GeneratorMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, false);
    }
    public GeneratorMenu(int id, Inventory inventory, BlockPos pos, boolean cosmic) {
        super(cosmic ? UselessGenerators.COSMIC_MENU.get() : UselessGenerators.MENU.get(), id);
        this.pos = pos.immutable();
        generator = inventory.player.level().getBlockEntity(pos) instanceof GeneratorBlockEntity entity ? entity : null;
        data = inventory.player.level().isClientSide || generator == null ? new SimpleContainerData(DATA_SIZE) : new ContainerData() {
            @Override public int get(int index) {
                if (index < 20) {
                    long value = switch (index / 4) {
                        case 0 -> generator.energy.stored(); case 1 -> generator.energy.capacity();
                        case 2 -> generator.energy.rate();
                        case 3 -> generator.generatedLastTick; default -> generator.fuelRemainder();
                    };
                    return MenuNumbers.word(value, index % 4);
                }
                if (index == 20) return generator.fuelTank.getFluidAmount();
                if (index == 21 || index == 22) return MenuNumbers.word(BuiltInRegistries.FLUID.getId(generator.fuelTank.getFluid().getFluid()), index - 21);
                if (index == 23) return generator.isAeConnected() ? 1 : 0;
                if (index < 30) return generator.fluidMode(Direction.values()[index - 24]).ordinal();
                if (index < 36) return generator.energyMode(Direction.values()[index - 30]).ordinal();
                if (index == 36) return generator.getBlockState().getValue(GeneratorBlock.FACING).ordinal();
                var reactor = generator.reactor;
                if (reactor == null) return 0;
                if (index == 52) return generator.workEnabled ? 1 : 0;
                if (index >= 53 && index < 58) return reactor.hatchCount(MeHatchKind.values()[index - 53]);
                if (index == 58) return reactor.onlineHatches();
                if (index == 37) return reactor.status().ordinal();
                if (index == 38) return reactor.checked();
                if (index == 39) return reactor.expected() == null ? 0 : reactor.expected().ordinal() + 1;
                BlockPos location = index < 46 ? reactor.problem() : reactor.origin();
                if (location == null) return 0;
                int offset = (index - 40) % 6;
                int coordinate = offset < 2 ? location.getX() : offset < 4 ? location.getY() : location.getZ();
                return MenuNumbers.word(coordinate, offset % 2);
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return DATA_SIZE; }
        };
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, (cosmic ? 80 : 62) + column * 18, (cosmic ? 156 : 158) + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, (cosmic ? 80 : 62) + column * 18, cosmic ? 214 : 216));
    }
    public boolean workEnabled() { return data.get(52) != 0; }
    public int hatchCount(MeHatchKind kind) { return data.get(53 + kind.ordinal()); }
    public int onlineHatches() { return data.get(58); }
    public long stored() { return MenuNumbers.join(data::get, 0); }
    public long capacity() { return MenuNumbers.join(data::get, 4); }
    public long maximumRate() { return MenuNumbers.join(data::get, 8); }
    public long generated() { return MenuNumbers.join(data::get, 12); }
    public long fuelRemainder() { return MenuNumbers.join(data::get, 16); }
    public boolean aeConnected() { return data.get(23) != 0; }
    public boolean isReactor() { return maximumRate() == ReactorLayout.RATE; }
    public ReactorStructure.Status structureStatus() {
        int value = data.get(37);
        return ReactorStructure.Status.values()[Math.max(0, Math.min(ReactorStructure.Status.values().length - 1, value))];
    }
    public int structureChecked() { return data.get(38); }
    public int expectedPart() { return data.get(39) - 1; }
    private int coordinate(int start) { return (data.get(start) & 0xffff) | ((data.get(start + 1) & 0xffff) << 16); }
    public BlockPos structureProblem() { return new BlockPos(coordinate(40), coordinate(42), coordinate(44)); }
    public BlockPos structureOrigin() { return new BlockPos(coordinate(46), coordinate(48), coordinate(50)); }
    public PortMode mode(boolean fluid, Direction side) { return PortMode.decode(data.get((fluid ? 24 : 30) + side.ordinal())); }
    public Direction front() { return Direction.values()[Math.max(0, Math.min(5, data.get(36)))]; }
    public FluidStack fuel() {
        int fluidId = (data.get(21) & 0xffff) | ((data.get(22) & 0xffff) << 16);
        var fluid = BuiltInRegistries.FLUID.byId(fluidId);
        return fluid == null || data.get(20) <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, data.get(20));
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || player.level().isClientSide || generator == null) return false;
        if (button >= 0 && button < 12 && generator.reactor == null) {
            generator.cyclePort(button < 6, Direction.values()[button % 6]);
            broadcastChanges(); return true;
        }
        if(button==CONSTRUCTION_PATTERN_ACTION && generator.reactor!=null){
            int blankSlot=-1;
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)
                if(appeng.core.definitions.AEItems.BLANK_PATTERN.isSameAs(player.getInventory().getItem(slot))){blankSlot=slot;break;}
            if(blankSlot<0 && !player.getAbilities().instabuild){
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.useless_generators.cosmic.pattern_blank"),true);return false;
            }
            if(!player.getAbilities().instabuild)player.getInventory().removeItem(blankSlot,1);
            player.getInventory().placeItemBackInInventory(new ItemStack(UselessGenerators.COSMIC_CONSTRUCTION_PATTERN.get()),false);
            player.getInventory().setChanged();broadcastChanges();return true;
        }
        if (button == RECHECK_ACTION && generator.reactor != null) {
            generator.reactor.recheck(); broadcastChanges(); return true;
        }
        if (button == WORK_ACTION && generator.reactor != null) {
            generator.workEnabled = !generator.workEnabled; generator.setChanged(); broadcastChanges(); return true;
        }
        if (button == CONTAINER_ACTION && generator.reactor == null && !getCarried().isEmpty()) {
            // Manual GUI access is internal; sided port modes control pipes/world-face interaction.
            var tank = generator.fuelTank;
            var inventory = new InvWrapper(player.getInventory());
            var result = FluidUtil.tryEmptyContainerAndStow(getCarried(), tank, inventory, 1000, player, true);
            if (!result.isSuccess()) result = FluidUtil.tryFillContainerAndStow(getCarried(), tank, inventory, 1000, player, true);
            if (result.isSuccess()) { setCarried(result.getResult()); broadcastChanges(); }
            else player.displayClientMessage(FluidInteractionFeedback.explain(getCarried(), generator, null), true);
            return result.isSuccess();
        }
        return false;
    }
    @Override public boolean stillValid(Player player) {
        return generator != null && !generator.isRemoved() && player.level().getBlockEntity(pos) == generator
                && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy(); ItemStack stack = slot.getItem();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
