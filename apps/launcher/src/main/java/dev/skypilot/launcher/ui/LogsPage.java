package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.CrashAnalyzer;
import dev.skypilot.launcher.Diagnostics;
import dev.skypilot.launcher.GameWatcher;
import dev.skypilot.launcher.Instance;
import dev.skypilot.launcher.ModScan;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Game output: the latest log with colours and filters, crash reports, and the crash explainer. */
final class LogsPage {
	private enum Filter {
		ALL, PROBLEMS, SKYPILOT
	}

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault());
	private final Ctx c;
	private final JTextPane text = new JTextPane();
	private final JTextField search = new JTextField();
	private final DefaultListModel<Path> crashes = new DefaultListModel<>();
	private final JList<Path> crashList = new JList<>(crashes);
	private final JPanel findings = new JPanel();
	private Filter filter = Filter.ALL;
	private String current = "";
	private Path currentFile;
	private final JComponent root;

	LogsPage(Ctx c) {
		this.c = c;
		root = build();
		reload();
	}

	JComponent view() {
		return root;
	}

	private JComponent build() {
		JPanel page = Ui.page();
		JButton latest = Widgets.button(c.t("Latest log", "最新ログ"), "terminal");
		latest.addActionListener(e -> {
			crashList.clearSelection();
			showLatest();
		});
		JButton copy = Widgets.button(c.t("Copy for support", "サポート用にコピー"), "copy");
		copy.setToolTipText(c.t("Copies the explanation and the last 200 lines, with your user folder hidden.", "説明と最後の200行をコピーします（ユーザーフォルダ名は伏せます）。"));
		copy.addActionListener(e -> copySupport());
		JButton folder = Widgets.button(c.t("Folder", "フォルダ"), "folder");
		folder.addActionListener(e -> Ui.openFolder(currentFile != null ? currentFile.getParent() : c.settings.instance().resolve("logs")));
		page.add(Ui.header(c.t("Logs & crashes", "ログとクラッシュ"), c.t("What the game wrote last time, and plain-language help when it crashed.", "前回ゲームが出力した内容と、クラッシュ時のわかりやすい説明です。"),
				latest, copy, folder), BorderLayout.NORTH);

		findings.setOpaque(false);
		findings.setLayout(new BoxLayout(findings, BoxLayout.Y_AXIS));

		JPanel toolbar = new JPanel(new BorderLayout(10, 0));
		toolbar.setOpaque(false);
		search.putClientProperty("JTextField.placeholderText", c.t("Search the log", "ログを検索"));
		search.putClientProperty("JTextField.leadingIcon", Icons.of("search", 16, Theme.FAINT));
		search.putClientProperty("JTextField.showClearButton", true);
		search.setPreferredSize(new Dimension(240, 32));
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
		JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		chips.setOpaque(false);
		ButtonGroup g = new ButtonGroup();
		for (String[] n : new String[][] {{"ALL", c.t("All", "すべて")}, {"PROBLEMS", c.t("Warnings & errors", "警告とエラー")}, {"SKYPILOT", "SkyPilot"}}) {
			JToggleButton chip = Widgets.chip(n[1]);
			chip.setSelected(n[0].equals("ALL"));
			chip.addActionListener(e -> {
				filter = Filter.valueOf(n[0]);
				render();
			});
			g.add(chip);
			chips.add(chip);
		}
		toolbar.add(chips, BorderLayout.CENTER);

		text.setEditable(false);
		text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		text.setBackground(Theme.SURFACE);
		JScrollPane textScroll = new JScrollPane(text);
		textScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

		crashList.setCellRenderer((l, p, i, sel, focus) -> {
			boolean jvm = p.getFileName().toString().startsWith("hs_err");
			JLabel cell = new JLabel("<html><b>" + DATE.format(Instant.ofEpochMilli(p.toFile().lastModified())) + "</b><br><span style='color:" + Theme.hex(Theme.MUTED) + "'>"
					+ (jvm ? c.t("Java crashed", "Javaのクラッシュ") : c.t("Crash report", "クラッシュレポート")) + "</span></html>", Icons.of("alert", 16, jvm ? Theme.WARN : Theme.BAD),
					JLabel.LEFT);
			cell.setIconTextGap(10);
			cell.setOpaque(true);
			cell.setForeground(Theme.TEXT);
			cell.setBackground(sel ? Theme.alpha(Theme.ACCENT, 60) : Theme.SURFACE);
			cell.setFont(Theme.font(Font.PLAIN, 12.5f));
			cell.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
			return cell;
		});
		crashList.setBackground(Theme.SURFACE);
		crashList.addListSelectionListener(e -> {
			if (e.getValueIsAdjusting() || crashList.getSelectedValue() == null) return;
			show(crashList.getSelectedValue());
		});
		JPanel side = new JPanel(new BorderLayout(0, 8));
		side.setOpaque(false);
		side.setPreferredSize(new Dimension(220, 10));
		side.add(Ui.label(c.t("CRASHES", "クラッシュ"), 11f, Font.BOLD, Theme.MUTED), BorderLayout.NORTH);
		JScrollPane cs = new JScrollPane(crashList);
		cs.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
		side.add(cs, BorderLayout.CENTER);

		JPanel main = new JPanel(new BorderLayout(0, 10));
		main.setOpaque(false);
		JPanel north = new JPanel(new BorderLayout(0, 10));
		north.setOpaque(false);
		north.add(findings, BorderLayout.NORTH);
		north.add(toolbar, BorderLayout.SOUTH);
		main.add(north, BorderLayout.NORTH);
		main.add(textScroll, BorderLayout.CENTER);
		JPanel center = new JPanel(new BorderLayout(16, 0));
		center.setOpaque(false);
		center.add(main, BorderLayout.CENTER);
		center.add(side, BorderLayout.EAST);
		page.add(center, BorderLayout.CENTER);
		return page;
	}

	void reload() {
		crashes.clear();
		List<Path> all = new ArrayList<>(Instance.crashReports(c.settings.instance()));
		Path jvm = GameWatcher.newestCrash(c.settings.instance());
		if (jvm != null && !all.contains(jvm)) all.add(0, jvm);
		for (Path p : all) crashes.addElement(p);
		if (c.playedAt == 0 && !all.isEmpty() && System.currentTimeMillis() - all.get(0).toFile().lastModified() < 3_600_000) {
			crashList.setSelectedIndex(0);
		} else {
			showLatest();
		}
	}

	private void showLatest() {
		currentFile = c.settings.instance().resolve("logs").resolve("latest.log");
		List<String> lines = Instance.latestLog(c.settings.instance(), 3000);
		current = lines.isEmpty() ? c.t("No log yet - play once with the SkyPilot profile.", "まだログがありません。SkyPilotプロファイルで一度プレイしてください。") : String.join("\n", lines);
		analyze();
		render();
	}

	private void show(Path f) {
		currentFile = f;
		try {
			byte[] data = Files.readAllBytes(f);
			current = new String(data, 0, Math.min(data.length, 2_000_000), StandardCharsets.UTF_8);
		} catch (Exception e) {
			current = e.getMessage();
		}
		analyze();
		render();
	}

	private void analyze() {
		findings.removeAll();
		List<ModScan.Meta> mods = ModScan.scan(c.settings.mods());
		List<CrashAnalyzer.Finding> list = CrashAnalyzer.analyze(current, mods, c.settings.memoryMb, c.l);
		boolean crash = currentFile != null && !currentFile.getFileName().toString().equals("latest.log");
		boolean interesting = list.stream().anyMatch(f -> f.kind() != CrashAnalyzer.Kind.INFO) || crash;
		if (interesting && !list.isEmpty()) {
			Widgets.Card card = new Widgets.Card(null, 14);
			card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
			card.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
			JLabel head = Ui.label(c.t("WHAT HAPPENED", "何が起きたか"), 11f, Font.BOLD, Theme.MUTED);
			head.setAlignmentX(0);
			card.add(head);
			card.add(Box.createVerticalStrut(6));
			for (CrashAnalyzer.Finding f : list) {
				Color col = switch (f.kind()) {
					case CAUSE -> Theme.BAD;
					case MOD -> Theme.WARN;
					case FIX -> Theme.OK;
					case INFO -> Theme.MUTED;
				};
				String icon = switch (f.kind()) {
					case CAUSE -> "alert";
					case MOD -> "mods";
					case FIX -> "wrench";
					case INFO -> "info";
				};
				JPanel row = new JPanel(new BorderLayout(10, 0));
				row.setOpaque(false);
				row.setAlignmentX(0);
				row.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
				row.add(new JLabel(Icons.of(icon, 16, col)), BorderLayout.WEST);
				JPanel t = new JPanel();
				t.setOpaque(false);
				t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));
				var title = Ui.wrap(f.title(), Theme.TEXT, 13f);
				title.setFont(Theme.font(Font.BOLD, 13f));
				t.add(title);
				if (!f.detail().isEmpty()) t.add(Ui.wrap(f.detail(), Theme.MUTED, 12f));
				row.add(t, BorderLayout.CENTER);
				card.add(row);
			}
			findings.add(card);
		}
		findings.revalidate();
		findings.repaint();
	}

	private void render() {
		String q = search.getText().strip().toLowerCase(Locale.ROOT);
		StyledDocument doc = text.getStyledDocument();
		try {
			doc.remove(0, doc.getLength());
			SimpleAttributeSet normal = style(Theme.TEXT);
			SimpleAttributeSet warn = style(Theme.WARN);
			SimpleAttributeSet error = style(Theme.BAD);
			SimpleAttributeSet sky = style(Theme.ACCENT);
			SimpleAttributeSet faint = style(Theme.MUTED);
			int shown = 0;
			for (String line : current.split("\n", -1)) {
				String low = line.toLowerCase(Locale.ROOT);
				boolean isWarn = low.contains("/warn]") || low.contains("[warn");
				boolean isError = low.contains("/error]") || low.contains("/fatal]") || low.contains("exception") || line.startsWith("\tat ") || low.contains("[error");
				boolean isSky = low.contains("skypilot");
				if (filter == Filter.PROBLEMS && !isWarn && !isError) continue;
				if (filter == Filter.SKYPILOT && !isSky) continue;
				if (!q.isEmpty() && !low.contains(q)) continue;
				doc.insertString(doc.getLength(), line + "\n", isError ? error : isWarn ? warn : isSky ? sky : line.startsWith("\tat ") ? faint : normal);
				if (++shown > 5000) break;
			}
		} catch (BadLocationException e) {
			// document rebuilt from scratch: not reached
		}
		text.setCaretPosition(currentFile != null && currentFile.getFileName().toString().equals("latest.log") ? doc.getLength() : 0);
	}

	private static SimpleAttributeSet style(Color c) {
		SimpleAttributeSet s = new SimpleAttributeSet();
		StyleConstants.setForeground(s, c);
		return s;
	}

	private void copySupport() {
		StringBuilder b = new StringBuilder();
		b.append("SkyPilot ").append(c.pins.modVersion()).append(" / Minecraft ").append(c.pins.minecraft()).append(" / Fabric ").append(c.pins.loader()).append('\n');
		for (CrashAnalyzer.Finding f : CrashAnalyzer.analyze(current, ModScan.scan(c.settings.mods()), c.settings.memoryMb, c.l)) {
			b.append("- ").append(f.title()).append(f.detail().isEmpty() ? "" : " (" + f.detail() + ")").append('\n');
		}
		String[] lines = current.split("\n");
		b.append("\n--- ").append(currentFile == null ? "log" : currentFile.getFileName()).append(" (last 200 lines) ---\n");
		for (int i = Math.max(0, lines.length - 200); i < lines.length; i++) b.append(lines[i]).append('\n');
		Ui.copy(Diagnostics.anonymize(b.toString()));
		c.ok(c.t("Copied", "コピーしました"), c.t("Paste it into your bug report or support chat.", "不具合報告やサポートに貼り付けてください。"));
	}
}
