package dev.skypilot.wardrobe;

import dev.skypilot.mod.core.CosmeticAnimation;
import dev.skypilot.mod.core.CosmeticCatalog;
import dev.skypilot.mod.core.SkyPilotConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Interactive Pet &amp; Companion Play &amp; Bonding Studio screen ({@code Shift+H}, {@code /wardrobe play},
 * or Wardrobe Studio's {@code ♥ Play} button). Displays live 3D previews and persisted Bond Levels
 * (Lv.1–10) for both your Floating Pet and Ground Companion alongside 6 interactive play actions.
 */
final class PetInteractScreen extends Screen {
    private final Screen parent;

    PetInteractScreen(Screen parent) {
        super(Component.literal("Pet & Companion Play"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();

        int panelW = Math.min(350, width - 28);
        int px = (width - panelW) / 2;
        int btnW = (panelW - 20) / 2;
        int bxL = px + 8;
        int bxR = px + 12 + btnW;

        boolean hasAny = !"none".equals(a.pet) || !"none".equals(a.companion);
        int y = Math.max(122, height / 2 - 4);

        // Row 1: Pat | Give Treat
        var patBtn = WardrobeUi.button(l.t("♥ Pat / Pet (+2♥)", "♥ なでる (+2♥)"), b -> triggerAndClose(PetWorldPhysics.Action.PAT))
                .bounds(bxL, y, btnW, 20)
                .tooltip(l.t("Pet your companions right in front of you with hearts & cute sounds!", "目の前でよしよし撫でてハート＆鳴き声！素手でペットを右クリック・Hキーでも可"))
                .build();
        patBtn.active = hasAny;
        addRenderableWidget(patBtn);

        var treatBtn = WardrobeUi.button(l.t("🍪 Give Treat (+4♥)", "🍪 おやつをあげる (+4♥)"), b -> triggerAndClose(PetWorldPhysics.Action.TREAT))
                .bounds(bxR, y, btnW, 20)
                .tooltip(l.t("Hold out a glowing golden treat for them to excitedly catch & eat!", "光るおやつを差し出してモグモグ食べてもらう"))
                .build();
        treatBtn.active = hasAny;
        addRenderableWidget(treatBtn);

        // Row 2: Throw Fetch Ball | Perform Trick
        y += 24;
        var fetchBtn = WardrobeUi.button(l.t("🎾 Throw Fetch Ball (+5♥)", "🎾 ボール投げ・もってこい (+5♥)"), b -> triggerAndClose(PetWorldPhysics.Action.FETCH))
                .bounds(bxL, y, btnW, 20)
                .tooltip(l.t("Toss a bouncing glowing toy — your pet & companion race to bring it back!", "光るボールを投げて、ペット＆相棒に取ってきてもらう"))
                .build();
        fetchBtn.active = hasAny;
        addRenderableWidget(fetchBtn);

        var trickBtn = WardrobeUi.button(l.t("✨ Do a Trick! (+3♥)", "✨ とくぎ・芸を見せる (+3♥)"), b -> triggerAndClose(PetWorldPhysics.Action.TRICK))
                .bounds(bxR, y, btnW, 20)
                .tooltip(l.t("Acrobatic barrel-roll & celebratory star show!", "クルッと宙返り＆音符と星のスペシャル芸を披露"))
                .build();
        trickBtn.active = hasAny;
        addRenderableWidget(trickBtn);

        // Row 3: Sit/Stay Toggle | Head/Shoulder Perch Toggle
        y += 24;
        boolean staying = PetWorldPhysics.isStaying();
        var stayBtn = WardrobeUi.button(
                staying ? l.t("🐾 Follow Me!", "🐾 ついてきて！") : l.t("🪑 Sit / Wait Here", "🪑 おすわり・待機"),
                b -> triggerAndClose(PetWorldPhysics.Action.TOGGLE_STAY))
                .bounds(bxL, y, btnW, 20)
                .tooltip(l.t("Ask your pet & companion to sit calmly or resume following you", "その場でおすわり待機／また一緒にお散歩する"))
                .build();
        stayBtn.active = hasAny;
        addRenderableWidget(stayBtn);

        boolean perched = PetWorldPhysics.isPerchCommanded();
        var perchBtn = WardrobeUi.button(
                perched ? l.t("🐾 Hop Down", "🐾 頭・肩から降りる") : l.t("🤗 Hop on Head / Shoulder", "🤗 頭・肩の上に乗せる"),
                b -> triggerAndClose(PetWorldPhysics.Action.TOGGLE_PERCH))
                .bounds(bxR, y, btnW, 20)
                .tooltip(l.t("Invite your pet & companion to snuggle on your head & shoulder!", "頭や肩の上にちょこんと乗って一緒にお出かけ"))
                .build();
        perchBtn.active = hasAny;
        addRenderableWidget(perchBtn);

        // Bottom row: Choose Pet in Wardrobe | Close
        y += 28;
        addRenderableWidget(WardrobeUi.button(l.t("✦ Choose Pets (Wardrobe)", "✦ ペット・相棒を選ぶ"), b ->
                minecraft.gui.setScreen(new AppearanceScreen(parent)))
                .bounds(bxL, y, btnW, 18).build());
        addRenderableWidget(WardrobeUi.button(l.t("Close", "閉じる"), b -> onClose())
                .bounds(bxR, y, btnW, 18).build());
    }

    private void triggerAndClose(PetWorldPhysics.Action action) {
        minecraft.gui.setScreen(null);
        PetWorldPhysics.performAction(action, null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
        WardrobeUi.backdrop(g, width, height);
        var rt = WardrobeRuntime.get();
        var l = rt.lang();
        boolean ja = rt.japanese();
        var a = rt.appearance();
        double time = CosmeticAnimation.animationTimeSeconds();

        int panelW = Math.min(350, width - 28);
        int px = (width - panelW) / 2;
        int topY = Math.max(18, height / 2 - 112);
        int panelH = Math.min(height - 32, 232);
        WardrobeUi.panel(g, px, topY, panelW, panelH);
        g.fill(px + 1, topY + 1, px + panelW - 1, topY + 3, 0xFFF472B6);

        g.centeredText(font, l.t("♥ PET & COMPANION PLAYTIME", "♥ ペット＆相棒 触れ合いメニュー"),
                width / 2, topY + 9, WardrobeUi.WHITE);
        g.centeredText(font, PetWorldPhysics.currentMoodLabel(ja), width / 2, topY + 20, WardrobeUi.CYAN);

        // Two side-by-side live 3D Bond Cards (Floating Pet & Ground Companion)
        int cardW = (panelW - 22) / 2;
        int cardY = topY + 34;
        int cardH = Math.max(58, Math.max(122, height / 2 - 4) - cardY - 8);
        drawPetBondCard(g, CosmeticCatalog.byId(a.pet), a, px + 8, cardY, cardW, cardH,
                l.t("Floating Pet", "浮遊ペット"), ja, time);
        drawPetBondCard(g, CosmeticCatalog.byId(a.companion), a, px + 14 + cardW, cardY, cardW, cardH,
                l.t("Ground Companion", "地上相棒"), ja, time);

        String tip = l.t("Tips: Empty-hand Right-Click on your pet to pat · Tap H to pat · Double-crouch to greet!",
                "ヒント: 素手でペットを右クリック or Hキーで即なでる · Shift2回連打で挨拶ジャンプ！");
        g.centeredText(font, font.plainSubstrByWidth(tip, width - 24), width / 2, topY + panelH + 6, WardrobeUi.GRAY);

        super.extractRenderState(g, mx, my, delta);
    }

    private void drawPetBondCard(
            GuiGraphicsExtractor g,
            CosmeticCatalog.Item item,
            SkyPilotConfig.Appearance a,
            int x, int y, int w, int h,
            String slotTitle,
            boolean ja,
            double time
    ) {
        g.fillGradient(x, y, x + w, y + h, 0xEE141C30, 0xEE0B101E);
        int border = item != null ? (0xFF000000 | (item.color() & 0xFFFFFF)) : WardrobeUi.PANEL_BORDER;
        g.outline(x, y, w, h, border);

        if (item == null) {
            g.text(font, slotTitle, x + 6, y + 6, WardrobeUi.GRAY, false);
            g.centeredText(font, ja ? "未装備" : "Not Equipped", x + w / 2, y + h / 2 - 3, WardrobeUi.DARK_GRAY);
            return;
        }

        // Left 3D preview + Right bond stats
        int prevW = Math.min(54, w / 3 + 6);
        WardrobeModel.renderCard(g, item, a.cosmeticGlow, x + 2, y + 4, x + prevW, y + h - 4, time);

        int tx = x + prevW + 4;
        int tw = Math.max(40, w - prevW - 10);
        g.text(font, slotTitle, tx, y + 6, WardrobeUi.GRAY, false);
        g.text(font, font.plainSubstrByWidth(item.label(ja), tw), tx, y + 17, WardrobeUi.WHITE, false);

        int pts = a.bondOf(item.id());
        int lv = SkyPilotConfig.Appearance.bondLevel(pts);
        String lvText = ja ? "♥ なつき度 Lv." + lv + (lv >= 10 ? " MAX" : "") : "♥ Bond Lv." + lv + (lv >= 10 ? " MAX" : "");
        g.text(font, lvText, tx, y + 30, 0xFFF472B6, false);

        // Bond progress bar
        int prevTh = SkyPilotConfig.Appearance.bondPrevThreshold(lv);
        int nextTh = SkyPilotConfig.Appearance.bondNextThreshold(lv);
        double frac = lv >= 10 ? 1.0 : Math.max(0.0, Math.min(1.0, (pts - prevTh) / (double) Math.max(1, nextTh - prevTh)));
        int barY = y + 42;
        g.fill(tx, barY, tx + tw, barY + 6, 0xCC060A14);
        int fillW = (int) Math.round(tw * frac);
        if (fillW > 0) g.fillGradient(tx, barY, tx + fillW, barY + 6, 0xFFF472B6, 0xFFFBBF24);
        g.outline(tx, barY, tw, 6, 0x66F472B6);
        String ptsStr = lv >= 10 ? ("♥ " + pts) : ("♥ " + pts + " / " + nextTh);
        if (h >= 58) g.text(font, ptsStr, tx, barY + 9, WardrobeUi.GRAY, false);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}

