package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.GameWatcher;
import dev.skypilot.launcher.Instance;
import dev.skypilot.mod.core.Features;
import dev.skypilot.mod.core.SkyPilotConfig;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;

/**
 * The SkyPilot mod's feature switches, edited in the game folder's config (the same file the mod
 * reads when it starts). Features that send chat text need the in-game consent and can only be
 * switched on in game.
 */
final class FeaturesPage {
	private final Ctx c;
	private final JPanel list = new Ui.ScrollBody(null);
	private final JTextField search = new JTextField();
	private final JLabel warn = new JLabel();
	private final JLabel count = Ui.muted("");
	private Features.Category category;
	private SkyPilotConfig cfg;
	private final JComponent root;

	FeaturesPage(Ctx c) {
		this.c = c;
		root = build();
		reload();
	}

	JComponent view() {
		return root;
	}

	void reload() {
		cfg = Instance.config(c.settings.instance());
		boolean running = GameWatcher.check(c.settings.instance(), 0).state() == GameWatcher.State.RUNNING;
		warn.setVisible(running);
		render();
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JButton recommended = Widgets.button(c.t("Recommended on", "おすすめをオン"), "check");
		recommended.addActionListener(e -> {
			int n = Features.applyRecommended(cfg);
			save(c.t(n + " recommended features turned on.", "おすすめ機能を " + n + " 個オンにしました。"));
		});
		JButton off = Widgets.button(c.t("All off", "すべてオフ"), "power");
		off.addActionListener(e -> {
			Features.disableAll(cfg, category);
			save(category == null ? c.t("Every feature is off.", "すべての機能をオフにしました。") : c.t(category.en + " features are off.", category.ja + "の機能をオフにしました。"));
		});
		page.add(Ui.header(c.t("SkyPilot features", "SkyPilotの機能"), c.t("Switch features of the SkyPilot mod on or off. They apply the next time the game starts; new features start off.",
				"SkyPilot MODの機能をオン／オフします。次回のゲーム起動から反映され、新しい機能は最初はオフです。"), recommended, off), BorderLayout.NORTH);

		JPanel side = new JPanel();
		side.setOpaque(false);
		side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
		ButtonGroup g = new ButtonGroup();
		side.add(categoryButton(null, g));
		for (Features.Category cat : Features.Category.values()) side.add(categoryButton(cat, g));
		JPanel sideWrap = new JPanel(new BorderLayout());
		sideWrap.setOpaque(false);
		sideWrap.add(side, BorderLayout.NORTH);
		sideWrap.setPreferredSize(new Dimension(190, 10));

		JPanel top = new JPanel(new BorderLayout(12, 8));
		top.setOpaque(false);
		search.putClientProperty("JTextField.placeholderText", c.t("Search features", "機能を検索"));
		search.putClientProperty("JTextField.leadingIcon", Icons.of("search", 16, Theme.FAINT));
		search.putClientProperty("JTextField.showClearButton", true);
		search.setPreferredSize(new Dimension(260, 34));
		search.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent e) {
				render();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				render();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				render();
			}
		});
		top.add(search, BorderLayout.WEST);
		top.add(count, BorderLayout.EAST);
		warn.setText(c.t("The game is running: changes made here apply after you restart it, and the game may overwrite them - change them in game (J → settings) instead.",
				"ゲームが起動中です。ここでの変更は再起動後に反映され、ゲーム側の保存で上書きされることがあります。ゲーム内（J → 設定）での変更をおすすめします。"));
		warn.setIcon(Icons.of("alert", 16, Theme.WARN));
		warn.setForeground(Theme.WARN);
		top.add(warn, BorderLayout.SOUTH);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		JPanel main = new JPanel(new BorderLayout(0, 12));
		main.setOpaque(false);
		main.add(top, BorderLayout.NORTH);
		main.add(Ui.scroll(list), BorderLayout.CENTER);
		JPanel center = new JPanel(new BorderLayout(18, 0));
		center.setOpaque(false);
		center.add(sideWrap, BorderLayout.WEST);
		center.add(main, BorderLayout.CENTER);
		page.add(center, BorderLayout.CENTER);
		return page;
	}

	private JComponent categoryButton(Features.Category cat, ButtonGroup g) {
		String label = cat == null ? c.t("All", "すべて") : c.t(cat.en, cat.ja);
		Widgets.NavButton b = new Widgets.NavButton(label, cat == Features.Category.PERFORMANCE ? "bolt" : cat == null ? "features" : "chevron");
		b.setFont(Theme.font(Font.PLAIN, 13f));
		b.setSelected(cat == category);
		b.addActionListener(e -> {
			category = cat;
			render();
		});
		g.add(b);
		return b;
	}

	private void render() {
		list.removeAll();
		String q = search.getText();
		List<Features.Feature> shown = Features.all().stream().filter(f -> (category == null || f.category() == category) && Features.matches(f, q)).toList();
		long on = Features.all().stream().filter(f -> cfg.on(f)).count();
		count.setText(c.t(on + " of " + Features.all().size() + " on", Features.all().size() + " 個中 " + on + " 個オン"));
		if (category == Features.Category.PERFORMANCE || category == null && q.isBlank()) list.add(perfCallout());
		Features.Category last = null;
		for (Features.Feature f : shown) {
			if (category == null && f.category() != last) {
				JLabel h = Ui.label(c.t(f.category().en, f.category().ja).toUpperCase(java.util.Locale.ROOT), 11.5f, Font.BOLD, Theme.MUTED);
				h.setBorder(BorderFactory.createEmptyBorder(last == null ? 4 : 16, 4, 6, 0));
				h.setAlignmentX(0);
				list.add(h);
				last = f.category();
			}
			list.add(row(f));
			list.add(Box.createVerticalStrut(6));
		}
		if (shown.isEmpty()) list.add(Ui.muted(c.t("Nothing matches.", "該当する機能はありません。")));
		list.revalidate();
		list.repaint();
	}

	private JComponent perfCallout() {
		Widgets.Card card = new Widgets.Card(new BorderLayout(14, 0), 16);
		card.fill = Theme.alpha(Theme.ACCENT, Theme.dark ? 30 : 20);
		card.stroke = Theme.alpha(Theme.ACCENT, 120);
		card.setAlignmentX(0);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		card.add(new JLabel(Icons.of("bolt", 28, Theme.ACCENT)), BorderLayout.WEST);
		JPanel t = new JPanel();
		t.setOpaque(false);
		t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
		t.add(Ui.label(c.t("Performance (FPS boost)", "軽量化（FPSブースト）"), 14.5f, Font.BOLD, Theme.TEXT));
		t.add(Ui.muted(c.t("Cap FPS in the background and in menus, skip far entities and busy particles. In game, /skypilot perf also lowers video settings (and restores them).",
				"非アクティブ時やメニュー中のFPSを抑え、遠くのエンティティや多すぎるパーティクルを省きます。ゲーム内の /skypilot perf ではビデオ設定も下げられます（元に戻せます）。")));
		card.add(t, BorderLayout.CENTER);
		JButton on = Widgets.primary(c.t("Turn on", "オンにする"), "bolt");
		on.addActionListener(e -> {
			int n = 0;
			for (Features.Feature f : List.of(Features.PERF_BACKGROUND_FPS, Features.PERF_MENU_FPS, Features.PERF_DAMAGE_SPLASH, Features.PERF_HIT_PARTICLES, Features.PERF_PARTICLE_CAP,
					Features.PERF_FAR_ENTITIES)) {
				if (!cfg.on(f)) {
					Features.set(cfg, f, true);
					n++;
				}
			}
			save(c.t(n + " performance switches turned on.", "軽量化スイッチを " + n + " 個オンにしました。"));
		});
		JPanel bw = new JPanel(new java.awt.GridBagLayout());
		bw.setOpaque(false);
		bw.add(on);
		card.add(bw, BorderLayout.EAST);
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setAlignmentX(0);
		wrap.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
		wrap.add(card);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		return wrap;
	}

	private JComponent row(Features.Feature f) {
		Widgets.Card row = new Widgets.Card(new BorderLayout(14, 0), 14);
		row.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
		row.setAlignmentX(0);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
		JPanel t = new JPanel();
		t.setOpaque(false);
		t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
		JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		title.setOpaque(false);
		title.setAlignmentX(0);
		title.add(Ui.label(f.name(c.l), 13.5f, Font.BOLD, Theme.TEXT));
		if (f.network()) title.add(Widgets.badge(c.t("SERVER", "サーバー"), Theme.ACCENT_2));
		if (f.consent()) title.add(Widgets.badge(c.t("IN GAME", "ゲーム内で設定"), Theme.WARN));
		if (f.recommended()) title.add(Widgets.badge(c.t("RECOMMENDED", "おすすめ"), Theme.OK));
		t.add(title);
		JLabel d = Ui.muted("<html>" + Ui.esc(f.description(c.l)) + "</html>");
		d.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
		d.setAlignmentX(0);
		t.add(d);
		row.add(t, BorderLayout.CENTER);
		Widgets.Toggle toggle = new Widgets.Toggle(cfg.on(f));
		// Chat translation needs the disclosure shown in game; it can be switched off here, not on.
		toggle.setEnabled(!f.consent() || cfg.on(f));
		if (f.consent()) toggle.setToolTipText(c.t("Switch this on in game: it shows what is sent first.", "ゲーム内で有効にしてください（送信内容の説明が先に表示されます）。"));
		toggle.onChange(e -> {
			Features.set(cfg, f, toggle.isOn());
			save(null);
		});
		JPanel tw = new JPanel(new java.awt.GridBagLayout());
		tw.setOpaque(false);
		tw.add(toggle);
		row.add(tw, BorderLayout.EAST);
		return row;
	}

	private void save(String message) {
		try {
			Instance.saveConfig(c.settings.instance(), cfg);
			if (message != null) c.ok(c.t("Saved", "保存しました"), message);
		} catch (Exception e) {
			c.fail(c.t("Could not save", "保存できませんでした"), e);
		}
		reload();
	}
}
