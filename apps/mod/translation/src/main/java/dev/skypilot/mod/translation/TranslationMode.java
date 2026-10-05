package dev.skypilot.mod.translation;

/**
 * Which incoming chat lines are translated automatically. {@link #OFF} is the default;
 * {@link #MANUAL} only translates on request ({@code /skypilot translate ...}).
 */
public enum TranslationMode {
	OFF, MANUAL, NPC_ONLY, PARTY, GUILD, AUTO;

	public boolean automatic(Channel channel) {
		return switch (this) {
			case OFF, MANUAL -> false;
			case NPC_ONLY -> channel == Channel.NPC;
			case PARTY -> channel == Channel.PARTY;
			case GUILD -> channel == Channel.GUILD;
			case AUTO -> channel != Channel.SYSTEM;
		};
	}

	/** Modes that send chat text to the SkyPilot server automatically (need the disclosure). */
	public boolean sendsAutomatically() {
		return this != OFF && this != MANUAL;
	}
}
