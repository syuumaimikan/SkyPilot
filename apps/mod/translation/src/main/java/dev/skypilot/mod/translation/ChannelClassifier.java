package dev.skypilot.mod.translation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits a plain chat line into its prefix (channel tag, ranks, sender name) and message body.
 *
 * <p>Hypixel formats, e.g. {@code "Party > [MVP+] Steve: anyone f7?"},
 * {@code "Guild > Steve [Officer]: gg"}, {@code "From [VIP] Alex: hi"},
 * {@code "[NPC] Rhys: Welcome!"}, {@code "[312] [MVP+] Steve: lf mage"}, and with a profile
 * emblem after the SkyBlock level: {@code "[312] ♲ [MVP+] Steve: hi"} (Ironman ♲, Bingo Ⓑ,
 * Stranded ☀). Checked against SkyHanni's chat patterns (Sept 2026).
 */
public final class ChannelClassifier {
	public record Classified(Channel channel, int bodyStart, String sender) {
		public String body(String line) {
			return line.substring(bodyStart);
		}
	}

	/** Bracketed tags (SkyBlock level, rank, island guest {@code [✌]}), each optionally followed by
	 * an emblem symbol; a bare symbol at the start of a line (e.g. "☠") is not a player prefix. */
	private static final String RANKS = "(?:\\[[^\\]]{1,20}\\] ?(?:[^\\s\\w\\[\\]:]{1,2} )?)*";
	private static final String NAME = "([A-Za-z0-9_]{1,16})";
	private static final String SUFFIX = "(?: \\[[^\\]]{1,20}\\])?";
	private static final Pattern PARTY = Pattern.compile("^(?:Party|Co-op) > " + RANKS + NAME + SUFFIX + ": ");
	private static final Pattern GUILD = Pattern.compile("^(?:Guild|Officer) > " + RANKS + NAME + SUFFIX + ": ");
	/** {@code "From stash: …"} is a system message, not a player. */
	private static final Pattern PRIVATE = Pattern.compile("^(?!From stash: )(?:From|To) " + RANKS + NAME + ": ");
	private static final Pattern NPC = Pattern.compile("^\\[NPC\\] ([^:]{1,32}): ");
	private static final Pattern PLAYER = Pattern.compile("^" + RANKS + NAME + SUFFIX + ": ");

	private ChannelClassifier() {
	}

	public static Classified classify(String line) {
		if (line == null || line.isEmpty()) return new Classified(Channel.SYSTEM, 0, null);
		Matcher m;
		if ((m = PARTY.matcher(line)).find()) return new Classified(Channel.PARTY, m.end(), m.group(1));
		if ((m = GUILD.matcher(line)).find()) return new Classified(Channel.GUILD, m.end(), m.group(1));
		if ((m = PRIVATE.matcher(line)).find()) return new Classified(Channel.PRIVATE, m.end(), m.group(1));
		if ((m = NPC.matcher(line)).find()) return new Classified(Channel.NPC, m.end(), m.group(1));
		if ((m = PLAYER.matcher(line)).find()) return new Classified(Channel.PLAYER, m.end(), m.group(1));
		return new Classified(Channel.SYSTEM, 0, null);
	}
}
