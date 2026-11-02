package com.sorrowmist.uselessgenerators;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.List;

/** Long-count ME insertion: no item entities or int-sized inventory stacks. */
@Mod.EventBusSubscriber(modid=UselessGenerators.ID)
public final class CosmicOreProduction {
    public static final long PER_TICK=2_100_000_000L;
    private static volatile List<AEItemKey> catalog;
    boolean enabled;
    private int prepaidTicks;
    public long lastGenerated;
    @SubscribeEvent public static void tags(TagsUpdatedEvent event){catalog=null;}
    private static List<AEItemKey> catalog(){
        if(catalog==null)catalog=ForgeRegistries.ITEMS.getValues().stream().filter(item->{
            ItemStack stack=new ItemStack(item);
            return stack.getTags().anyMatch(tag->{var id=tag.location();String p=id.getPath();
                return (id.getNamespace().equals("c")||id.getNamespace().equals("forge"))
                    && (p.equals("ores")||p.startsWith("ores/")||p.equals("raw_materials")||p.startsWith("raw_materials/"));});
        }).map(item->AEItemKey.of(new ItemStack(item))).filter(java.util.Objects::nonNull).toList();
        return catalog;
    }
    void tick(GeneratorBlockEntity owner,boolean operational){
        lastGenerated=0;
        if(!enabled||!operational||!owner.workEnabled||owner.reactor==null)return;
        var hatch=owner.reactor.networkEndpoint(true);
        if(hatch==null||hatch.getActionableNode()==null)return;
        var fuel=owner.fuelTank.getFluid();
        if(prepaidTicks==0 && (fuel.isEmpty()||GeneratorFuels.kind(fuel.getFluid())!=FuelKind.USEFUL))return;
        var storage=hatch.getActionableNode().getGrid().getStorageService().getInventory();
        var source=IActionSource.ofMachine(hatch);
        boolean paid=false;
        for(var key:catalog()){
            if(storage.insert(key,PER_TICK,Actionable.SIMULATE,source)<=0)continue;
            if(!paid){
                if(prepaidTicks==0){owner.fuelTank.drain(1,net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);prepaidTicks=200;}
                paid=true;
            }
            lastGenerated+=Math.max(0,storage.insert(key,PER_TICK,Actionable.MODULATE,source));
        }
        if(lastGenerated>0){prepaidTicks--;owner.setChanged();}
    }
    void save(CompoundTag tag){tag.putBoolean("OreMode",enabled);tag.putInt("OrePrepaidTicks",prepaidTicks);}
    void load(CompoundTag tag){enabled=tag.getBoolean("OreMode");prepaidTicks=Math.max(0,Math.min(200,tag.getInt("OrePrepaidTicks")));}
}
