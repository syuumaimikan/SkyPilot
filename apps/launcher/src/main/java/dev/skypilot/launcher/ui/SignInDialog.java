package dev.skypilot.launcher.ui;

import dev.skypilot.launcher.Account;
import dev.skypilot.launcher.LauncherLog;
import dev.skypilot.launcher.MicrosoftAuth;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * "Sign in with Microsoft": shows a short code, opens microsoft.com/link in the browser and
 * waits until you have signed in there (device code flow). Your password is typed only on
 * Microsoft's page. When no client ID is set yet, it explains how to get one.
 */
final class SignInDialog {
	static final String REGISTER_DOCS = "https://learn.microsoft.com/entra/identity-platform/quickstart-register-app";
	static final String MOJANG_FORM = "https://aka.ms/mce-reviewappid";

	private final Ctx c;
	private final Runnable onDone;
	private final CardLayout cards = new CardLayout();
	private final JPanel deck = new JPanel(cards);
	private final JLabel code = Ui.label("······", 34f, Font.BOLD, Theme.TEXT);
	private final JLabel status = Ui.muted(" ");
	private final JTextField clientId = new JTextField();
	private final AtomicBoolean cancelled = new AtomicBoolean();
	private JDialog dialog;
	private MicrosoftAuth.DeviceCode device;

	private SignInDialog(Ctx c, Runnable onDone) {
		this.c = c;
		this.onDone = onDone;
	}

