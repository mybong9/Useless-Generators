package com.sorrowmist.uselessgenerators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** Event-invalidated, bounded structure checks. Never loads a chunk to complete a check. */
public final class ReactorStructure {
    public enum Status { SINGLE, CHECKING, FORMED, INCOMPLETE, UNLOADED, ALIGNMENT, HEIGHT, OVERLAP }
    // Values contain positions only, so neither levels nor unloaded entities are kept alive.
    private static final Map<Level, Map<Long, Set<BlockPos>>> WATCHERS = new WeakHashMap<>();
    private static final Map<Level, Set<BlockPos>> CLIENT_CONTROLLERS = new WeakHashMap<>();
    public static void rememberClient(GeneratorBlockEntity owner) {
        if (owner.reactor != null && owner.getLevel() != null && owner.getLevel().isClientSide)
            CLIENT_CONTROLLERS.computeIfAbsent(owner.getLevel(),ignored -> new HashSet<>()).add(owner.getBlockPos());
    }
    public static Set<BlockPos> clientControllers(Level level) { return CLIENT_CONTROLLERS.getOrDefault(level,Set.of()); }
    private final GeneratorBlockEntity owner;
    private final BlockPos origin;
    private final int rotation;
    private Status status = Status.CHECKING;
    private int cursor;
    private BlockPos problem;
    private ReactorLayout.Part expected;
    private boolean registered;
    private final java.util.List<BlockPos> hatchPositions = new java.util.ArrayList<>();
    private final java.util.List<MeHatchBlockEntity> hatches = new java.util.ArrayList<>();
    private MeHatchBlockEntity connector;
    private final BlockPos[] chunkAnchors;
    private final int chunkWidth, chunkDepth;

    ReactorStructure(GeneratorBlockEntity owner) {
        this.owner = owner;
        rotation = rotation(owner.getBlockState().getValue(GeneratorBlock.FACING));
        var controller = ReactorLayout.rotate(ReactorLayout.CONTROLLER.x(), ReactorLayout.CONTROLLER.z(), rotation);
        origin = owner.getBlockPos().offset(-controller.x(), -ReactorLayout.CONTROLLER.y(), -controller.z());
        chunkWidth = ((origin.getX()+ReactorLayout.WIDTH-1)>>4) - (origin.getX()>>4) + 1;
        chunkDepth = ((origin.getZ()+ReactorLayout.WIDTH-1)>>4) - (origin.getZ()>>4) + 1;
        chunkAnchors = new BlockPos[chunkWidth*chunkDepth];
        for(int x=0;x<chunkWidth;x++) for(int z=0;z<chunkDepth;z++)
            chunkAnchors[x*chunkDepth+z] = new BlockPos(((origin.getX()>>4)+x)<<4,origin.getY(),((origin.getZ()>>4)+z)<<4);
    }
    public static int rotation(Direction facing) {
        return switch (facing) { case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0; };
    }
    public static Block block(ReactorLayout.Part part) {
        return switch (part) {
            case CASING -> UselessGenerators.REACTOR_CASING.get();
            case COIL -> UselessGenerators.REACTOR_COIL.get();
            case CORE -> UselessGenerators.REACTOR_CORE.get();
            case CONTROLLER -> UselessGenerators.COSMIC_HEART.get();
            case HULL -> UselessGenerators.REACTOR_HULL.get();
            case GLASS -> net.minecraft.world.level.block.Blocks.TINTED_GLASS;
            case AIR -> net.minecraft.world.level.block.Blocks.AIR;
            case FLUID_INPUT -> UselessGenerators.HATCHES.get(MeHatchKind.FLUID_INPUT).get();
            case ITEM_INPUT -> UselessGenerators.HATCHES.get(MeHatchKind.ITEM_INPUT).get();
            case OUTPUT -> UselessGenerators.HATCHES.get(MeHatchKind.OUTPUT).get();
            case ENERGY -> UselessGenerators.HATCHES.get(MeHatchKind.ENERGY).get();
            case CONNECTOR -> UselessGenerators.HATCHES.get(MeHatchKind.CONNECTOR).get();
        };
    }
    public Status status() { return status; }
    public int checked() { return cursor; }
    public BlockPos problem() { return problem; }
    public ReactorLayout.Part expected() { return expected; }
    public BlockPos origin() { return origin; }
    public BlockPos position(ReactorLayout.Cell cell) {
        var horizontal = ReactorLayout.rotate(cell.x(), cell.z(), rotation);
        return origin.offset(horizontal.x(), cell.y(), horizontal.z());
    }
    public boolean formed() { return status == Status.FORMED; }
    public boolean matchesFacing(Direction facing) { return rotation == rotation(facing); }
    private void reset(Status next) {
        if (status == next && cursor == 0 && hatches.isEmpty()) return;
        for (var hatch : hatches) if (!hatch.isRemoved()) hatch.bind(null);
        hatches.clear(); hatchPositions.clear(); connector = null;
        status = next; cursor = 0; problem = null; expected = null;
    }
    public MeHatchBlockEntity connector() { return formed() ? connector : null; }
    /** Select an endpoint without merging the independent grids of physically wired hatches. */
    public MeHatchBlockEntity networkEndpoint(boolean active) {
        if (!formed()) return null;
        if (connector != null && connector.directEndpoint(active)) return connector;
        for (var hatch : hatches) if (hatch.directEndpoint(active)) return hatch;
        return null;
    }
    public int hatchCount(MeHatchKind kind) { return (int) hatches.stream().filter(h -> !h.isRemoved() && h.kind == kind).count(); }
    public int onlineHatches() { return (int) hatches.stream().filter(h -> !h.isRemoved() && h.online()).count(); }
    public void feedFuel() { if (formed()) for (var hatch : hatches) if (!hatch.isRemoved()) hatch.feedFuel(owner); }
    public void exportEnergy() { if (formed()) for (var hatch : hatches) if (!hatch.isRemoved() && hatch.kind == MeHatchKind.ENERGY && hatch.sendEnergy(owner.energy) > 0) owner.setChanged(); }
    public void recheck() { reset(Status.CHECKING); }

