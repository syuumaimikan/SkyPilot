package dev.skypilot.launcher.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;
import java.util.Map;

/** Colours, fonts and the FlatLaf setup, for the dark (default) and light themes. */
final class Theme {
	static boolean dark = true;
	static Color BG, SURFACE, SURFACE_2, BORDER, SIDEBAR, TEXT, MUTED, FAINT, ACCENT, ACCENT_2, OK, WARN, BAD, HERO_A, HERO_B;

	private Theme() {
	}

	static void apply(boolean darkTheme) {
		dark = darkTheme;
		if (dark) {
			BG = new Color(0x090D16);
			SURFACE = new Color(0x111827);
			SURFACE_2 = new Color(0x1B2538);
			BORDER = new Color(0x29354D);
			SIDEBAR = new Color(0x080B12);
			TEXT = new Color(0xEDEDF5);
			MUTED = new Color(0xA0A0B8);
			FAINT = new Color(0x6C6C84);
			ACCENT = new Color(0xA38BFA);
			ACCENT_2 = new Color(0x55D6D2);
			OK = new Color(0x4ADE80);
			WARN = new Color(0xFBBF24);
			BAD = new Color(0xF87171);
			HERO_A = new Color(0x191C3C);
			HERO_B = new Color(0x0D353B);
		} else {
			BG = new Color(0xF4F4F9);
			SURFACE = new Color(0xFFFFFF);
			SURFACE_2 = new Color(0xEEEEF5);
			BORDER = new Color(0xE0E0EA);
			SIDEBAR = new Color(0xEAEAF2);
			TEXT = new Color(0x15151D);
			MUTED = new Color(0x5C5C72);
			FAINT = new Color(0x8C8CA2);
			ACCENT = new Color(0x6B55E8);
			ACCENT_2 = new Color(0x2196D8);
			OK = new Color(0x16A34A);
			WARN = new Color(0xD97706);
			BAD = new Color(0xDC2626);
			HERO_A = new Color(0x6B55E8);
			HERO_B = new Color(0x2196D8);
		}
		FlatLaf.setGlobalExtraDefaults(Map.of("@accentColor", hex(ACCENT), "@background", hex(BG), "@foreground", hex(TEXT)));
		if (dark) FlatDarkLaf.setup();
		else FlatLightLaf.setup();
		UIManager.put("Button.arc", 18);
		UIManager.put("Component.arc", 14);
		UIManager.put("TextComponent.arc", 10);
		UIManager.put("CheckBox.arc", 6);
		UIManager.put("ProgressBar.arc", 999);
		UIManager.put("Component.focusWidth", 1);
		UIManager.put("Component.innerFocusWidth", 0);
		UIManager.put("ScrollBar.thumbArc", 999);
		UIManager.put("ScrollBar.thumbInsets", new java.awt.Insets(2, 2, 2, 2));
		UIManager.put("ScrollBar.width", 10);
		UIManager.put("ScrollBar.showButtons", false);
		UIManager.put("ScrollPane.smoothScrolling", true);
		UIManager.put("TitlePane.unifiedBackground", true);
		UIManager.put("TitlePane.background", SIDEBAR);
		UIManager.put("TitlePane.inactiveBackground", SIDEBAR);
		UIManager.put("Panel.background", BG);
		UIManager.put("Button.background", SURFACE_2);
		UIManager.put("Button.borderColor", BORDER);
		UIManager.put("TextField.background", SURFACE_2);
		UIManager.put("ComboBox.background", SURFACE_2);
		UIManager.put("Spinner.background", SURFACE_2);
		UIManager.put("List.background", SURFACE);
		UIManager.put("TextArea.background", SURFACE);
		UIManager.put("TextPane.background", SURFACE);
		UIManager.put("ToolTip.background", SURFACE_2);
		UIManager.put("ToolTip.foreground", TEXT);
		UIManager.put("Slider.trackWidth", 4);
		UIManager.put("Slider.thumbSize", new java.awt.Dimension(16, 16));
		FlatLaf.updateUI();
	}

	static String hex(Color c) {
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}

	static Font font(int style, float size) {
		Font base = UIManager.getFont("defaultFont");
		if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
		return base.deriveFont(style, size);
	}

	static Color alpha(Color c, int a) {
		return new Color(c.getRed(), c.getGreen(), c.getBlue(), a);
	}

	static Color mix(Color a, Color b, double t) {
		return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
				(int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
	}
}
