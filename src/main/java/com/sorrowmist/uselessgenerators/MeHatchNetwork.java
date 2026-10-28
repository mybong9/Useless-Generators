package com.sorrowmist.uselessgenerators;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;
import java.util.Optional;

/** GUI-only snapshots and validated menu actions; inventories are never broadcast to a chunk. */
public final class MeHatchNetwork {
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(UselessGenerators.ID, "me_hatch"), () -> "1", "1"::equals, "1"::equals);
    public record Action(int menu, int action, int slot, long amount) {}
    public record Snapshot(int menu, CompoundTag data) {}
    public record EncodeConstruction(int menu) {}
    public record Mark(int menu,int slot,appeng.api.stacks.AEKey key) {}
    public static void register() {
        CHANNEL.registerMessage(3, Mark.class, (p,b)->{b.writeVarInt(p.menu);b.writeVarInt(p.slot);b.writeNbt(p.key.toTagGeneric());},
                b->{int menu=b.readVarInt(),slot=b.readVarInt();var tag=b.readNbt();
                    return new Mark(menu,slot,tag==null?null:appeng.api.stacks.AEKey.fromTagGeneric(tag));}, (p,supplier)->{
                    var context=supplier.get();context.enqueueWork(()->{
                        var player=context.getSender();
                        if(player!=null && player.containerMenu instanceof MeHatchMenu menu && menu.containerId==p.menu)
                            menu.mark(player,p.slot,p.key);
                    });context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(2, EncodeConstruction.class, (p,b)->b.writeVarInt(p.menu),
                b->new EncodeConstruction(b.readVarInt()), (p,supplier)->{
                    var context=supplier.get(); context.enqueueWork(()->encodeConstruction(context.getSender(),p.menu));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(0, Action.class, (p, b) -> { b.writeVarInt(p.menu); b.writeVarInt(p.action); b.writeVarInt(p.slot); b.writeLong(p.amount); },
                b -> new Action(b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readLong()), (p, supplier) -> {
                    var context = supplier.get(); context.enqueueWork(() -> {
                        var player = context.getSender();
                        if (player != null && player.containerMenu instanceof MeHatchMenu menu && menu.containerId == p.menu && menu.stillValid(player))
                            menu.action(player, p.action, p.slot, p.amount);
                    }); context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(1, Snapshot.class, (p, b) -> { b.writeVarInt(p.menu); b.writeNbt(p.data); },
                b -> new Snapshot(b.readVarInt(), b.readNbt()), (p, supplier) -> {
                    var context = supplier.get(); context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                            net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.sorrowmist.uselessgenerators.client.MeHatchScreen.receive(p.menu, p.data)));
                    context.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public static void encodeConstruction(int menu) { CHANNEL.sendToServer(new EncodeConstruction(menu)); }
    public static void mark(int menu,int slot,appeng.api.stacks.AEKey key) {if(key!=null)CHANNEL.sendToServer(new Mark(menu,slot,key));}
    private static void encodeConstruction(net.minecraft.server.level.ServerPlayer player,int id) {
        if(player==null || !(player.containerMenu instanceof appeng.menu.me.items.PatternEncodingTermMenu menu)
                || menu.containerId!=id || !menu.stillValid(player)
                || !(menu instanceof com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PatternEncodingTermMenuAccess access)) return;
        var logic=access.uselessMod$getEncodingLogic();
        var output=logic.getEncodedPatternInv(); var blank=logic.getBlankPatternInv();
        if(!output.getStackInSlot(0).isEmpty()) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.useless_mod.omniversal_pattern.take_encoded_first"),true);return;
        }
        if(!appeng.core.definitions.AEItems.BLANK_PATTERN.isSameAs(blank.getStackInSlot(0))) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("gui.useless_mod.omniversal_pattern.insert_blank"),true);return;
        }
        if(logic instanceof com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.PendingOmniversalPatternHolder holder) {
            holder.uselessMod$setPendingOmniversalRecipe(null);holder.uselessMod$setPendingOmniversalSourceId(null);
        }
        blank.extractItem(0,1,false);
        output.setItemDirect(0,new net.minecraft.world.item.ItemStack(UselessGenerators.COSMIC_CONSTRUCTION_PATTERN.get()));
        logic.saveChanges();menu.broadcastChanges();
    }
    public static void action(int menu, int action, int slot, long amount) { CHANNEL.sendToServer(new Action(menu, action, slot, amount)); }
    static void sync(net.minecraft.server.level.ServerPlayer player, int menu, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Snapshot(menu, data));
    }
    private MeHatchNetwork() {}
}
