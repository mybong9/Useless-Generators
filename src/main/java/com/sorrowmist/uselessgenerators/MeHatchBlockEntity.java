package com.sorrowmist.uselessgenerators;

import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.*;
import appeng.api.networking.security.*;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import com.sorrowmist.uselesscore.api.logistics.LongEnergyHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import java.util.EnumSet;
import java.util.Objects;

/** Finite local buffers; network operations only address configured keys, never the whole network. */
public final class MeHatchBlockEntity extends BlockEntity implements IInWorldGridNodeHost, IActionHost, MenuProvider {
    public static final Capability<MEStorage> KEY_STORAGE = com.sorrowmist.uselesscore.api.capability.CoreCapabilities.KeyStorage.BLOCK;
    public static final Capability<LongEnergyHandler> LONG_ENERGY = com.sorrowmist.uselesscore.api.capability.CoreCapabilities.LongEnergy.BLOCK;
    public final MeHatchKind kind;
    private final AEKey[] marks = new AEKey[16], stock = new AEKey[32];
    private final long[] targets = new long[16], amounts = new long[32];
    private final IManagedGridNode node;
    private final IActionSource actionSource = IActionSource.ofMachine(this);
    private final HatchTransferPolicy schedule = new HatchTransferPolicy();
    private final GeneratorEnergy energy = new GeneratorEnergy(ReactorLayout.RATE, ReactorLayout.RATE);
    private BlockPos controller;
    private int cursor;
    private long nextWork, revision, lastTransfer, nextEnergyCheck;
    private boolean paused, unloading, networkOperation;
    private LazyOptional<IInWorldGridNodeHost> gridCap;
    private LazyOptional<IItemHandler> itemCap;
    private LazyOptional<IFluidHandler> fluidCap;
    private LazyOptional<IEnergyStorage> energyCap;
    private LazyOptional<MEStorage> keyCap;
    private LazyOptional<LongEnergyHandler> longCap;

