package com.sorrowmist.uselessgenerators;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import com.sorrowmist.uselesscore.api.component.CoreItemComponents;
import com.sorrowmist.uselesscore.api.component.UselessComponents;
import com.sorrowmist.uselesscore.api.enums.tool.ConstructionWandCoreMode;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** User-triggered, bounded jobs. Materials are withdrawn for one placement at a time. */
@Mod.EventBusSubscriber(modid = UselessGenerators.ID)
public final class CosmicConstruction {
    private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();
    private record Target(BlockPos pos, BlockState before, BlockState after) {}
    private static final class Job {
        final ServerPlayer player; final ServerLevel level; final GeneratorBlockEntity core;
        final ReactorStructure structure; final BlockState coreState;
        final ItemStack staff; final boolean remove; final IGrid grid; final IActionSource source;
        final List<Target> targets = new ArrayList<>();
        final Map<Item, Integer> needs = new LinkedHashMap<>();
        final Map<Item, List<AEItemKey>> variants = new HashMap<>();
        int scan, cursor, completed; boolean ready;
        Job(ServerPlayer player, GeneratorBlockEntity core, ItemStack staff, boolean remove) {
            this.player = player; this.level = player.serverLevel(); this.core = core;
            this.structure = core.reactor; this.coreState = core.getBlockState();
            this.staff = staff.copy(); this.remove = remove; grid = linkedGrid(player, staff, core); source = IActionSource.ofPlayer(player);
        }
    }
    public static boolean interact(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return false;
        ItemStack staff = event.getItemStack();
        // Covers base staffs, tier variants and addon subclasses such as WondrousStaffItem.
        if (!(staff.getItem() instanceof com.sorrowmist.useless.content.items.EndlessBeafItem)) return false;
        boolean enabled = CoreItemComponents.getOrDefault(staff, UselessComponents.ConstructionWandEnabledComponent.get(), false);
        if (!event.getEntity().isShiftKeyDown() && !enabled) return false;
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof GeneratorBlockEntity core) || core.reactor == null) return false;
        event.setCanceled(true); event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (event.getEntity() instanceof ServerPlayer player) {
            boolean remove = CoreItemComponents.getOrDefault(staff, UselessComponents.ConstructionWandCoreComponent.get(), ConstructionWandCoreMode.DEFAULT) == ConstructionWandCoreMode.DESTRUCTION;
            start(player, core, staff, remove);
        }
        return true;
    }
    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST) public static void fallbackInteraction(PlayerInteractEvent.RightClickBlock event) { interact(event); }
    private static IGrid linkedGrid(ServerPlayer player, ItemStack staff, GeneratorBlockEntity core) {
        var linked = com.sorrowmist.uselesscore.api.ae.CoreLinkedGrid.resolve(staff, player);
        if (linked != null) return linked.grid();
        var node = core.getActionableNode();
        return node == null ? null : node.getGrid();
    }
    private static boolean allowed(Job job, BlockPos pos) {
        return job.player.mayBuild() && job.level.mayInteract(job.player, pos)
                && job.player.mayUseItemAt(pos, Direction.UP, job.staff)
                && job.level.getWorldBorder().isWithinBounds(pos) && !job.level.isOutsideBuildHeight(pos);
    }
    private static void start(ServerPlayer player, GeneratorBlockEntity core, ItemStack staff, boolean remove) {
        if (JOBS.containsKey(player.getUUID()) || JOBS.size() >= 8 || JOBS.values().stream().anyMatch(j -> j.core == core)) { tell(player,"busy"); return; }
        var origin = core.reactor.origin();
        if (JOBS.values().stream().anyMatch(j -> j.level == player.serverLevel() && ReactorLayout.overlaps(origin.getX(),origin.getY(),origin.getZ(),
                j.core.reactor.origin().getX(),j.core.reactor.origin().getY(),j.core.reactor.origin().getZ()))) { tell(player,"busy"); return; }
        if (core.reactor.overlapsOther(player.serverLevel(),false)) { tell(player,"overlap"); return; }
        if (origin.getY() < player.level().getMinBuildHeight() || origin.getY() + ReactorLayout.HEIGHT > player.level().getMaxBuildHeight()) { tell(player,"height"); return; }
        Job job = new Job(player, core, staff, remove);
        if (!allowed(job, core.getBlockPos())) { tell(player,"protected"); return; }
        core.constructionActive = true; core.reactor.recheck();
        JOBS.put(player.getUUID(), job); tell(player, remove ? "scan_remove" : "scan_build");
    }
    private static boolean scan(Job job, int budget) {
        var facing = job.coreState.getValue(GeneratorBlock.FACING);
        while (job.scan < ReactorLayout.CELLS.size() && budget-- > 0) {
            var cell = ReactorLayout.CELLS.get(job.scan++);
            BlockPos pos = job.structure.position(cell);
            if (!job.level.hasChunkAt(pos)) return stop(job,"unloaded",pos.toShortString());
            if (!allowed(job,pos)) return stop(job,"protected",pos.toShortString());
            BlockState state = job.level.getBlockState(pos);
            if (cell.part() == ReactorLayout.Part.CONTROLLER) continue;
            if (job.remove) {
                if (cell.part() != ReactorLayout.Part.AIR && ReactorStructure.matches(state,cell.part()))
                    job.targets.add(new Target(pos,state,Blocks.AIR.defaultBlockState()));
            } else if (!ReactorStructure.matches(state, cell.part())) {
                // Empty ground only: never erase decorations, fluids, machines or terrain.
                if (!state.isAir() || cell.part() == ReactorLayout.Part.AIR) return stop(job,"blocked",pos.toShortString());
                var expected = CosmicMaterials.state(cell, facing);
                job.targets.add(new Target(pos,state,expected));
                job.needs.merge(expected.getBlock().asItem(),1,Integer::sum);
            }
        }
        if (job.scan < ReactorLayout.CELLS.size()) return true;
        if (job.remove) {
            job.targets.sort(Comparator.comparingInt(t -> t.before().getBlock() instanceof MeHatchBlock ? 1 : 0));
            job.targets.add(new Target(job.core.getBlockPos(),job.core.getBlockState(),Blocks.AIR.defaultBlockState()));
        } else if (!materials(job)) return false;
        job.ready = true; tell(job.player,job.remove ? "remove_started" : "build_started",job.targets.size()); return true;
    }
    private static int inventoryCount(Job job, Item item) {
        int count = 0;
        for (var stack : job.player.getInventory().items) if (stack.is(item)) count += stack.getCount();
        return count;
    }
    private static boolean online(Job job) { return job.grid != null && !job.grid.isEmpty() && job.grid.getEnergyService().isNetworkPowered(); }
    private static boolean materials(Job job) {
        if (job.player.getAbilities().instabuild) return true;
        for (var item : job.needs.keySet()) job.variants.put(item,new ArrayList<>());
        // Once per explicit job, include saved hatch/controller variants so dismantled parts can be rebuilt.
        if (online(job)) for (var entry : job.grid.getStorageService().getInventory().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey key && job.variants.containsKey(key.getItem()) && entry.getLongValue() > 0)
                job.variants.get(key.getItem()).add(key);
        }
        boolean enough = true;
        for (var entry : job.needs.entrySet()) {
            long have = inventoryCount(job, entry.getKey());
            if (online(job)) for (var key : job.variants.get(entry.getKey())) {
                have += Math.min(entry.getValue(),job.grid.getStorageService().getInventory().extract(key,entry.getValue(),Actionable.SIMULATE,job.source));
                if (have >= entry.getValue()) break;
            }
            if (have < entry.getValue()) {
                if (enough) tell(job.player,"missing");
                enough = false; tell(job.player,"missing_line",entry.getKey().getDescription(),entry.getValue(),have,entry.getValue()-have);
            }
        }
        return enough;
    }
    private static ItemStack take(Job job, Item item) {
        if (job.player.getAbilities().instabuild) return new ItemStack(item);
        for (var stack : job.player.getInventory().items) if (stack.is(item)) { var result = stack.split(1); job.player.getInventory().setChanged(); return result; }
        if (online(job)) for (var key : job.variants.getOrDefault(item,List.of()))
            if (job.grid.getStorageService().getInventory().extract(key,1,Actionable.MODULATE,job.source) == 1) return key.toStack(1);
        return ItemStack.EMPTY;
    }
    private static int roomInInventory(Job job, ItemStack stack) {
        int count = 0;
        for (var slot : job.player.getInventory().items) {
            if (slot.isEmpty()) count += stack.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(slot,stack)) count += Math.max(0,slot.getMaxStackSize()-slot.getCount());
        }
        return count;
    }
    private static void give(Job job, ItemStack stack) {
        if (stack.isEmpty()) return;
        try {
            if (online(job)) {
                long accepted = job.grid.getStorageService().getInventory().insert(AEItemKey.of(stack),stack.getCount(),Actionable.MODULATE,job.source);
                stack.shrink((int)Math.min(stack.getCount(),Math.max(0,accepted)));
            }
        } catch (RuntimeException disconnected) { /* The exact remaining stack goes back to the player. */ }
        if (!stack.isEmpty()) job.player.getInventory().placeItemBackInInventory(stack,false);
    }
    private static boolean work(Job job, int budget) {
        while (job.cursor < job.targets.size() && budget-- > 0) {
            var target = job.targets.get(job.cursor);
            if (!job.level.hasChunkAt(target.pos())) return stop(job,"unloaded",target.pos().toShortString());
            if (!allowed(job,target.pos())) return stop(job,"protected",target.pos().toShortString());
            if (!job.level.getBlockState(target.pos()).equals(target.before())) return stop(job,"changed",target.pos().toShortString());
            if (job.remove) {
                if (ForgeHooks.onBlockBreakEvent(job.level,job.player.gameMode.getGameModeForPlayer(),job.player,target.pos()) < 0)
                    return stop(job,"protected",target.pos().toShortString());
                if (!job.level.getBlockState(target.pos()).equals(target.before())) return stop(job,"changed",target.pos().toShortString());
                var drops = Block.getDrops(target.before(),job.level,target.pos(),job.level.getBlockEntity(target.pos()),job.player,job.staff);
                // These template blocks drop exactly one item, carrying any local buffers.
                for (var drop : drops) {
                    long networkRoom = online(job) ? job.grid.getStorageService().getInventory().insert(AEItemKey.of(drop),drop.getCount(),Actionable.SIMULATE,job.source) : 0;
                    if (networkRoom + roomInInventory(job,drop) < drop.getCount()) return stop(job,"full");
                }
                if (!job.level.setBlock(target.pos(),target.after(),3)) return stop(job,"changed",target.pos().toShortString());
                for (var drop : drops) give(job,drop);
            } else {
                ItemStack material = take(job,target.after().getBlock().asItem());
                if (material.isEmpty()) return stop(job,"short_during",target.after().getBlock().getName(),job.completed);
                var snapshot = BlockSnapshot.create(job.level.dimension(),job.level,target.pos());
                boolean committed = false;
                try {
                    if (!job.level.setBlock(target.pos(),target.after(),3)) return stop(job,"changed",target.pos().toShortString());
                    if (ForgeEventFactory.onBlockPlace(job.player,snapshot,Direction.UP)) {
                        return stop(job,"protected",target.pos().toShortString());
                    }
                    target.after().getBlock().setPlacedBy(job.level,target.pos(),target.after(),job.player,material);
                    committed = true;
                } finally {
                    if (!committed) {
                        snapshot.restore(true,false);
                        if (!job.player.getAbilities().instabuild) give(job,material);
                    }
                }
            }
            job.cursor++; job.completed++;
        }
        if (job.cursor == job.targets.size()) { tell(job.player,job.remove ? "removed" : "built",job.completed); return false; }
        return true;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var iterator = JOBS.values().iterator();
        int editBudget = Math.max(1,64/Math.max(1,JOBS.size())), scanBudget = Math.max(1,512/Math.max(1,JOBS.size()));
        // At most eight jobs: 512 validation cells or 64 edits globally per tick.
        while (iterator.hasNext()) {
            var job = iterator.next(); boolean keep = false;
            try {
                if (!job.player.isRemoved() && job.player.isAlive() && job.player.serverLevel() == job.level
                        && !job.core.isRemoved() && job.level.hasChunkAt(job.core.getBlockPos())
                        && job.core.reactor == job.structure && job.core.getBlockState().equals(job.coreState)) keep = job.ready ? work(job,editBudget) : scan(job,scanBudget);
                else tell(job.player,"stopped",job.completed);
            } catch (RuntimeException error) {
                com.mojang.logging.LogUtils.getLogger().warn("Cosmic Heart construction stopped at {}",job.core.getBlockPos(),error);
                tell(job.player,"stopped",job.completed);
            }
            if (!keep) { finish(job); iterator.remove(); }
        }
    }
    private static void finish(Job job) { job.core.constructionActive = false; if (!job.core.isRemoved()) job.core.reactor.recheck(); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { JOBS.values().forEach(CosmicConstruction::finish); JOBS.clear(); }
    private static boolean stop(Job job, String key, Object... args) { tell(job.player,key,args); return false; }
    private static void tell(ServerPlayer player,String key,Object... args) { player.displayClientMessage(Component.translatable("gui.useless_generators.build."+key,args),false); }
    private CosmicConstruction() {}
}
