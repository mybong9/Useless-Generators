package com.sorrowmist.uselessgenerators;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import appeng.crafting.pattern.EncodedPatternItem;
import com.sorrowmist.uselesscore.api.ae.CoreConstructionPattern;
import com.sorrowmist.uselesscore.api.crafting.CoreMaterialAmounts;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;
import java.util.*;
/** A canonical bill: never trusts material counts written by a client into item NBT. */
public final class CosmicConstructionPatternItem extends EncodedPatternItem {
    public CosmicConstructionPatternItem() { super(new Properties().stacksTo(1)); }
    @Override public IPatternDetails decode(ItemStack stack,Level level,boolean recovery) { return decode(AEItemKey.of(stack),level); }
    @Override public IPatternDetails decode(AEItemKey key,Level level) {
        return key!=null && key.toStack().getItem() instanceof CosmicConstructionPatternItem ? new Details(key) : null;
    }
    @Override public void appendHoverText(ItemStack stack,Level level,List<Component> tooltip,TooltipFlag flag) {
        tooltip.add(Component.translatable("item.useless_generators.cosmic_construction_pattern.help"));
        for(var material:CosmicMaterials.all())tooltip.add(Component.literal(material.count()+" × ").append(material.stack().getHoverName()));
    }
    @Override public net.minecraft.world.InteractionResultHolder<ItemStack> use(Level level,net.minecraft.world.entity.player.Player player,net.minecraft.world.InteractionHand hand) {
        if(level.isClientSide)com.sorrowmist.uselessgenerators.client.CosmicConstructionPatternScreen.open();
        return net.minecraft.world.InteractionResultHolder.sidedSuccess(player.getItemInHand(hand),level.isClientSide);
    }
    private static final class Details implements CoreConstructionPattern {
        private final AEItemKey definition;
        private final Map<AEKey,Long> bill=new LinkedHashMap<>();
        private final IInput[] inputs;
        Details(AEItemKey definition) {
            this.definition=definition;
            for(var material:CosmicMaterials.all())bill.merge(AEItemKey.of(material.stack()),(long)material.count(),Math::addExact);
            inputs=bill.entrySet().stream().map(e->new Input(e.getKey(),e.getValue())).toArray(IInput[]::new);
        }
        @Override public AEItemKey getDefinition(){return definition;}
        @Override public IInput[] getInputs(){return inputs.clone();}
        @Override public GenericStack[] getOutputs(){return new GenericStack[]{new GenericStack(AEItemKey.of(new ItemStack(UselessGenerators.COSMIC_CONSTRUCTION_RECEIPT.get())),1)};}
        @Override public GenericStack[] materials(long copies){return bill.entrySet().stream().map(e->new GenericStack(e.getKey(),Math.multiplyExact(e.getValue(),copies))).toArray(GenericStack[]::new);}
        @Override public boolean matches(KeyCounter[] supplied,long copies){
            if(supplied==null)return false;
            var counts=new LinkedHashMap<AEKey,Long>();
            try {
                for(var holder:supplied)if(holder!=null)for(var entry:holder){
                    if(entry.getLongValue()<0)return false;
                    if(entry.getLongValue()>0)counts.merge(entry.getKey(),entry.getLongValue(),Math::addExact);
                }
                return CoreMaterialAmounts.matches(bill,counts,copies);
            }catch(ArithmeticException overflow){return false;}
        }
        @Override public boolean supportsPushInputsToExternalInventory(){return false;}
        @Override public int hashCode(){return definition.hashCode();}
        @Override public boolean equals(Object other){return other instanceof Details d && definition.equals(d.definition);}
    }
    private record Input(AEKey key,long amount) implements IPatternDetails.IInput {
        @Override public GenericStack[] getPossibleInputs(){return new GenericStack[]{new GenericStack(key,1)};}
        @Override public long getMultiplier(){return amount;}
        @Override public boolean isValid(AEKey input,Level level){return key.equals(input);}
        @Override public AEKey getRemainingKey(AEKey input){return null;}
    }
}