    public MeHatchBlockEntity(BlockPos pos, BlockState state) {
        super(UselessGenerators.ME_HATCH.get(), pos, state);
        kind = ((MeHatchBlock) state.getBlock()).kind;
        node = GridHelper.createManagedNode(this, new IGridNodeListener<MeHatchBlockEntity>() {
            @Override public void onSaveChanges(MeHatchBlockEntity owner, IGridNode ignored) { owner.setChanged(); }
            @Override public void onStateChanged(MeHatchBlockEntity owner, IGridNode ignored, State state) { owner.wake(); }
            @Override public void onGridChanged(MeHatchBlockEntity owner, IGridNode ignored) { owner.wake(); }
        }).setInWorldNode(true).setTagName("ae_node").setIdlePowerUsage(1)
                // The connector and energy port provide network access/power, not a channel-consuming service.
                .setFlags(kind == MeHatchKind.CONNECTOR || kind == MeHatchKind.ENERGY
                        ? new GridFlags[] { GridFlags.CANNOT_CARRY }
                        : new GridFlags[] { GridFlags.CANNOT_CARRY, GridFlags.REQUIRE_CHANNEL })
                .setExposedOnSides(EnumSet.allOf(Direction.class)).setVisualRepresentation(state.getBlock());
        rebuildCaps();
    }
    public int slots() { return kind == MeHatchKind.OUTPUT ? 32 : kind.input() ? 16 : 0; }
    public boolean paused() { return paused; }
    public long revision() { return revision; }
    public long storedEnergy() { return energy.stored(); }
    public long lastTransfer() { return lastTransfer; }
    public long waitTicks() { return level == null ? 0 : Math.max(0, nextWork - level.getGameTime()); }
    public boolean online() { return endpoint(true) != null; }
    public boolean linked() { return reactor() != null; }
    public AEKey mark(int slot) { return slot >= 0 && slot < 16 ? marks[slot] : null; }
    public long target(int slot) { return slot >= 0 && slot < 16 ? targets[slot] : 0; }
    public AEKey stock(int slot) { return slot >= 0 && slot < slots() ? stock[slot] : null; }
    public long amount(int slot) { return slot >= 0 && slot < slots() ? amounts[slot] : 0; }
    public long capacity(AEKey key) { return kind.input() ? HatchTransferPolicy.MAX_TARGET : key instanceof AEItemKey ? HatchTransferPolicy.ITEM_CAPACITY : HatchTransferPolicy.FLUID_CAPACITY; }
    public void setOwner(Player player) { node.setOwningPlayer(player); }
    public void bind(BlockPos controller) { if (!Objects.equals(this.controller, controller)) { this.controller = controller == null ? null : controller.immutable(); wake(); } }
    public GeneratorBlockEntity reactor() {
        if (controller == null || level == null || !level.hasChunkAt(controller)) return null;
        return level.getBlockEntity(controller) instanceof GeneratorBlockEntity be && be.reactor != null && be.reactor.formed() ? be : null;
    }
    private boolean directlyConnected() { return node.getNode() != null && !node.getNode().getConnections().isEmpty(); }
    boolean directEndpoint(boolean active) {
        return !unloading && !isRemoved() && directlyConnected() && (!active || node.isActive());
    }
    /** Prefer a directly wired network. Never join or bridge two grids through the structure. */
    private MeHatchBlockEntity endpoint(boolean active) {
        if (unloading || isRemoved()) return null;
        if (directEndpoint(active)) return this;
        var reactor = reactor();
        return reactor == null ? null : reactor.reactor.networkEndpoint(active);
    }
    public void wake() { nextWork = 0; nextEnergyCheck = 0; schedule.wake(); revision++; }
    private void changed() { revision++; setChanged(); }
    private void changedAndWake() { changed(); wake(); }
    public void toggle() { paused = !paused; changedAndWake(); }
    public boolean configure(int slot, AEKey key, long amount) {
        if (!kind.input() || slot < 0 || slot >= 16) return false;
        if (key != null && !(kind == MeHatchKind.ITEM_INPUT ? key instanceof AEItemKey : key instanceof AEFluidKey)) return false;
        // Keep the old stock until it can be returned. Re-marking never deletes resources.
        marks[slot] = key;
        targets[slot] = key == null ? 0 : HatchTransferPolicy.target(amount);
        changedAndWake(); return true;
    }
    public static void tick(Level level, BlockPos pos, BlockState state, MeHatchBlockEntity hatch) {
        hatch.ensureNode();
        if (hatch.kind.energy()) {
            hatch.energy.tick(0);
            if (hatch.energy.stored() > 0) hatch.sendEnergy(hatch.energy);
        }
        if (hatch.paused || hatch.kind == MeHatchKind.CONNECTOR || hatch.kind == MeHatchKind.ENERGY || level.getGameTime() < hatch.nextWork) return;
        var endpoint = hatch.endpoint(true);
        boolean moved = false;
        if (endpoint != null) {
            MEStorage network = endpoint.node.getGrid().getStorageService().getInventory();
            // Four addresses at most per work cycle; no getAvailableStacks on the ME network.
            int attempts = 0;
            for (int i = 0; i < hatch.slots() && attempts < HatchTransferPolicy.OPERATIONS; i++) {
                int slot = hatch.cursor++ % hatch.slots();
                boolean needsWork = hatch.kind == MeHatchKind.OUTPUT ? hatch.stock[slot] != null
                        : !Objects.equals(hatch.stock[slot], hatch.marks[slot]) || hatch.amounts[slot] != hatch.targets[slot];
                if (!needsWork) continue;
                attempts++;
                moved |= hatch.transferSlot(slot, network, endpoint.actionSource);
            }
            if (hatch.cursor >= hatch.slots()) hatch.cursor %= hatch.slots();
        }
        hatch.nextWork = level.getGameTime() + hatch.schedule.nextDelay(moved);
    }
    private boolean transferSlot(int slot, MEStorage network, IActionSource source) {
        networkOperation = true;
        try { return transferSlotContents(slot, network, source); }
        finally { networkOperation = false; }
    }
    private boolean transferSlotContents(int slot, MEStorage network, IActionSource source) {
        AEKey key = stock[slot]; long count = amounts[slot]; boolean moved = false;
        long keep = kind == MeHatchKind.OUTPUT || !Objects.equals(key, marks[slot]) ? 0 : targets[slot];
        if (key != null && count > keep) {
            long offer = Math.min(count - keep, operationLimit(key));
            long returned = HatchTransferPolicy.accepted(network.insert(key, offer, Actionable.MODULATE, source), offer);
            if (returned > 0) { remove(slot, returned); moved = true; }
        }
        if (kind.input() && marks[slot] != null && (stock[slot] == null || marks[slot].equals(stock[slot]))) {
            long needed = Math.min(Math.max(0, targets[slot] - amounts[slot]), operationLimit(marks[slot]));
            if (needed > 0) {
                long received = HatchTransferPolicy.accepted(network.extract(marks[slot], needed, Actionable.MODULATE, source), needed);
                if (received > 0) { stock[slot] = marks[slot]; amounts[slot] += received; changed(); moved = true; }
            }
        }
        return moved;
    }
    private long operationLimit(AEKey key) { return key instanceof AEItemKey ? 4096 : 2_000_000; }
    private void remove(int slot, long count) {
        amounts[slot] -= Math.min(amounts[slot], Math.max(0, count));
        if (amounts[slot] == 0) stock[slot] = null;
        changed();
    }
    public long insert(AEKey key, long amount, boolean simulate) {
        if (networkOperation || key == null || amount <= 0 || kind != MeHatchKind.OUTPUT) return 0;
        long accepted = 0;
        for (int pass = 0; pass < 2; pass++) for (int i = 0; i < slots() && accepted < amount; i++) {
            if (pass == 0 ? !key.equals(stock[i]) : stock[i] != null) continue;
            long n = Math.min(amount - accepted, HatchTransferPolicy.room(capacity(key), amounts[i]));
            if (n > 0) { if (!simulate) { stock[i] = key; amounts[i] += n; } accepted += n; }
        }
        if (accepted > 0 && !simulate) changedAndWake();
        return accepted;
    }
    public long extract(AEKey key, long amount, boolean simulate) {
        if (networkOperation || key == null || amount <= 0) return 0;
        long extracted = 0;
        for (int i = 0; i < slots() && extracted < amount; i++) if (key.equals(stock[i])) {
            long n = Math.min(amount - extracted, amounts[i]);
            extracted += n; if (n > 0 && !simulate) remove(i, n);
        }
        if (extracted > 0 && !simulate) wake();
        return extracted;
    }
    public void feedFuel(GeneratorBlockEntity generator) {
        if (kind != MeHatchKind.FLUID_INPUT || paused) return;
        int room = generator.fuelTank.getCapacity() - generator.fuelTank.getFluidAmount();
        if (room <= 0) return;
        for (int i = 0; i < slots() && room > 0; i++) if (stock[i] instanceof AEFluidKey key) {
            FluidStack offered = key.toStack((int) Math.min(room, amounts[i]));
            int accepted = generator.fuelTank.fill(offered, IFluidHandler.FluidAction.EXECUTE);
            if (accepted > 0) { remove(i, accepted); room -= accepted; wake(); }
        }
    }
    /** The reactor calls this after resetting its shared output budget, so there is no tick-order duplication. */
    public long sendEnergy(GeneratorEnergy source) {
        if (paused || !kind.energy() || level == null || source.stored() <= 0 || level.getGameTime() < nextEnergyCheck) return 0;
        var endpoint = endpoint(false);
        if (endpoint == null) { nextEnergyCheck = level.getGameTime() + 20; return 0; }
        var grid = endpoint.node.getGrid();
        long sent = NetworkEnergyTransfer.transfer(source, PowerUnits.FE.convertTo(PowerUnits.AE, 1),
                offered -> grid.getEnergyService().injectPower(offered, Actionable.MODULATE));
        if (endpoint.node.isActive()) sent += FluxEnergyTransfer.transfer(source,
                offered -> AppliedFluxCompat.insertFe(grid, offered, endpoint.actionSource));
        lastTransfer = sent;
        nextEnergyCheck = level.getGameTime() + (sent > 0 ? 0 : 20);
        if (sent > 0) changed();
        return sent;
    }
    public IFluidHandler fluidHandler() { return fluids; }
    private final IItemHandler items = new IItemHandler() {
        @Override public int getSlots() { return slots(); }
        @Override public ItemStack getStackInSlot(int slot) { return stock(slot) instanceof AEItemKey key ? key.toStack((int) amount(slot)) : ItemStack.EMPTY; }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (networkOperation || slot < 0 || slot >= slots() || stack.isEmpty() || kind != MeHatchKind.OUTPUT || GenericStack.isWrapped(stack)) return stack;
            AEItemKey key = AEItemKey.of(stack);
            if (stock[slot] != null && !key.equals(stock[slot])) return stack;
            int n = (int) Math.min(stack.getCount(), HatchTransferPolicy.room(capacity(key), amounts[slot]));
            if (n > 0 && !simulate) { stock[slot] = key; amounts[slot] += n; changedAndWake(); }
            ItemStack remainder = stack.copy(); remainder.shrink(n); return remainder;
        }
        @Override public ItemStack extractItem(int slot, int count, boolean simulate) {
            if (networkOperation || !(stock(slot) instanceof AEItemKey key) || count <= 0) return ItemStack.EMPTY;
            int n = (int) Math.min(Math.min(count, key.getMaxStackSize()), amount(slot));
            if (!simulate && n > 0) { remove(slot, n); wake(); }
            return key.toStack(n);
        }
        @Override public int getSlotLimit(int slot) { return (int) (kind.input() ? HatchTransferPolicy.MAX_TARGET : HatchTransferPolicy.ITEM_CAPACITY); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return kind == MeHatchKind.OUTPUT && !GenericStack.isWrapped(stack); }
    };
    private final IFluidHandler fluids = new IFluidHandler() {
        @Override public int getTanks() { return slots(); }
        @Override public FluidStack getFluidInTank(int slot) { return stock(slot) instanceof AEFluidKey key ? key.toStack((int) amount(slot)) : FluidStack.EMPTY; }
        @Override public int getTankCapacity(int slot) { return (int) (kind.input() ? HatchTransferPolicy.MAX_TARGET : HatchTransferPolicy.FLUID_CAPACITY); }
        @Override public boolean isFluidValid(int slot, FluidStack stack) { return kind == MeHatchKind.OUTPUT; }
        @Override public int fill(FluidStack stack, FluidAction action) { return (int) insert(AEFluidKey.of(stack), stack.getAmount(), action.simulate()); }
        @Override public FluidStack drain(FluidStack stack, FluidAction action) {
            long n = extract(AEFluidKey.of(stack), stack.getAmount(), action.simulate());
            return n <= 0 ? FluidStack.EMPTY : AEFluidKey.of(stack).toStack((int) n);
        }
        @Override public FluidStack drain(int count, FluidAction action) {
            for (int i = 0; i < slots(); i++) if (stock[i] instanceof AEFluidKey key) return drain(key.toStack(Math.max(0, count)), action);
            return FluidStack.EMPTY;
        }
    };
    private final MEStorage keys = new MEStorage() {
        @Override public long insert(AEKey key, long count, Actionable mode, IActionSource source) { return MeHatchBlockEntity.this.insert(key, count, mode == Actionable.SIMULATE); }
        @Override public long extract(AEKey key, long count, Actionable mode, IActionSource source) { return MeHatchBlockEntity.this.extract(key, count, mode == Actionable.SIMULATE); }
        @Override public void getAvailableStacks(KeyCounter out) { for (int i = 0; i < slots(); i++) if (stock[i] != null) out.add(stock[i], amounts[i]); }
        @Override public Component getDescription() { return getDisplayName(); }
    };
    private long receiveEnergy(long amount, boolean simulate) {
        long received = kind.energy() ? energy.receive(amount, simulate) : 0;
        if (received > 0 && !simulate) changedAndWake(); return received;
    }
    private final IEnergyStorage forgeEnergy = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) { return (int) MeHatchBlockEntity.this.receiveEnergy(amount, simulate); }
        @Override public int extractEnergy(int amount, boolean simulate) { return 0; }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, energy.stored()); }
        @Override public int getMaxEnergyStored() { return Integer.MAX_VALUE; }
        @Override public boolean canExtract() { return false; }
        @Override public boolean canReceive() { return kind.energy(); }
    };
    private final LongEnergyHandler longEnergy = new LongEnergyHandler() {
        @Override public long extract(long amount, boolean simulate) { return 0; }
        @Override public long receive(long amount, boolean simulate) { return receiveEnergy(amount, simulate); }
        @Override public long stored() { return energy.stored(); }
        @Override public long capacity() { return energy.capacity(); }
        @Override public boolean canExtract() { return false; }
    };
    LongEnergyHandler longEnergyHandler() { return longEnergy; }
    private void rebuildCaps() {
        gridCap = LazyOptional.of(() -> this); itemCap = LazyOptional.of(() -> items); fluidCap = LazyOptional.of(() -> fluids);
        keyCap = LazyOptional.of(() -> keys); energyCap = LazyOptional.of(() -> forgeEnergy); longCap = LazyOptional.of(() -> longEnergy);
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST) return gridCap.cast();
        if (cap == ForgeCapabilities.ITEM_HANDLER && (kind == MeHatchKind.ITEM_INPUT || kind == MeHatchKind.OUTPUT)) return itemCap.cast();
        if (cap == ForgeCapabilities.FLUID_HANDLER && (kind == MeHatchKind.FLUID_INPUT || kind == MeHatchKind.OUTPUT)) return fluidCap.cast();
        if (cap == KEY_STORAGE && slots() > 0) return keyCap.cast();
        if (cap == ForgeCapabilities.ENERGY && kind.energy()) return energyCap.cast();
        if (cap == LONG_ENERGY && kind.energy()) return longCap.cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); gridCap.invalidate(); itemCap.invalidate(); fluidCap.invalidate(); keyCap.invalidate(); energyCap.invalidate(); longCap.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); rebuildCaps(); }
    @Override public IGridNode getGridNode(Direction side) { return node.getNode(); }
    @Override public IGridNode getActionableNode() { return node.getNode(); }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.SMART; }
    private void ensureNode() { if (level != null && !level.isClientSide && !unloading && !isRemoved() && !node.isReady()) node.create(level, worldPosition); }
    @Override public void onLoad() { super.onLoad(); unloading = false; GridHelper.onFirstTick(this, MeHatchBlockEntity::ensureNode); }
    @Override public void clearRemoved() { super.clearRemoved(); unloading = false; GridHelper.onFirstTick(this, MeHatchBlockEntity::ensureNode); }
    @Override public void setRemoved() { unloading = true; controller = null; node.destroy(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { unloading = true; controller = null; node.destroy(); super.onChunkUnloaded(); }
    public CompoundTag contentsTag() {
        CompoundTag tag = new CompoundTag(); ListTag entries = new ListTag();
        for (int i = 0; i < 32; i++) {
            CompoundTag entry = new CompoundTag(); entry.putInt("Slot", i);
            if (i < 16 && marks[i] != null) { entry.put("Mark", marks[i].toTagGeneric()); entry.putLong("Target", targets[i]); }
            if (stock[i] != null && amounts[i] > 0) { entry.put("Key", stock[i].toTagGeneric()); entry.putLong("Count", amounts[i]); }
            if (entry.size() > 1) entries.add(entry);
        }
        tag.put("Entries", entries); tag.putLong("Energy", energy.stored()); tag.putBoolean("Paused", paused); return tag;
    }
    public void restoreContents(CompoundTag tag) {
        java.util.Arrays.fill(marks, null); java.util.Arrays.fill(stock, null); java.util.Arrays.fill(targets, 0); java.util.Arrays.fill(amounts, 0);
        for (var value : tag.getList("Entries", 10)) {
            CompoundTag entry = (CompoundTag) value; int i = entry.getInt("Slot");
            if (i < 0 || i >= slots()) continue;
            if (kind.input() && i < 16 && entry.contains("Mark")) {
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("Mark"));
                if (kind == MeHatchKind.ITEM_INPUT ? key instanceof AEItemKey : key instanceof AEFluidKey) { marks[i] = key; targets[i] = Math.max(1, Math.min(capacity(key), entry.getLong("Target"))); }
            }
            if (entry.contains("Key")) {
                AEKey key = AEKey.fromTagGeneric(entry.getCompound("Key"));
                if (key != null && entry.getLong("Count") > 0) { stock[i] = key; amounts[i] = Math.min(capacity(key), entry.getLong("Count")); }
            }
        }
        energy.restore(tag.getLong("Energy")); paused = tag.getBoolean("Paused"); changedAndWake();
    }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.put("Contents", contentsTag()); node.saveToNBT(tag); }
    @Override public void load(CompoundTag tag) { super.load(tag); restoreContents(tag.getCompound("Contents")); node.loadFromNBT(tag); }
    public CompoundTag menuTag() {
        CompoundTag tag = contentsTag(); tag.putInt("Kind", kind.ordinal()); tag.putBoolean("Online", online()); tag.putBoolean("Linked", linked());
        tag.putLong("Transfer", lastTransfer); tag.putLong("Wait", waitTicks()); return tag;
    }
    @Override public Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new MeHatchMenu(id, inventory, worldPosition); }
}