    public boolean tick() {
        Level level = owner.getLevel();
        if (level == null || level.isClientSide) return false;
        if (origin.getY() < level.getMinBuildHeight() || origin.getY() + ReactorLayout.HEIGHT > level.getMaxBuildHeight()) {
            reset(Status.HEIGHT); return false;
        }
        register(level);
        for (BlockPos anchor : chunkAnchors) {
            if (!level.hasChunkAt(anchor)) { reset(Status.UNLOADED); return false; }
        }
        if (status == Status.UNLOADED) reset(Status.CHECKING);
        if (status == Status.FORMED) return true;
        if (status == Status.INCOMPLETE) return false;
        if (status == Status.OVERLAP) {
            if (level.getGameTime() % 20 != 0 || overlapsOther(level)) return false;
            reset(Status.CHECKING);
        }
        int limit = Math.min(ReactorLayout.CELLS.size(), cursor + ReactorLayout.CHECKS_PER_TICK);
        while (cursor < limit) {
            var cell = ReactorLayout.CELLS.get(cursor);
            BlockPos pos = position(cell);
            var state = level.getBlockState(pos);
            if (!matches(state, cell.part())) {
                status = Status.INCOMPLETE; problem = pos; expected = cell.part(); return false;
            }
            if (state.getBlock() instanceof MeHatchBlock) {
                if (hatchPositions.size() >= ReactorLayout.MAX_HATCHES) { status = Status.INCOMPLETE; problem = pos; expected = ReactorLayout.Part.CASING; return false; }
                hatchPositions.add(pos);
            }
            cursor++;
        }
        if (cursor == ReactorLayout.CELLS.size()) {
            status = overlapsOther(level) ? Status.OVERLAP : Status.FORMED;
            if (formed()) for (BlockPos pos : hatchPositions) {
                if (level.getBlockEntity(pos) instanceof MeHatchBlockEntity hatch) {
                    hatches.add(hatch); hatch.bind(owner.getBlockPos());
                    if (connector == null && hatch.kind == MeHatchKind.CONNECTOR) connector = hatch;
                } else { reset(Status.CHECKING); return false; }
            }
        }
        return formed();
    }
    public boolean overlapsOther(Level level) {
        return overlapsOther(level, true);
    }
    public boolean overlapsOther(Level level, boolean formedOnly) {
        Map<Long, Set<BlockPos>> byChunk = WATCHERS.get(level);
        if (byChunk == null) return false;
        Set<BlockPos> candidates = new HashSet<>();
        for (int x = 0; x < chunkWidth; x++) for (int z = 0; z < chunkDepth; z++)
            candidates.addAll(byChunk.getOrDefault(chunk(x, z), Set.of()));
        for (BlockPos pos : candidates) {
            if (pos.equals(owner.getBlockPos()) || !level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof GeneratorBlockEntity other && other.reactor != null && (!formedOnly || other.reactor.formed())) {
                BlockPos b = other.reactor.origin;
                if (ReactorLayout.overlaps(origin.getX(), origin.getY(), origin.getZ(), b.getX(), b.getY(), b.getZ())) return true;
            }
        }
        return false;
    }
    private long chunk(int x, int z) { return ChunkPos.asLong((origin.getX() >> 4) + x, (origin.getZ() >> 4) + z); }
    private void register(Level level) {
        if (registered) return;
        var byChunk = WATCHERS.computeIfAbsent(level, ignored -> new HashMap<>());
        for (int x = 0; x < chunkWidth; x++) for (int z = 0; z < chunkDepth; z++)
            byChunk.computeIfAbsent(chunk(x, z), ignored -> new HashSet<>()).add(owner.getBlockPos());
        registered = true;
    }
    public void unload() {
        Level level = owner.getLevel();
        var clients = CLIENT_CONTROLLERS.get(level);
        if (clients != null) { clients.remove(owner.getBlockPos()); if (clients.isEmpty()) CLIENT_CONTROLLERS.remove(level); }
        var byChunk = WATCHERS.get(level);
        if (registered && byChunk != null) {
            for (int x = 0; x < chunkWidth; x++) for (int z = 0; z < chunkDepth; z++) {
                long key = chunk(x, z); var positions = byChunk.get(key);
                if (positions != null) { positions.remove(owner.getBlockPos()); if (positions.isEmpty()) byChunk.remove(key); }
            }
            if (byChunk.isEmpty()) WATCHERS.remove(level);
        }
        registered = false; reset(Status.CHECKING);
    }
    public static void changed(Level level, BlockPos changed) {
        if (level.isClientSide) return;
        var byChunk = WATCHERS.get(level);
        if (byChunk == null) return;
        var positions = byChunk.get(ChunkPos.asLong(changed.getX() >> 4, changed.getZ() >> 4));
        if (positions == null) return;
        for (BlockPos pos : Set.copyOf(positions)) {
            if (!level.hasChunkAt(pos)) continue;
            if (level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator && generator.reactor != null) {
                var structure = generator.reactor;
                if (structure.cellAt(changed) != null) structure.recheck();
            }
        }
    }
    private ReactorLayout.Cell cellAt(BlockPos pos) {
        var local = ReactorLayout.rotate(pos.getX() - origin.getX(), pos.getZ() - origin.getZ(), 4 - rotation);
        return ReactorLayout.at(local.x(), pos.getY() - origin.getY(), local.z());
    }
    public static boolean matches(net.minecraft.world.level.block.state.BlockState state, ReactorLayout.Part part) {
        if (state.getBlock() instanceof MeHatchBlock hatch)
            return ReactorLayout.acceptsHatch(part, ReactorLayout.Part.valueOf(hatch.kind.name()));
        return part == ReactorLayout.Part.AIR ? state.isAir() : state.is(block(part));
    }
    public static void blockChanged(net.minecraftforge.event.level.BlockEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        if (event instanceof net.minecraftforge.event.level.BlockEvent.BreakEvent
                || event instanceof net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent) {
            changed(level, event.getPos()); return;
        }
        if (!(event instanceof net.minecraftforge.event.level.BlockEvent.NeighborNotifyEvent)) return;
        var byChunk = WATCHERS.get(level);
        if (byChunk == null) return;
        var positions = byChunk.get(ChunkPos.asLong(event.getPos().getX() >> 4, event.getPos().getZ() >> 4));
        if (positions == null) return;
        for (BlockPos pos : Set.copyOf(positions)) if (level.hasChunkAt(pos)
                && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator && generator.reactor != null) {
            var reactor = generator.reactor; var cell = reactor.cellAt(event.getPos());
            if (cell != null && (!matches(level.getBlockState(event.getPos()), cell.part())
                    || reactor.status == Status.INCOMPLETE && event.getPos().equals(reactor.problem))) reactor.recheck();
        }
    }
    public static void chunkUnloaded(net.minecraftforge.event.level.ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide) return;
        var byChunk = WATCHERS.get(level);
        if (byChunk == null) return;
        var positions = byChunk.get(event.getChunk().getPos().toLong());
        if (positions == null) return;
        for (BlockPos pos : Set.copyOf(positions)) {
            if (level.hasChunkAt(pos) && level.getBlockEntity(pos) instanceof GeneratorBlockEntity generator && generator.reactor != null)
                generator.reactor.reset(Status.UNLOADED);
        }
    }
}
