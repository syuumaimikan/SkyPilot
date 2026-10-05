package dev.skypilot.mod.translation;

/**
 * Which incoming chat lines get a click-to-translate button. Nothing is sent anywhere until the
 * player clicks the button of a line.
 */
public final class ClickToTranslate {
	/** Longest line the server accepts for translation. */
	public static final int MAX_LENGTH = 600;

	private ClickToTranslate() {
	}

	/**
	 * True for single-line messages whose body (after the channel / rank / name prefix) has
	 * something to translate: at least a few letters and not already Japanese.
	 */
	public static boolean eligible(String plain) {
		if (plain == null || plain.isBlank() || plain.length() > MAX_LENGTH || plain.indexOf('\n') >= 0) return false;
		ChannelClassifier.Classified c = ChannelClassifier.classify(plain);
		return Protector.worthTranslating(c.body(plain));
	}
}
