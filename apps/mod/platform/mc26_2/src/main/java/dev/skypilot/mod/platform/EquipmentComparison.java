package dev.skypilot.mod.platform;

import dev.skypilot.mod.core.ItemStatComparison;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/** Explicit lore deltas; no predicted damage, price, or missing stats. */
final class EquipmentComparison {
    private static List<String> lore(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        return lore == null ? List.of() : lore.lines().stream().map(Component::getString).toList();
    }
    static void tooltip(ItemStack stack, List<Component> lines) {
        var sp=SkyPilot.get(); var mc=Minecraft.getInstance();
        if(sp==null || mc.player==null || !sp.config().utility.itemComparison || stack.isEmpty()) return;
        var equippable=stack.get(DataComponents.EQUIPPABLE);
        EquipmentSlot slot=equippable == null ? EquipmentSlot.MAINHAND : equippable.slot();
        var equipped=mc.player.getItemBySlot(slot);
        if(equipped.isEmpty() || ItemStack.isSameItemSameComponents(stack,equipped)) return;
        // Hand items must both explicitly advertise damage; don't compare an accessory to a weapon.
        if(equippable==null && (!ItemStatComparison.parse(lore(stack)).containsKey("Damage") || !ItemStatComparison.parse(lore(equipped)).containsKey("Damage"))) return;
        var deltas=ItemStatComparison.compare(lore(stack),lore(equipped));
        if(deltas.isEmpty()) return;
        lines.add(Component.literal(sp.lang().t("SkyPilot · vs equipped (lore only)","SkyPilot · 装備との差（説明欄の値）")).withStyle(ChatFormatting.GRAY));
        for(var d:deltas) lines.add(Component.literal(String.format(java.util.Locale.ROOT,"  %s: %+.1f%s",d.name(),d.amount(),d.percent()?" pp":""))
                .withStyle(d.amount()>0?ChatFormatting.GREEN:ChatFormatting.RED));
    }
}
