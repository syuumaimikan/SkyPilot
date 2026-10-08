package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ExperimentMemory;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.platform.mixin.ContainerScreenAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Reads only visible server stacks; all state dies with the container window. */
final class ExperimentOverlay {
    private final AbstractContainerScreen<?> screen;
    private final ExperimentMemory memory=new ExperimentMemory();
    private final Map<Integer,ItemStack> cards=new HashMap<>();
    private boolean remembering;
    ExperimentMemory memory(){return memory;}
    ExperimentOverlay(AbstractContainerScreen<?> screen){this.screen=screen;}
    static String clean(String s){return s.replaceAll("§.", "").strip();}
    void render(GuiGraphicsExtractor g){
        String title=clean(screen.getTitle().getString());
        boolean pairs=title.startsWith("Superpairs"), ultra=title.startsWith("Ultrasequencer"), chrono=title.startsWith("Chronomatron");
        if(!pairs&&!ultra&&!chrono)return;
        boolean pairOn=Feat.on(Features.EXP_PAIRS),matches=Feat.on(Features.EXP_MATCHES),names=Feat.on(Features.EXP_NAMES);
        if(pairs&&!pairOn&&!matches&&!names || ultra&&!Feat.on(Features.EXP_NUMBERS) || chrono&&!Feat.on(Features.EXP_SEQUENCE))return;
        var box=(ContainerScreenAccessor)screen; var font=Minecraft.getInstance().font;
        var slots=screen.getMenu().slots;
        int count=(int)slots.stream().filter(s->!(s.container instanceof Inventory)).count();
        if(count!=54)return; // Unknown layouts must not be treated as experiments.
        ItemStack control=slots.get(49).getItem();
        String instruction=clean(control.getHoverName().getString());
        boolean observing=control.is(Items.GLOWSTONE)||instruction.toLowerCase(Locale.ROOT).contains("remember");
        boolean playing=instruction.startsWith("Timer:")||control.is(Items.CLOCK);
        if(!pairs&&observing&&!remembering)memory.startRound();
        remembering=observing;
        int flash=-1;
        for(Slot s:slots){
            if(s.container instanceof Inventory || s.index<9||s.index>=45||s.index%9==0||s.index%9==8)continue;
            ItemStack stack=s.getItem(); String id=BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            String name=clean(stack.getHoverName().getString());
            int x=box.skypilot$leftPos()+s.x,y=box.skypilot$topPos()+s.y;
            boolean pane=id.endsWith("_stained_glass_pane")||id.equals("minecraft:glass_pane")||pairs&&id.equals("minecraft:cyan_stained_glass");
            if(pairs){
                String lower=name.toLowerCase(Locale.ROOT);
                if(!stack.isEmpty()&&!pane&&!lower.contains("click")&&!lower.contains("instant find")&&!lower.contains("powerup")){
                    var lore=stack.get(DataComponents.LORE);
                    String identity=id+"|"+name+"|"+stack.getCount()+"|"+(lore==null?"":lore.lines().stream().map(c->clean(c.getString())).filter(t->!t.toLowerCase(Locale.ROOT).contains("click")).toList());
                    memory.reveal(s.index,identity); cards.put(s.index,stack.copy());
                }
                ItemStack saved=cards.get(s.index);
                if(pairOn&&saved!=null&&pane){g.fill(x,y,x+16,y+16,0xDD10182B);g.item(saved,x,y);}
                if(matches&&memory.pair(s.index)>=0){
                    Slot hovered=box.skypilot$hoveredSlot();
                    boolean selected=hovered!=null&&(hovered.index==s.index||memory.pair(hovered.index)==s.index);
                    g.outline(x,y,16,16,selected?0xFFFFFF55:0xFF55DD99);
                }
            }else if(ultra){
                if(observing&&!pane&&!stack.isEmpty()&&name.matches("\\d{1,2}"))memory.number(s.index,Integer.parseInt(name));
                Integer n=memory.number(s.index);
                if(playing&&n!=null){g.fill(x,y,x+16,y+16,0xDD10182B);g.text(font,""+n,x+1,y+4,0xFFFFFF55,true);}
            }else{
                if(observing&&id.endsWith("_terracotta")&&stack.hasFoil())flash=s.index;
                if(playing){
                    var seq=memory.sequence(); List<String> orders=new ArrayList<>();
                    for(int i=0;i<seq.size();i++)if(seq.get(i)==s.index)orders.add(""+(i+1));
                    if(!orders.isEmpty()){g.outline(x,y,16,16,0xFFAA88FF);g.text(font,orders.getFirst()+(orders.size()>1?"+":""),x,y,0xFFFFFF55,true);
                        if(box.skypilot$hoveredSlot()==s)g.text(font,font.plainSubstrByWidth(String.join(" → ",orders),box.skypilot$imageWidth()),box.skypilot$leftPos(),Math.max(0,box.skypilot$topPos()-12),0xFFFFFF55,true);}
                }
            }
        }
        if(chrono&&observing)memory.flash(flash);
        if(pairs&&names){Slot hovered=box.skypilot$hoveredSlot();ItemStack saved=hovered==null?null:cards.get(hovered.index);if(saved!=null){String text=clean(saved.getHoverName().getString());g.text(font,font.plainSubstrByWidth(text,box.skypilot$imageWidth()),box.skypilot$leftPos(),Math.max(0,box.skypilot$topPos()-12),0xFFFFFF55,true);}}
    }
}
