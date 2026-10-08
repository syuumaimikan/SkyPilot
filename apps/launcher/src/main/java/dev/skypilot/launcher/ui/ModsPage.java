package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.FabricVersion;
import dev.skypilot.launcher.ModCheck;
import dev.skypilot.launcher.ModScan;
import dev.skypilot.launcher.ModrinthUpdates;
import dev.skypilot.launcher.Mods;
import dev.skypilot.launcher.Net;
import dev.skypilot.launcher.Presets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.TransferHandler;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Mods of the SkyPilot game folder: list, switches, compatibility, presets, updates, details. */
final class ModsPage {
	private enum Filter {
		ALL, ON, OFF, PROBLEMS, UPDATES
	}

	private final Ctx c;
	private final JPanel list = new Ui.ScrollBody(null);
	private final JPanel details = new JPanel(new BorderLayout());
	private final JPanel banner = new JPanel(new BorderLayout());
	private final JLabel count = Ui.muted("");
	private final JTextField search = new JTextField();
	private List<ModScan.Meta> mods = List.of();
	private List<ModCheck.Issue> issues = List.of();
	private Map<Path, ModrinthUpdates.Update> updates = Map.of();
	private final Map<Path, BufferedImage> icons = new HashMap<>();
	private Filter filter = Filter.ALL;
	private Path selected;
	private final JComponent root;

	ModsPage(Ctx c) {
		this.c = c;
		root = build();
		reload();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JButton updatesBtn = Widgets.button(c.t("Check for updates", "更新を確認"), "refresh");
		updatesBtn.addActionListener(e -> checkUpdates(updatesBtn));
		JButton add = Widgets.primary(c.t("Add mods", "MODを追加"), "plus");
		add.addActionListener(e -> pickFiles());
		JButton folder = Widgets.button(c.t("Folder", "フォルダ"), "folder");
		JButton packs = Widgets.button(c.t("Expansion packs", "拡張パック"), "archive");
		packs.addActionListener(e -> companionPack(packs));
		folder.addActionListener(e -> Ui.openFolder(c.settings.mods()));
		page.add(Ui.header(c.t("Mods", "MOD"), c.t("Drop .jar files here to add them. Switching off keeps the file; select a mod to delete it.",
				".jarファイルをドロップすると追加できます。オフはファイルを残します。削除する場合はMODを選択してください。"), updatesBtn, packs, folder, add), BorderLayout.NORTH);

		JPanel toolbar = new JPanel(new BorderLayout(12, 0));
		toolbar.setOpaque(false);
		search.putClientProperty("JTextField.placeholderText", c.t("Search mods", "MODを検索"));
		search.putClientProperty("JTextField.leadingIcon", Icons.of("search", 16, Theme.FAINT));
		search.putClientProperty("JTextField.showClearButton", true);
		search.setPreferredSize(new Dimension(240, 34));
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
		toolbar.add(search, BorderLayout.WEST);
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
		chips.setOpaque(false);
		ButtonGroup g = new ButtonGroup();
		String[][] names = {{"ALL", c.t("All", "すべて")}, {"ON", c.t("On", "オン")}, {"OFF", c.t("Off", "オフ")}, {"PROBLEMS", c.t("Problems", "問題あり")}, {"UPDATES", c.t("Updates", "更新あり")}};
		for (String[] n : names) {
			JToggleButton chip = Widgets.chip(n[1]);
			chip.setSelected(n[0].equals(filter.name()));
			chip.addActionListener(e -> {
				filter = Filter.valueOf(n[0]);
				render();
			});
			g.add(chip);
			chips.add(chip);
		}
		toolbar.add(chips, BorderLayout.CENTER);
		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);
		right.add(count);
		JButton presets = Widgets.button(c.t("Presets", "プリセット"), "archive");
		presets.addActionListener(e -> presetsMenu(presets));
		right.add(presets);
		JButton safe = Widgets.button(c.t("Troubleshoot", "トラブルシュート"), "wrench");
		safe.setToolTipText(c.t("Only SkyPilot and Fabric API stay on; your current set is saved as a preset first.", "SkyPilotとFabric APIだけを残します。今の状態は先にプリセットとして保存します。"));
		safe.addActionListener(e -> safeMode());
		right.add(safe);
		toolbar.add(right, BorderLayout.EAST);

