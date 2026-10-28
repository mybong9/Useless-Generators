package com.sorrowmist.uselessgenerators;

import java.util.EnumSet;
import appeng.api.config.Actionable;
import appeng.api.config.PowerUnits;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.util.AECableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public final class GeneratorBlockEntity extends BlockEntity implements IInWorldGridNodeHost, net.minecraft.world.MenuProvider, appeng.api.networking.security.IActionHost {
    public final GeneratorEnergy energy;
    public ReactorStructure reactor;
    public boolean cosmicFormed, cosmicWorking;
    public boolean workEnabled = true;
    public boolean constructionActive;
    private final IManagedGridNode mainNode;
    private int firstSide;
    private final PortMode[] fluidModes = new PortMode[6];
    private final PortMode[] energyModes = new PortMode[6];
    private final java.util.EnumMap<Direction, LazyOptional<IEnergyStorage>> energyPorts = new java.util.EnumMap<>(Direction.class);
    private final java.util.EnumMap<Direction, LazyOptional<IFluidHandler>> fluidPorts = new java.util.EnumMap<>(Direction.class);
    public long generatedLastTick;
    private boolean unloading;
    private LazyOptional<IInWorldGridNodeHost> gridHost = LazyOptional.of(() -> this);
    private final FuelBurn burn = new FuelBurn();
    public final FluidTank fuelTank;
    private final IEnergyStorage forgeEnergy = new IEnergyStorage() {
        @Override public int receiveEnergy(int amount, boolean simulate) {
            int accepted = (int) energy.receive(amount, simulate);
            if (accepted > 0 && !simulate) setChanged();
            return accepted;
        }
        @Override public int extractEnergy(int amount, boolean simulate) {
            int result = (int) energy.extract(amount, simulate);
            if (!simulate && result > 0) setChanged();
            return result;
        }
        @Override public int getEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, energy.stored()); }
        @Override public int getMaxEnergyStored() { return (int) Math.min(Integer.MAX_VALUE, energy.capacity()); }
        @Override public boolean canExtract() { return true; }
        @Override public boolean canReceive() { return true; }
    };
    public GeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(UselessGenerators.GENERATOR.get(), pos, state);
        GeneratorBlock block = (GeneratorBlock) state.getBlock();
        energy = new GeneratorEnergy(block.generationRate(), block.energyCapacity());
        reactor = block instanceof ReactorBlock ? new ReactorStructure(this) : null;
        java.util.Arrays.fill(fluidModes, PortMode.INPUT);
        java.util.Arrays.fill(energyModes, PortMode.OUTPUT);
        GeneratorTier tier = ((GeneratorBlock) state.getBlock()).tier;
        fuelTank = new FluidTank(16000, stack -> {
            FuelKind kind = GeneratorFuels.kind(stack.getFluid());
            return kind != null && kind.allowedIn(tier);
        }) {
            @Override protected void onContentsChanged() { setChanged(); }
        };
        mainNode = GridHelper.createManagedNode(this, (owner, node) -> owner.setChanged())
                .setInWorldNode(true)
                .setTagName("ae_node")
                .setIdlePowerUsage(0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setExposedOnSides(EnumSet.allOf(Direction.class))
                .setVisualRepresentation(state.getBlock());
        rebuildPorts();
    }
    public static void tick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        if (generator.reactor != null && !generator.reactor.matchesFacing(state.getValue(GeneratorBlock.FACING))) {
            generator.reactor.unload(); generator.reactor = new ReactorStructure(generator);
        }
        generator.ensureGridReady();
        boolean operational = generator.reactor == null || !generator.constructionActive && generator.reactor.tick();
        if (generator.reactor != null && operational && generator.workEnabled) generator.reactor.feedFuel();
        FluidStack fuel = generator.fuelTank.getFluid();
        FuelKind kind = fuel.isEmpty() ? null : GeneratorFuels.kind(fuel.getFluid());
        GeneratorTier tier = ((GeneratorBlock) state.getBlock()).tier;
        boolean valid = kind != null && kind.allowedIn(tier);
        FuelBurn.Result result = generator.burn.burn(operational && generator.workEnabled ? generator.energy.generationRoom() : 0,
                valid ? kind.perMb() : 0, valid ? fuel.getAmount() : 0);
        if (result.consumedMb() > 0) generator.fuelTank.drain(result.consumedMb(), IFluidHandler.FluidAction.EXECUTE);
        generator.energy.tick(result.generated());
        generator.generatedLastTick = result.generated();
        boolean networkWorking = operational && generator.isAeConnected();
        if (generator.reactor != null && (generator.cosmicFormed != operational || generator.cosmicWorking != networkWorking)) {
            generator.cosmicFormed = operational; generator.cosmicWorking = networkWorking;
            level.sendBlockUpdated(pos, state, state, 2);
        }
        if (generator.reactor != null) generator.reactor.exportEnergy();
        else generator.supplyAeNetwork();
        // Bound the number of int packets even at 9.2E FE/t; rotate faces for fairness.
        Direction[] sides = Direction.values();
        if (generator.reactor != null) {
            if (result.generated() > 0 || result.consumedMb() > 0) generator.setChanged();
            return;
        }
        for (int i = 0; i < sides.length; i++) {
            Direction side = sides[(generator.firstSide + i) % sides.length];
            BlockPos targetPos = pos.relative(side);
            if (!level.hasChunkAt(targetPos)) continue;
            BlockEntity target = level.getBlockEntity(targetPos);
            if (target == null) continue;
            if (generator.fluidMode(side).output && !generator.fuelTank.isEmpty()) {
                IFluidHandler destination = target.getCapability(ForgeCapabilities.FLUID_HANDLER, side.getOpposite()).resolve().orElse(null);
                if (destination != null) {
                    FluidStack offered = generator.fuelTank.drain(1000, IFluidHandler.FluidAction.SIMULATE);
                    int accepted = Math.max(0, Math.min(offered.getAmount(), destination.fill(offered, IFluidHandler.FluidAction.EXECUTE)));
                    if (accepted > 0) generator.fuelTank.drain(accepted, IFluidHandler.FluidAction.EXECUTE);
                }
            }
            if (!generator.energyMode(side).output) continue;
            if (FluxNetworksCompat.push(target, side.getOpposite(), generator.energy)) continue;
            IEnergyStorage receiver = target.getCapability(ForgeCapabilities.ENERGY, side.getOpposite()).resolve().orElse(null);
            if (receiver == null || !receiver.canReceive()) continue;
            ForgeEnergyTransfer.transfer(generator.energy, offered -> receiver.receiveEnergy(offered, false));
        }
        generator.firstSide = (generator.firstSide + 1) % sides.length;
        generator.setChanged();
    }
    private void supplyAeNetwork() {
        var grid = mainNode.getGrid();
        if (grid == null || !isAeConnected()) return;
        boolean[] connected = new boolean[6];
        for (Direction side : mainNode.getNode().getInWorldConnections().keySet()) connected[side.ordinal()] = true;
        PortMode networkMode = AePortPolicy.connectedMode(energyModes, connected);
        if (!networkMode.output) {
            if (!networkMode.input) return;
            long room = energy.receive(1L << 40, true);
            if (room <= 0) return;
            double possible = grid.getEnergyService().extractAEPower(PowerUnits.FE.convertTo(PowerUnits.AE, room),
                    Actionable.SIMULATE, appeng.api.config.PowerMultiplier.ONE);
            long wholeFe = Math.min(room, (long) Math.floor(PowerUnits.AE.convertTo(PowerUnits.FE, possible)));
            if (wholeFe > 0) {
                double extracted = grid.getEnergyService().extractAEPower(PowerUnits.FE.convertTo(PowerUnits.AE, wholeFe),
                        Actionable.MODULATE, appeng.api.config.PowerMultiplier.ONE);
                energy.receive((long) Math.floor(PowerUnits.AE.convertTo(PowerUnits.FE, extracted)), false);
            }
            return;
        }
        // AE2 injectPower returns the rejected remainder, NOT the accepted amount.
        NetworkEnergyTransfer.transfer(energy, PowerUnits.FE.convertTo(PowerUnits.AE, 1),
                offered -> grid.getEnergyService().injectPower(offered, Actionable.MODULATE));
        // Keep the ME network powered first, then store the remaining FE as AppFlux keys.
        // Both paths debit the same per-tick output budget, also shared with adjacent cables.
        if (mainNode.getNode().isActive()) {
            FluxEnergyTransfer.transfer(energy, offered -> AppliedFluxCompat.insertFe(grid, offered,
                    appeng.api.networking.security.IActionSource.ofMachine(this)));
        }
    }
    @Override public IGridNode getActionableNode() { return mainNode.getNode(); }
    public void setOwner(net.minecraft.world.entity.player.Player player) { mainNode.setOwningPlayer(player); }
    public boolean isAeConnected() {
        if (reactor != null) return reactor.networkEndpoint(true) != null;
        IGridNode node = mainNode.getNode();
        return node != null && !node.getConnections().isEmpty();
    }
    public long fuelRemainder() { return burn.remainder(); }
    public PortMode fluidMode(Direction side) { return fluidModes[side.ordinal()]; }
    public PortMode energyMode(Direction side) { return energyModes[side.ordinal()]; }
    public IFluidHandler fluidPort(Direction side) { return fluidPorts.get(side).orElseThrow(IllegalStateException::new); }
    public void cyclePort(boolean fluid, Direction side) {
        PortMode[] modes = fluid ? fluidModes : energyModes;
        modes[side.ordinal()] = modes[side.ordinal()].next();
        energyPorts.values().forEach(LazyOptional::invalidate);
        fluidPorts.values().forEach(LazyOptional::invalidate);
        rebuildPorts(); updateAePort(); setChanged();
        if (level != null) level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
    }
    private void updateAePort() {
        EnumSet<Direction> exposed = EnumSet.noneOf(Direction.class);
        if (reactor == null) for (Direction side : Direction.values()) if (energyMode(side) != PortMode.DISABLED) exposed.add(side);
        mainNode.setExposedOnSides(exposed);
    }
    private void rebuildPorts() {
        for (Direction side : Direction.values()) {
            energyPorts.put(side, LazyOptional.of(() -> new IEnergyStorage() {
                @Override public int receiveEnergy(int amount, boolean simulate) { return canReceive() ? forgeEnergy.receiveEnergy(amount, simulate) : 0; }
                @Override public int extractEnergy(int amount, boolean simulate) { return canExtract() ? forgeEnergy.extractEnergy(amount, simulate) : 0; }
                @Override public int getEnergyStored() { return forgeEnergy.getEnergyStored(); }
                @Override public int getMaxEnergyStored() { return forgeEnergy.getMaxEnergyStored(); }
                @Override public boolean canExtract() { return energyMode(side).output; }
                @Override public boolean canReceive() { return energyMode(side).input; }
            }));
            fluidPorts.put(side, LazyOptional.of(() -> new IFluidHandler() {
                @Override public int getTanks() { return 1; }
                @Override public FluidStack getFluidInTank(int tank) { return fuelTank.getFluidInTank(tank).copy(); }
                @Override public int getTankCapacity(int tank) { return fuelTank.getTankCapacity(tank); }
                @Override public boolean isFluidValid(int tank, FluidStack stack) { return fluidMode(side).input && fuelTank.isFluidValid(tank, stack); }
                @Override public int fill(FluidStack resource, FluidAction action) { return fluidMode(side).input ? fuelTank.fill(resource, action) : 0; }
                @Override public FluidStack drain(FluidStack resource, FluidAction action) { return fluidMode(side).output ? fuelTank.drain(resource, action) : FluidStack.EMPTY; }
                @Override public FluidStack drain(int amount, FluidAction action) { return fluidMode(side).output ? fuelTank.drain(amount, action) : FluidStack.EMPTY; }
            }));
        }
    }
    com.sorrowmist.uselesscore.api.logistics.LongEnergyHandler longEnergyPort(Direction side) {
        return new com.sorrowmist.uselesscore.api.logistics.LongEnergyHandler() {
            @Override public long extract(long amount, boolean simulate) {
                long moved = canExtract() ? energy.extract(amount, simulate) : 0;
                if (moved > 0 && !simulate) setChanged();
                return moved;
            }
            @Override public long receive(long amount, boolean simulate) {
                long moved = canReceive() ? energy.receive(amount, simulate) : 0;
                if (moved > 0 && !simulate) setChanged();
                return moved;
            }
            @Override public long stored() { return energy.stored(); }
            @Override public long capacity() { return energy.capacity(); }
            @Override public boolean canExtract() { return !isRemoved() && reactor == null && energyMode(side).output; }
            @Override public boolean canReceive() { return !isRemoved() && reactor == null && energyMode(side).input; }
        };
    }
    @Override public net.minecraft.network.chat.Component getDisplayName() { return getBlockState().getBlock().getName(); }
    @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inventory,
            net.minecraft.world.entity.player.Player player) { return new GeneratorMenu(id, inventory, worldPosition, reactor != null); }
    @Override public IGridNode getGridNode(Direction side) {
        return side == null || energyMode(side) != PortMode.DISABLED ? mainNode.getNode() : null;
    }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.GLASS; }
    @Override public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        GridHelper.onFirstTick(this, GeneratorBlockEntity::ensureGridReady);
    }
    @Override public void onLoad() { super.onLoad(); unloading = false; ReactorStructure.rememberClient(this); GridHelper.onFirstTick(this, GeneratorBlockEntity::ensureGridReady); }
    private void ensureGridReady() {
        if (level != null && !level.isClientSide && !unloading && !isRemoved() && !mainNode.isReady()) {
            mainNode.setInWorldNode(true).setIdlePowerUsage(0).setFlags(GridFlags.CANNOT_CARRY)
                    .setVisualRepresentation(getBlockState().getBlock());
            updateAePort(); mainNode.create(level, worldPosition);
        }
    }
    @Override public void setRemoved() { unloading = true; if (reactor != null) reactor.unload(); super.setRemoved(); mainNode.destroy(); }
    @Override public void onChunkUnloaded() { unloading = true; if (reactor != null) reactor.unload(); super.onChunkUnloaded(); mainNode.destroy(); }
    public CompoundTag portableContents() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Energy", energy.stored()); tag.put("FuelTank", fuelTank.writeToNBT(new CompoundTag()));
        tag.putLong("FuelRemainder", burn.remainder()); tag.putBoolean("WorkEnabled", workEnabled);
        return tag;
    }
    public void restorePortable(CompoundTag tag) {
        energy.restore(tag.getLong("Energy")); fuelTank.readFromNBT(tag.getCompound("FuelTank"));
        if (!fuelTank.isEmpty() && !fuelTank.isFluidValid(fuelTank.getFluid())) fuelTank.setFluid(FluidStack.EMPTY);
        if (fuelTank.getFluidAmount() > fuelTank.getCapacity()) fuelTank.getFluid().setAmount(fuelTank.getCapacity());
        burn.restore(tag.getLong("FuelRemainder")); workEnabled = !tag.contains("WorkEnabled") || tag.getBoolean("WorkEnabled");
        setChanged();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.putLong("Energy", energy.stored()); mainNode.saveToNBT(tag);
        tag.put("FuelTank", fuelTank.writeToNBT(new CompoundTag()));
        tag.putLong("FuelRemainder", burn.remainder());
        tag.putBoolean("WorkEnabled", workEnabled);
        for (Direction side : Direction.values()) {
            tag.putInt("FluidSide" + side.ordinal(), fluidMode(side).ordinal());
            tag.putInt("EnergySide" + side.ordinal(), energyMode(side).ordinal());
        }
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag); energy.restore(tag.getLong("Energy")); mainNode.loadFromNBT(tag);
        fuelTank.readFromNBT(tag.getCompound("FuelTank"));
        if (!fuelTank.isEmpty() && !fuelTank.isFluidValid(fuelTank.getFluid())) fuelTank.setFluid(FluidStack.EMPTY);
        burn.restore(tag.getLong("FuelRemainder"));
        workEnabled = !tag.contains("WorkEnabled") || tag.getBoolean("WorkEnabled");
        for (Direction side : Direction.values()) {
            int index = side.ordinal();
            fluidModes[index] = tag.contains("FluidSide" + index) ? PortMode.decode(tag.getInt("FluidSide" + index)) : PortMode.INPUT;
            energyModes[index] = tag.contains("EnergySide" + index) ? PortMode.decode(tag.getInt("EnergySide" + index)) : PortMode.OUTPUT;
        }
        updateAePort();
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (reactor != null && (cap == ForgeCapabilities.ENERGY || cap == ForgeCapabilities.FLUID_HANDLER
                || cap == appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST)) return LazyOptional.empty();
        if (cap == appeng.capabilities.Capabilities.IN_WORLD_GRID_NODE_HOST) return gridHost.cast();
        if (cap == ForgeCapabilities.ENERGY) return side == null || energyMode(side) == PortMode.DISABLED ? LazyOptional.empty() : energyPorts.get(side).cast();
        if (cap == ForgeCapabilities.FLUID_HANDLER) return side == null || fluidMode(side) == PortMode.DISABLED ? LazyOptional.empty() : fluidPorts.get(side).cast();
        return super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps();
        gridHost.invalidate();
        energyPorts.values().forEach(LazyOptional::invalidate); fluidPorts.values().forEach(LazyOptional::invalidate);
    }
    @Override public void reviveCaps() {
        super.reviveCaps();
        gridHost = LazyOptional.of(() -> this);
        rebuildPorts();
    }
    @Override public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("CosmicFormed", cosmicFormed); tag.putBoolean("CosmicWorking", cosmicWorking);
        return tag;
    }
    @Override public void handleUpdateTag(CompoundTag tag) {
        if (reactor != null && !reactor.matchesFacing(getBlockState().getValue(GeneratorBlock.FACING))) reactor = new ReactorStructure(this);
        cosmicFormed = tag.getBoolean("CosmicFormed"); cosmicWorking = tag.getBoolean("CosmicWorking");
    }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) handleUpdateTag(packet.getTag());
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        if (reactor == null) return super.getRenderBoundingBox();
        BlockPos origin = reactor.origin();
        return new net.minecraft.world.phys.AABB(origin.getX() + UniverseEffects.CENTER_X - UniverseEffects.HORIZONTAL_BOUND,
                origin.getY() + UniverseEffects.CENTER_Y - UniverseEffects.VERTICAL_BOUND,
                origin.getZ() + UniverseEffects.CENTER_Z - UniverseEffects.HORIZONTAL_BOUND,
                origin.getX() + UniverseEffects.CENTER_X + UniverseEffects.HORIZONTAL_BOUND,
                origin.getY() + UniverseEffects.CENTER_Y + UniverseEffects.VERTICAL_BOUND,
                origin.getZ() + UniverseEffects.CENTER_Z + UniverseEffects.HORIZONTAL_BOUND);
    }
}
