package com.sorrowmist.uselessgenerators;

import appeng.api.stacks.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

public final class MeHatchMenu extends AbstractContainerMenu {
    private final MeHatchBlockEntity hatch;
    private final Player player;
    private final BlockPos pos;
    private long lastRevision = -1, lastSync = -10;
    private int lastState = -1;
    private CompoundTag snapshot = new CompoundTag();
    private final GenericStack[] marks = new GenericStack[16], stocks = new GenericStack[32];
    public MeHatchMenu(int id, Inventory inventory, FriendlyByteBuf data) { this(id, inventory, data.readBlockPos()); }
    public MeHatchMenu(int id, Inventory inventory, BlockPos pos) {
        super(UselessGenerators.HATCH_MENU.get(), id);
        player = inventory.player; this.pos = pos.immutable();
        hatch = player.level().getBlockEntity(pos) instanceof MeHatchBlockEntity be ? be : null;
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, MeHatchLayout.INVENTORY_X + column * 18, MeHatchLayout.INVENTORY_Y + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, MeHatchLayout.INVENTORY_X + column * 18, MeHatchLayout.HOTBAR_Y));
        if (hatch != null) snapshot.putInt("Kind", hatch.kind.ordinal());
    }
    public MeHatchKind kind() { return MeHatchKind.values()[Math.max(0, Math.min(MeHatchKind.values().length - 1, snapshot.getInt("Kind")))]; }
    public CompoundTag snapshot() { return snapshot; }
    public void receive(CompoundTag tag) {
        if (tag == null) return;
        snapshot = tag; java.util.Arrays.fill(marks, null); java.util.Arrays.fill(stocks, null);
        for (var value : tag.getList("Entries", 10)) {
            var entry = (CompoundTag) value; int i = entry.getInt("Slot");
            if (i < 0 || i >= 32) continue;
            if (i < 16 && entry.contains("Mark")) { var key = AEKey.fromTagGeneric(entry.getCompound("Mark")); if (key != null) marks[i] = new GenericStack(key, entry.getLong("Target")); }
            if (entry.contains("Key")) { var key = AEKey.fromTagGeneric(entry.getCompound("Key")); if (key != null) stocks[i] = new GenericStack(key, entry.getLong("Count")); }
        }
    }
    public GenericStack stack(int slot, boolean mark) {
        var values = mark ? marks : stocks;
        return slot >= 0 && slot < values.length ? values[slot] : null;
    }
    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof net.minecraft.server.level.ServerPlayer server) || hatch == null) return;
        long now = player.level().getGameTime();
        if (now - lastSync < 5) return;
        int state = (hatch.online() ? 1 : 0) | (hatch.linked() ? 2 : 0) | (hatch.paused() ? 4 : 0);
        if (lastRevision == hatch.revision() && state == lastState) return;
        lastSync = now; lastRevision = hatch.revision(); lastState = state;
        MeHatchNetwork.sync(server, containerId, hatch.menuTag());
    }
    public void mark(Player player,int slot,AEKey key) {
        if(hatch==null || player.level().isClientSide || !stillValid(player) || key==null
                || !hatch.kind.input() || slot<0 || slot>=16) return;
        long target=key.equals(hatch.mark(slot)) ? hatch.target(slot) : hatch.kind==MeHatchKind.FLUID_INPUT ? 64000 : 64;
        if(hatch.configure(slot,key,target)) {lastSync=-10;broadcastChanges();}
    }
    public void action(Player player, int action, int slot, long amount) {
        if (hatch == null || !stillValid(player) || player.level().isClientSide) return;
        if (action == 3) hatch.toggle();
        else if (action == 8) hatch.wake();
        else if (slot >= 0 && slot < (hatch.kind.input() ? 16 : hatch.slots())) {
            if (action == 0 && hatch.kind.input() && !getCarried().isEmpty()) {
                ItemStack held = getCarried(); AEKey key = null;
                GenericStack wrapped = GenericStack.unwrapItemStack(held);
                if (wrapped != null) key = wrapped.what();
                else if (hatch.kind == MeHatchKind.ITEM_INPUT) key = AEItemKey.of(held);
                else { FluidStack fluid = FluidUtil.getFluidContained(held).orElse(FluidStack.EMPTY); if (!fluid.isEmpty()) key = AEFluidKey.of(fluid); }
                if (key != null) hatch.configure(slot, key, hatch.kind == MeHatchKind.FLUID_INPUT ? 64000 : 64);
            } else if (action == 1 && hatch.kind.input()) hatch.configure(slot, null, 0);
            else if (action == 2 && hatch.kind.input() && hatch.mark(slot) != null && amount > 0) hatch.configure(slot, hatch.mark(slot), amount);
            else if (action == 4 && getCarried().isEmpty() && hatch.stock(slot) instanceof AEItemKey key) {
                int count = (int) hatch.extract(key, Math.min(key.getMaxStackSize(), hatch.amount(slot)), false);
                if (count > 0) setCarried(key.toStack(count));
            } else if (action == 5 && hatch.kind == MeHatchKind.OUTPUT && !getCarried().isEmpty()) {
                // Fluid containers are emptied without turning the resource into a bucket item.
                var result = FluidUtil.tryEmptyContainerAndStow(getCarried(), hatch.fluidHandler(), new InvWrapper(player.getInventory()), Integer.MAX_VALUE, player, true);
                if (result.isSuccess()) setCarried(result.getResult());
                else if (!GenericStack.isWrapped(getCarried())) {
                    long inserted = hatch.insert(AEItemKey.of(getCarried()), getCarried().getCount(), false);
                    ItemStack remainder = getCarried().copy(); remainder.shrink((int) inserted); setCarried(remainder);
                }
            } else if (action == 6 && !getCarried().isEmpty() && hatch.stock(slot) instanceof AEFluidKey key) {
                var handler = new IFluidHandler() {
                    @Override public int getTanks() { return 1; }
                    @Override public FluidStack getFluidInTank(int tank) { return key.toStack((int) hatch.amount(slot)); }
                    @Override public int getTankCapacity(int tank) { return (int) hatch.capacity(key); }
                    @Override public boolean isFluidValid(int tank, FluidStack fluid) { return false; }
                    @Override public int fill(FluidStack fluid, FluidAction mode) { return 0; }
                    @Override public FluidStack drain(FluidStack fluid, FluidAction mode) { return key.matches(fluid) ? drain(fluid.getAmount(), mode) : FluidStack.EMPTY; }
                    @Override public FluidStack drain(int count, FluidAction mode) { return key.toStack((int) hatch.extract(key, count, mode.simulate())); }
                };
                var result = FluidUtil.tryFillContainerAndStow(getCarried(), handler, new InvWrapper(player.getInventory()), Integer.MAX_VALUE, player, true);
                if (result.isSuccess()) setCarried(result.getResult());
            }
        }
        lastSync = -10; broadcastChanges();
    }
    @Override public boolean stillValid(Player player) {
        return hatch != null && !hatch.isRemoved() && player.level().getBlockEntity(pos) == hatch
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), original = stack.copy();
        if (hatch != null && hatch.kind == MeHatchKind.OUTPUT && !GenericStack.isWrapped(stack)) {
            long inserted = hatch.insert(AEItemKey.of(stack), stack.getCount(), false);
            if (inserted == 0) return ItemStack.EMPTY;
            stack.shrink((int) inserted);
        } else if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