		banner.setOpaque(false);
		JPanel top = new JPanel(new BorderLayout(0, 12));
		top.setOpaque(false);
		top.add(toolbar, BorderLayout.NORTH);
		top.add(banner, BorderLayout.CENTER);

		list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
		details.setOpaque(false);
		details.setPreferredSize(new Dimension(330, 10));
		JPanel center = new JPanel(new BorderLayout(16, 12));
		center.setOpaque(false);
		center.add(top, BorderLayout.NORTH);
		center.add(Ui.scroll(list), BorderLayout.CENTER);
		center.add(details, BorderLayout.EAST);
		page.add(center, BorderLayout.CENTER);
		page.setTransferHandler(new TransferHandler() {
			@Override
			public boolean canImport(TransferSupport s) {
				return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
			}

			@Override
			public boolean importData(TransferSupport s) {
				try {
					@SuppressWarnings("unchecked")
					List<File> files = (List<File>) s.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
					addFiles(files.stream().map(File::toPath).toList());
					return true;
				} catch (Exception e) {
					c.fail(c.t("Could not add", "追加できませんでした"), e);
					return false;
				}
			}
		});
		return page;
	}

	void reload() {
		c.async(() -> {
			List<ModScan.Meta> scanned = ModScan.scan(c.settings.mods());
			Map<Path, BufferedImage> img = new HashMap<>();
			for (ModScan.Meta m : scanned) img.put(m.file(), Widgets.read(m.icon()));
			return new Object[] {scanned, img, ModCheck.check(scanned, ModCheck.builtins(c.pins, c.settings.minecraft()), c.l)};
		}, r -> {
			@SuppressWarnings("unchecked")
			List<ModScan.Meta> scanned = (List<ModScan.Meta>) r[0];
			@SuppressWarnings("unchecked")
			Map<Path, BufferedImage> img = (Map<Path, BufferedImage>) r[1];
			@SuppressWarnings("unchecked")
			List<ModCheck.Issue> found = (List<ModCheck.Issue>) r[2];
			mods = scanned;
			icons.clear();
			icons.putAll(img);
			issues = found;
			if (selected != null && mods.stream().noneMatch(m -> m.file().equals(selected))) {
				// the file was renamed by a switch: follow it
				String base = baseOf(selected);
				selected = mods.stream().filter(m -> m.baseName().equals(base)).map(ModScan.Meta::file).findFirst().orElse(null);
			}
			if (selected == null && !mods.isEmpty()) {
				// start with the first mod that has a problem (or the first mod), so the details are never empty
				selected = mods.stream().filter(m -> ModCheck.worst(issues, m.file()) == ModCheck.Level.ERROR).map(ModScan.Meta::file).findFirst().orElse(mods.get(0).file());
			}
			render();
		}, e -> c.fail(c.t("Could not read the mods", "MODを読めませんでした"), e));
	}

	private void companionPack(JButton button) {
		String[] choices = {c.t("Performance", "軽量化"), c.t("Performance + shaders", "軽量化＋シェーダー"), c.t("SkyBlock helpers", "SkyBlock補助"), c.t("All", "すべて"), c.t("Cancel", "キャンセル")};
		int choice = JOptionPane.showOptionDialog(root,
				c.t("Performance: Sodium, FerriteCore, Lithium. Shaders adds Iris.\nSkyBlock helpers: Skyblocker + Fabric Language Kotlin.\nPinned Fabric 26.2 releases, verified with SHA-512. Close Minecraft first.\nExisting different versions are kept; manage them before installing.\nShader packs are selected separately in Iris video settings.\nRun Repair on Home first to install SkyPilot / Fabric API.\nSkyblocker may duplicate SkyPilot overlays; configure its features separately.",
						"軽量化: Sodium・FerriteCore・Lithium。シェーダー版はIrisも追加。\nSkyBlock補助: Skyblocker＋Fabric Language Kotlin。\nFabric 26.2対応の固定版をSHA-512で検証します。先にMinecraftを閉じてください。\n別バージョンがある場合は上書きせず停止します。\nシェーダーパックはIrisのビデオ設定から別途選択してください。\n先にホームの「修復」でSkyPilot・Fabric APIを導入してください。\nSkyblockerと重複するHUDはそれぞれの設定で調整できます。"),
				c.t("Expansion packs", "拡張パック"), JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, choices, choices[0]);
		if (choice < 0 || choice > 3) return;
		var entries = choice == 2 ? dev.skypilot.launcher.CompanionMods.skyblockPack() : choice == 3 ? dev.skypilot.launcher.CompanionMods.catalog().mods() : dev.skypilot.launcher.CompanionMods.pack(choice == 1);
		button.setEnabled(false);
		c.async(() -> dev.skypilot.launcher.CompanionMods.install(c.settings.mods(), entries,
				ModCheck.builtins(c.pins, c.settings.minecraft()), Net::download), n -> {
			button.setEnabled(true); reload();
			c.ok(c.t("Pack ready", "拡張パックを導入しました"), c.t(n + " mods added. Start Minecraft to use them.", n + " 個追加しました。Minecraftの起動後に使えます。"));
		}, e -> {button.setEnabled(true); c.fail(c.t("Pack could not be installed", "拡張パックを導入できませんでした"), e);});
	}

	private static String baseOf(Path p) {
		String n = p.getFileName().toString();
		return n.endsWith(".disabled") ? n.substring(0, n.length() - ".disabled".length()) : n;
	}

	private boolean visible(ModScan.Meta m) {
		String q = search.getText().strip().toLowerCase(Locale.ROOT);
		if (!q.isEmpty() && !(m.name() + " " + m.id() + " " + m.description() + " " + m.file().getFileName()).toLowerCase(Locale.ROOT).contains(q)) return false;
		return switch (filter) {
			case ALL -> true;
			case ON -> m.enabled();
			case OFF -> !m.enabled();
			case PROBLEMS -> ModCheck.worst(issues, m.file()) != null && ModCheck.worst(issues, m.file()) != ModCheck.Level.INFO;
			case UPDATES -> updates.containsKey(m.file()) && !updates.get(m.file()).upToDate();
		};
	}

	private void render() {
		list.removeAll();
		int on = (int) mods.stream().filter(ModScan.Meta::enabled).count();
		count.setText(c.t(on + " on · " + mods.size() + " total", "オン " + on + " / 全 " + mods.size()));
		List<ModScan.Meta> shown = mods.stream().filter(this::visible).toList();
		for (ModScan.Meta m : shown) {
			list.add(row(m));
			list.add(Box.createVerticalStrut(6));
		}
		if (shown.isEmpty()) {
			JLabel empty = Ui.muted(mods.isEmpty() ? c.t("No mods yet. Press \"Set up\" on Home, or drop .jar files here.", "まだMODがありません。ホームでセットアップするか、.jarをここにドロップしてください。")
					: c.t("Nothing matches.", "該当するMODはありません。"));
			empty.setBorder(BorderFactory.createEmptyBorder(20, 6, 0, 0));
			list.add(empty);
		}
		list.revalidate();
		list.repaint();
		renderBanner();
		renderDetails();
	}

	private void renderBanner() {
		banner.removeAll();
		long errors = issues.stream().filter(i -> i.level() == ModCheck.Level.ERROR).count();
		long warnings = issues.stream().filter(i -> i.level() == ModCheck.Level.WARNING).count();
		if (errors + warnings > 0) {
			Widgets.Card card = new Widgets.Card(new BorderLayout(12, 0), 14);
			Color col = errors > 0 ? Theme.BAD : Theme.WARN;
			card.fill = Theme.alpha(col, Theme.dark ? 28 : 22);
			card.stroke = Theme.alpha(col, 120);
			card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
			card.add(new JLabel(Icons.of("alert", 20, col)), BorderLayout.WEST);
			String text = errors > 0 ? c.t(errors + " problem(s) - the game may not start. Click a highlighted mod for details.", errors + " 件の問題があり、ゲームが起動しない可能性があります。印の付いたMODをクリックすると詳細が出ます。")
					: c.t(warnings + " warning(s). Click a highlighted mod for details.", warnings + " 件の注意があります。印の付いたMODをクリックすると詳細が出ます。");
			JLabel l = new JLabel(text);
			l.setFont(Theme.font(Font.BOLD, 12.5f));
			card.add(l, BorderLayout.CENTER);
			JButton show = Widgets.link(c.t("Show", "表示"), null);
			show.addActionListener(e -> {
				filter = Filter.PROBLEMS;
				render();
			});
			card.add(show, BorderLayout.EAST);
			banner.add(card, BorderLayout.CENTER);
		}
		banner.revalidate();
		banner.repaint();
	}

	private JComponent row(ModScan.Meta m) {
		boolean sel = m.file().equals(selected);
		JPanel row = new JPanel(new BorderLayout(14, 0)) {
			boolean over;

			{
				addMouseListener(new MouseAdapter() {
					@Override
					public void mouseEntered(MouseEvent e) {
						over = true;
						repaint();
					}

					@Override
					public void mouseExited(MouseEvent e) {
						over = false;
						repaint();
					}

					@Override
					public void mouseClicked(MouseEvent e) {
						selected = m.file();
						render();
					}
				});
			}

			@Override
			protected void paintComponent(Graphics g0) {
				Graphics2D g = Widgets.smooth(g0);
				g.setColor(sel ? Theme.alpha(Theme.ACCENT, Theme.dark ? 36 : 28) : over ? Theme.SURFACE_2 : Theme.SURFACE);
				g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 14, 14));
				g.setColor(sel ? Theme.alpha(Theme.ACCENT, 140) : Theme.BORDER);
				g.draw(new RoundRectangle2D.Double(0.5, 0.5, getWidth() - 1, getHeight() - 1, 14, 14));
				g.dispose();
			}
		};
		row.setOpaque(false);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 14));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
		row.setAlignmentX(0);
		row.add(Widgets.roundImage(icons.get(m.file()), 40, m.name(), false), BorderLayout.WEST);
		JPanel text = new JPanel();
		text.setOpaque(false);
		text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		titleRow.setOpaque(false);
		titleRow.setAlignmentX(0);
		JLabel name = new JLabel(m.name());
		name.setFont(Theme.font(Font.BOLD, 14f));
		name.setForeground(m.enabled() ? Theme.TEXT : Theme.FAINT);
		titleRow.add(name);
		titleRow.add(Ui.label(m.version(), 12f, Font.PLAIN, Theme.FAINT));
		ModCheck.Level worst = ModCheck.worst(issues, m.file());
		if (worst == ModCheck.Level.ERROR) titleRow.add(Widgets.badge(c.t("PROBLEM", "問題"), Theme.BAD));
		else if (worst == ModCheck.Level.WARNING) titleRow.add(Widgets.badge(c.t("CHECK", "注意"), Theme.WARN));
		ModrinthUpdates.Update u = updates.get(m.file());
		if (u != null && !u.upToDate()) titleRow.add(Widgets.badge(c.t("UPDATE", "更新あり"), Theme.ACCENT_2));
		if (!m.enabled()) titleRow.add(Widgets.badge(c.t("OFF", "オフ"), Theme.FAINT));
		if (m.id().equals("skypilot")) titleRow.add(Widgets.badge("SKYPILOT", Theme.ACCENT));
		text.add(titleRow);
		JLabel desc = Ui.muted(m.description().isEmpty() ? m.file().getFileName().toString() : m.description().replace('\n', ' '));
		desc.setAlignmentX(0);
		desc.setBorder(BorderFactory.createEmptyBorder(2, 6, 0, 0));
		text.add(desc);
		row.add(text, BorderLayout.CENTER);
		Widgets.Toggle toggle = new Widgets.Toggle(m.enabled());
		toggle.setToolTipText(c.t("Switch on / off (applies the next time the game starts)", "オン／オフ（次回のゲーム起動から反映）"));
		toggle.onChange(e -> toggle(m));
		JPanel tw = new JPanel(new java.awt.GridBagLayout());
		tw.setOpaque(false);
		tw.add(toggle);
		row.add(tw, BorderLayout.EAST);
		return row;
	}

	private void toggle(ModScan.Meta m) {
		try {
			Mods.toggle(new Mods.ModFile(m.file(), m.id(), m.name(), m.version(), m.enabled()));
			selected = m.file();
		} catch (Exception e) {
			c.fail(c.t("Could not switch", "切り替えできませんでした"), e);
		}
		reload();
	}

	private void renderDetails() {
		details.removeAll();
		ModScan.Meta m = mods.stream().filter(x -> x.file().equals(selected)).findFirst().orElse(null);
		if (m == null) {
			Widgets.Card hint = new Widgets.Card(new BorderLayout());
			hint.add(Ui.wrap(c.t("Select a mod to see its details, dependencies and problems.", "MODを選ぶと、詳細・依存関係・問題点が表示されます。"), Theme.MUTED, 13f), BorderLayout.NORTH);
			details.add(hint, BorderLayout.NORTH);
		} else {
			details.add(Ui.scroll(detailBody(m)), BorderLayout.CENTER);
		}
		details.revalidate();
		details.repaint();
	}

	private JComponent detailBody(ModScan.Meta m) {
		Widgets.Card card = new Widgets.Card(null);
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		JPanel head = new JPanel(new BorderLayout(12, 0));
		head.setOpaque(false);
		head.setAlignmentX(0);
		head.add(Widgets.roundImage(icons.get(m.file()), 56, m.name(), false), BorderLayout.WEST);
		JPanel ht = new JPanel();
		ht.setOpaque(false);
		ht.setLayout(new BoxLayout(ht, BoxLayout.Y_AXIS));
		ht.add(Ui.label(m.name(), 17f, Font.BOLD, Theme.TEXT));
		ht.add(Ui.muted(m.version() + (m.id().isEmpty() ? "" : "  ·  " + m.id())));
		if (!m.authors().isEmpty()) ht.add(Ui.muted(String.join(", ", m.authors().subList(0, Math.min(3, m.authors().size())))));
		head.add(ht, BorderLayout.CENTER);
		card.add(head);
		card.add(Box.createVerticalStrut(12));
		if (!m.description().isEmpty()) {
			var d = Ui.wrap(m.description(), Theme.TEXT, 13f);
			d.setAlignmentX(0);
			card.add(d);
			card.add(Box.createVerticalStrut(10));
		}
		JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		links.setOpaque(false);
		links.setAlignmentX(0);
		for (Map.Entry<String, String> e : m.links().entrySet()) {
			String label = switch (e.getKey()) {
				case "homepage" -> c.t("Website", "サイト");
				case "sources" -> c.t("Source", "ソース");
				default -> c.t("Issues", "不具合報告");
			};
			JButton b = Widgets.link(label, "external");
			b.addActionListener(ev -> Ui.browse(e.getValue()));
			links.add(b);
		}
		ModrinthUpdates.Update u = updates.get(m.file());
		if (u != null && !u.upToDate()) {
			JButton b = Widgets.link(c.t("Update: ", "更新: ") + u.versionNumber(), "external");
			b.setToolTipText(c.t("Opens the version page on Modrinth (nothing is downloaded by SkyPilot)", "Modrinthのページを開きます（SkyPilotはダウンロードしません）"));
			b.addActionListener(ev -> Ui.browse(u.url()));
			links.add(b);
		}
		card.add(links);
		List<ModCheck.Issue> mine = ModCheck.of(issues, m.file());
		if (!mine.isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			card.add(section(c.t("Problems", "問題点")));
			for (ModCheck.Issue i : mine) {
				Color col = i.level() == ModCheck.Level.ERROR ? Theme.BAD : i.level() == ModCheck.Level.WARNING ? Theme.WARN : Theme.MUTED;
				JPanel r = new JPanel(new BorderLayout(8, 0));
				r.setOpaque(false);
				r.setAlignmentX(0);
				r.add(new JLabel(Icons.of(i.level() == ModCheck.Level.INFO ? "info" : "alert", 15, col)), BorderLayout.WEST);
				r.add(Ui.wrap(i.text(), Theme.TEXT, 12.5f), BorderLayout.CENTER);
				r.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
				card.add(r);
			}
		}
		if (!m.depends().isEmpty()) {
			card.add(Box.createVerticalStrut(10));
			card.add(section(c.t("Needs", "必要なもの")));
			Map<String, String> present = new HashMap<>(ModCheck.builtins(c.pins, c.settings.minecraft()));
			for (ModScan.Meta o : mods) if (o.enabled()) present.putAll(o.provides());
			for (Map.Entry<String, List<String>> d : m.depends().entrySet()) {
				String have = present.get(d.getKey());
				boolean ok = d.getKey().equals("mixinextras") || have != null && FabricVersion.matchesAny(d.getValue(), have);
				JLabel l = new JLabel(d.getKey() + "  " + String.join(" | ", d.getValue()) + (have == null ? "" : "  (" + have + ")"), Icons.of(ok ? "check" : "x", 14, ok ? Theme.OK : Theme.BAD),
						JLabel.LEFT);
				l.setFont(Theme.font(Font.PLAIN, 12f));
				l.setAlignmentX(0);
				l.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
				card.add(l);
			}
		}
		card.add(Box.createVerticalStrut(12));
		card.add(section(c.t("File", "ファイル")));
		JLabel f = Ui.muted(m.file().getFileName() + "  ·  " + Ui.size(m.size()) + (m.license().isEmpty() ? "" : "  ·  " + m.license()));
		f.setAlignmentX(0);
		card.add(f);
		card.add(Box.createVerticalStrut(10));
		JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		actions.setOpaque(false);
		actions.setAlignmentX(0);
		JButton toggle = Widgets.button(m.enabled() ? c.t("Switch off", "オフにする") : c.t("Switch on", "オンにする"), "power");
		toggle.addActionListener(e -> toggle(m));
		actions.add(toggle);
		actions.add(Box.createHorizontalStrut(8));
		JButton show = Widgets.button(c.t("Show file", "ファイルを表示"), "folder");
		show.addActionListener(e -> Ui.openFolder(m.file().getParent()));
		actions.add(show);
		card.add(actions);
		card.add(Box.createVerticalStrut(8));
		JPanel removal = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		removal.setOpaque(false);
		removal.setAlignmentX(0);
		JButton delete = Widgets.button(c.t("Delete…", "削除…"), "x");
		delete.setForeground(Theme.BAD);
		delete.addActionListener(e -> deleteMod(m));
		removal.add(delete);
		card.add(removal);
		JPanel wrap = new Ui.ScrollBody(new BorderLayout());
		wrap.add(card, BorderLayout.NORTH);
		return wrap;
	}

	private JLabel section(String text) {
		JLabel l = Ui.label(text.toUpperCase(Locale.ROOT), 11f, Font.BOLD, Theme.MUTED);
		l.setAlignmentX(0);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return l;
	}

	// ---------------------------------------------------------------- actions

	private void deleteMod(ModScan.Meta m) {
		String message = c.t("Permanently delete this mod file? This cannot be undone.\nClose Minecraft first.\n\n", "このMODファイルを完全に削除しますか？ 元に戻せません。\n先にMinecraftを終了してください。\n\n")
				+ m.name() + " " + m.version() + "\n" + m.file().getFileName() + "\n" + m.file().getParent();
		if (m.id().equals("skypilot") || m.id().equals("fabric-api")) {
			message += c.t("\n\nSkyPilot and Fabric API are installed again by Repair or Play.", "\n\nSkyPilot・Fabric APIは「修復」または「プレイ」で再導入されます。");
		}
		var dependents = mods.stream().filter(o -> o.enabled() && !o.file().equals(m.file())
				&& o.depends().keySet().stream().anyMatch(m.provides()::containsKey)).map(ModScan.Meta::name).distinct().toList();
		if (m.enabled() && !dependents.isEmpty()) message += c.t("\n\nRequired by: ", "\n\nこのMODが必要なもの: ") + String.join(", ", dependents);
		String[] choices = {c.t("Cancel", "キャンセル"), c.t("Delete", "削除")};
		int choice = JOptionPane.showOptionDialog(root, message, c.t("Delete mod", "MODを削除"),
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, choices, choices[0]);
		if (choice != 1) return;
		try {
			Mods.delete(c.settings.mods(), m.file());
			updates = new HashMap<>(updates); updates.remove(m.file());
			selected = null;
			c.ok(c.t("Mod deleted", "MODを削除しました"), m.file().getFileName().toString());
		} catch (Exception e) {
			c.fail(c.t("Could not delete; close Minecraft and try again", "削除できませんでした。Minecraftを終了して再試行してください"), e);
		}
		reload();
	}

	private void pickFiles() {
		JFileChooser ch = new JFileChooser();
		ch.setMultiSelectionEnabled(true);
		ch.setFileFilter(new FileNameExtensionFilter(c.t("Fabric mods (.jar)", "Fabric MOD (.jar)"), "jar"));
		if (ch.showOpenDialog(root) != JFileChooser.APPROVE_OPTION) return;
		addFiles(java.util.Arrays.stream(ch.getSelectedFiles()).map(File::toPath).toList());
	}

	private void addFiles(List<Path> files) {
		int ok = 0;
		List<String> failed = new ArrayList<>();
		for (Path p : files) {
			try {
				Mods.add(c.settings.mods(), p);
				ok++;
			} catch (Exception e) {
				failed.add(p.getFileName() + ": " + e.getMessage());
			}
		}
		if (ok > 0) c.ok(c.t(ok + " mod(s) added", ok + " 個のMODを追加しました"), c.t("They load the next time the game starts.", "次回のゲーム起動から読み込まれます。"));
		if (!failed.isEmpty()) c.toast("alert", Theme.WARN, c.t("Not added", "追加しませんでした"), String.join("\n", failed));
		reload();
	}

	private void checkUpdates(JButton btn) {
		btn.setEnabled(false);
		List<Path> files = mods.stream().filter(ModScan.Meta::fabric).map(ModScan.Meta::file).toList();
		c.async(() -> ModrinthUpdates.check(files, c.pins.minecraft(), (url, json) -> Net.postInfo(url, json, 4L << 20)), r -> {
			btn.setEnabled(true);
			updates = r;
			long n = r.values().stream().filter(u -> !u.upToDate()).count();
			c.toast("refresh", Theme.ACCENT_2, n == 0 ? c.t("Everything is up to date", "すべて最新です") : c.t(n + " update(s) on Modrinth", "Modrinthに " + n + " 件の更新"),
					c.t("Checked " + r.size() + " of " + files.size() + " mods known to Modrinth. Links only; nothing is downloaded.",
							files.size() + " 個中 " + r.size() + " 個がModrinthで見つかりました。リンク表示のみで、ダウンロードはしません。"));
			if (n > 0) filter = Filter.UPDATES;
			render();
		}, e -> {
			btn.setEnabled(true);
			c.fail(c.t("Update check failed", "更新確認に失敗しました"), e);
		});
	}

	private void presetsMenu(JComponent anchor) {
		JPopupMenu menu = new JPopupMenu();
		Map<String, Presets.Preset> all = Presets.load(c.launcherDir, c.settings.instance());
		for (Map.Entry<String, Presets.Preset> e : all.entrySet()) {
			String label = e.getKey().equals(Presets.BEFORE_SAFE_MODE) ? c.t("Before troubleshooting", "トラブルシュート前") : e.getKey();
			JMenuItem it = new JMenuItem(c.t("Apply: ", "適用: ") + label + "  (" + e.getValue().on().size() + ")");
			it.addActionListener(ev -> {
				try {
					int n = Presets.apply(c.settings.mods(), e.getValue());
					c.ok(c.t("Preset applied", "プリセットを適用しました"), c.t(n + " file(s) switched.", n + " 個のファイルを切り替えました。"));
				} catch (Exception ex) {
					c.fail(c.t("Could not apply", "適用できませんでした"), ex);
				}
				reload();
			});
			menu.add(it);
		}
		if (!all.isEmpty()) menu.addSeparator();
		JMenuItem save = new JMenuItem(c.t("Save current set as…", "今の状態を保存…"), Icons.of("plus", 14));
		save.addActionListener(ev -> {
			String name = JOptionPane.showInputDialog(root, c.t("Preset name", "プリセット名"), c.t("Save preset", "プリセットを保存"), JOptionPane.PLAIN_MESSAGE);
			if (name == null || name.isBlank()) return;
			try {
				Presets.save(c.launcherDir, c.settings.instance(), name, Presets.capture(Mods.list(c.settings.mods())));
				c.ok(c.t("Preset saved", "プリセットを保存しました"), name);
			} catch (Exception ex) {
				c.fail(c.t("Could not save", "保存できませんでした"), ex);
			}
		});
		menu.add(save);
		if (!all.isEmpty()) {
			JMenu del = new JMenu(c.t("Delete", "削除"));
			for (String name : all.keySet()) {
				JMenuItem it = new JMenuItem(name);
				it.addActionListener(ev -> {
					try {
						Presets.delete(c.launcherDir, c.settings.instance(), name);
					} catch (Exception ex) {
						c.fail(c.t("Could not delete", "削除できませんでした"), ex);
					}
				});
				del.add(it);
			}
			menu.add(del);
		}
		menu.show(anchor, 0, anchor.getHeight());
	}

	private void safeMode() {
		int ok = JOptionPane.showConfirmDialog(root, c.t("Switch off every mod except SkyPilot and Fabric API?\nYour current set is saved as the preset \"Before troubleshooting\".",
				"SkyPilotとFabric API以外のMODをすべてオフにしますか？\n今の状態はプリセット「トラブルシュート前」として保存されます。"), c.t("Troubleshoot", "トラブルシュート"), JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) return;
		try {
			int n = Presets.safeMode(c.launcherDir, c.settings.instance(), c.settings.mods());
			c.ok(c.t("Troubleshoot mode", "トラブルシュートモード"), c.t(n + " mod(s) switched off. Presets → \"Before troubleshooting\" brings them back.",
					n + " 個のMODをオフにしました。プリセット →「トラブルシュート前」で戻せます。"));
		} catch (Exception e) {
			c.fail(c.t("Could not switch", "切り替えできませんでした"), e);
		}
		reload();
	}

	/** For tests: select a mod. */
	void select(String id) {
		mods.stream().filter(m -> m.id().equals(id)).findFirst().ifPresent(m -> selected = m.file());
		render();
	}
}