	static void open(Ctx c, Component parent, Runnable onDone) {
		SignInDialog d = new SignInDialog(c, onDone);
		Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
		d.dialog = new JDialog(w, c.t("Sign in with Microsoft", "Microsoft でサインイン"), java.awt.Dialog.ModalityType.MODELESS);
		d.dialog.setContentPane(d.content());
		d.dialog.setSize(560, 430);
		d.dialog.setLocationRelativeTo(w);
		d.dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				d.cancelled.set(true);
			}
		});
		d.dialog.setVisible(true);
		if (MicrosoftAuth.validClientId(c.settings.msClientId)) d.start();
		else d.cards.show(d.deck, "setup");
	}

	/** The dialog's content (also painted by the screenshot test). */
	JComponent content() {
		deck.setOpaque(true);
		deck.setBackground(Theme.BG);
		deck.add(top(setupCard()), "setup");
		deck.add(top(codeCard()), "code");
		return deck;
	}

	/** Keeps a page at its natural height (wrapped text would otherwise stretch). */
	private static JComponent top(JComponent page) {
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(page, BorderLayout.NORTH);
		return p;
	}

	static JComponent preview(Ctx c, boolean setup) {
		SignInDialog d = new SignInDialog(c, () -> {
		});
		JComponent p = d.content();
		if (setup) {
			d.cards.show(d.deck, "setup");
		} else {
			d.code.setText("ABCD-EFGH");
			d.status.setText(c.t("Waiting for you to sign in in the browser…", "ブラウザでのサインインを待っています…"));
			d.cards.show(d.deck, "code");
		}
		return p;
	}

	private JPanel page() {
		JPanel p = new JPanel();
		p.setOpaque(false);
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
		return p;
	}

	private JComponent setupCard() {
		JPanel p = page();
		JLabel title = Ui.label(c.t("One-time setup: a client ID", "最初に一度だけ: クライアントID"), 20f, Font.BOLD, Theme.TEXT);
		title.setAlignmentX(0);
		p.add(title);
		p.add(Box.createVerticalStrut(10));
		var text = Ui.wrap(c.t(
				"Mojang only lets approved apps sign in to Minecraft, and SkyPilot does not borrow another launcher's ID. "
						+ "1) Register a free app in Microsoft Entra (Azure): \"Personal Microsoft accounts\", and turn on \"Allow public client flows\". "
						+ "2) Ask Mojang to allow it for Minecraft (form below; approval can take a while). "
						+ "3) Paste the Application (client) ID here. Until then, PLAY opens the official Minecraft Launcher.",
				"Mojang は承認されたアプリにだけ Minecraft のサインインを許可しています。SkyPilot は他のランチャーのIDを借用しません。"
						+ "① Microsoft Entra（Azure）で無料のアプリを登録します（「個人用 Microsoft アカウント」、「パブリック クライアント フローを許可する」をオン）。"
						+ "② 下のフォームから Mojang に Minecraft での利用を申請します（承認には時間がかかることがあります）。"
						+ "③ アプリケーション（クライアント）ID をここに貼り付けます。それまではプレイを押すと公式ランチャーが開きます。"), Theme.MUTED, 13f);
		text.setAlignmentX(0);
		p.add(text);
		p.add(Box.createVerticalStrut(12));
		JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		links.setOpaque(false);
		links.setAlignmentX(0);
		JButton docs = Widgets.link(c.t("How to register an app", "アプリの登録方法"), "external");
		docs.addActionListener(e -> Ui.browse(REGISTER_DOCS));
		JButton form = Widgets.link(c.t("Mojang's approval form", "Mojang の申請フォーム"), "external");
		form.addActionListener(e -> Ui.browse(MOJANG_FORM));
		links.add(docs);
		links.add(Box.createHorizontalStrut(14));
		links.add(form);
		p.add(links);
		p.add(Box.createVerticalStrut(14));
		clientId.setText(c.settings.msClientId);
		clientId.putClientProperty("JTextField.placeholderText", "00000000-0000-0000-0000-000000000000");
		clientId.setAlignmentX(0);
		clientId.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 34));
		p.add(clientId);
		p.add(Box.createVerticalStrut(14));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(0);
		JButton go = Widgets.primary(c.t("Save and sign in", "保存してサインイン"), "user");
		go.addActionListener(e -> {
			String id = clientId.getText().strip();
			if (!MicrosoftAuth.validClientId(id)) {
				c.toast("alert", Theme.BAD, c.t("Not a client ID", "クライアントIDではありません"), c.t("It looks like 00000000-0000-0000-0000-000000000000.", "00000000-0000-0000-0000-000000000000 の形式です。"));
				return;
			}
			c.settings.msClientId = id;
			try {
				c.settings.save(c.launcherDir);
			} catch (Exception ex) {
				c.fail(c.t("Could not save", "保存できませんでした"), ex);
				return;
			}
			start();
		});
		buttons.add(go);
		p.add(buttons);
		return p;
	}

	private JComponent codeCard() {
		JPanel p = page();
		JLabel title = Ui.label(c.t("Sign in with Microsoft", "Microsoft でサインイン"), 20f, Font.BOLD, Theme.TEXT);
		title.setAlignmentX(0);
		p.add(title);
		p.add(Box.createVerticalStrut(8));
		var how = Ui.wrap(c.t("Open microsoft.com/link, enter this code and sign in. Your password is typed only on Microsoft's page - this launcher never sees it.",
				"microsoft.com/link を開いてこのコードを入力し、サインインしてください。パスワードは Microsoft のページにだけ入力します。このランチャーが見ることはありません。"), Theme.MUTED, 13f);
		how.setAlignmentX(0);
		p.add(how);
		p.add(Box.createVerticalStrut(18));
		JPanel codeBox = new JPanel(new BorderLayout());
		codeBox.setBackground(Theme.SURFACE);
		codeBox.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BORDER), BorderFactory.createEmptyBorder(14, 18, 14, 18)));
		code.setFont(new Font(Font.MONOSPACED, Font.BOLD, 34));
		code.setHorizontalAlignment(JLabel.CENTER);
		codeBox.add(code);
		codeBox.setAlignmentX(0);
		codeBox.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 80));
		p.add(codeBox);
		p.add(Box.createVerticalStrut(16));
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		buttons.setOpaque(false);
		buttons.setAlignmentX(0);
		JButton open = Widgets.primary(c.t("Copy code and open the sign-in page", "コードをコピーしてサインインページを開く"), "external");
		open.addActionListener(e -> {
			if (device == null) return;
			Ui.copy(device.userCode());
			Ui.browse(device.verificationUri());
		});
		JButton cancel = Widgets.button(c.t("Cancel", "キャンセル"), "x");
		cancel.addActionListener(e -> close());
		buttons.add(open);
		buttons.add(Box.createHorizontalStrut(10));
		buttons.add(cancel);
		p.add(buttons);
		p.add(Box.createVerticalStrut(14));
		status.setAlignmentX(0);
		p.add(status);
		p.add(Box.createVerticalStrut(18));
		var note = Ui.wrap(c.t("Kept on this PC only: your name and a renewable sign-in, encrypted for your Windows account. Nothing is sent to the SkyPilot server. Sign out any time in the sidebar.",
				"このPCにだけ保存します: 名前と更新用のサインイン情報（Windowsアカウント用に暗号化）。SkyPilotサーバーには送信しません。サイドバーからいつでもサインアウトできます。"), Theme.FAINT, 11.5f);
		note.setAlignmentX(0);
		p.add(note);
		return p;
	}

	private void start() {
		cards.show(deck, "code");
		code.setText("······");
		status.setText(c.t("Asking Microsoft for a code…", "Microsoft にコードを要求中…"));
		status.setForeground(Theme.MUTED);
		cancelled.set(false);
		String id = c.settings.msClientId;
		MicrosoftAuth auth = new MicrosoftAuth();
		c.async(() -> auth.start(id), d -> {
			device = d;
			code.setText(d.userCode());
			status.setText(c.t("Waiting for you to sign in in the browser…", "ブラウザでのサインインを待っています…"));
			Ui.copy(d.userCode());
			Ui.browse(d.verificationUri());
			c.async(() -> {
				MicrosoftAuth.MsTokens t = auth.poll(id, d, cancelled::get);
				Ctx.later(() -> status.setText(c.t("Signing in to Xbox and Minecraft…", "Xbox と Minecraft にサインイン中…")));
				MicrosoftAuth.Session s = auth.minecraft(t.accessToken());
				boolean kept = Account.signedIn(c.launcherDir, s, t);
				return new Object[] {s, kept};
			}, r -> {
				MicrosoftAuth.Session s = (MicrosoftAuth.Session) r[0];
				LauncherLog.info("signed in with Microsoft");
				c.ok(c.t("Signed in as " + s.name(), s.name() + " でサインインしました"), (Boolean) r[1] ? c.t("PLAY now starts the game from here.", "プレイでこのランチャーから起動します。")
						: c.t("Not remembered on this system: sign in again next time.", "このシステムでは保存できないため、次回もサインインが必要です。"));
				close();
				onDone.run();
			}, this::failed);
		}, this::failed);
	}

	private void failed(Throwable e) {
		if (cancelled.get()) return;
		String msg = e instanceof MicrosoftAuth.AuthException a ? a.text(c.l == dev.skypilot.mod.core.Lang.JA) : c.t("Sign-in failed: ", "サインインに失敗しました: ") + e.getMessage();
		LauncherLog.info("sign-in failed: " + (e instanceof MicrosoftAuth.AuthException a ? a.en : e.getClass().getSimpleName()));
		status.setText("<html>" + Ui.esc(msg) + "</html>");
		status.setForeground(Theme.BAD);
	}

	private void close() {
		cancelled.set(true);
		if (dialog != null) dialog.dispose();
	}
}
