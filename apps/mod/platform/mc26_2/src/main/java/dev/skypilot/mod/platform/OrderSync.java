package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.BazaarOrderRules;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** A page observation, never a complete history. Disappearing orders are not assumed sold. */
final class OrderSync {
    private static long attempted;
    static void observe(AbstractContainerScreen<?> screen){
        var sp=SkyPilot.get();
        if(sp==null||!sp.linked()||sp.api()==null||!screen.getTitle().getString().equals("Your Bazaar Orders"))return;
        long now=System.currentTimeMillis();if(now-attempted<15_000)return;
        var rows=new ArrayList<Map<String,Object>>();
        for(var slot:screen.getMenu().slots){
            if(slot.container instanceof Inventory)continue;
            var o=BazaarOrders.order(slot.getItem());if(o==null)continue;
            rows.add(Map.of("name",o.product(),"side",o.buy()?"BUY":"SELL","price",o.price(),"status",o.filled()?"FILLED":"ACTIVE"));
        }
        // Wait for menu contents; an empty/loading page must not erase the last observation.
        if(rows.isEmpty())return;
        attempted=now;sp.api().uploadOrders("bazaar",rows).exceptionally(e->null);
    }
}
