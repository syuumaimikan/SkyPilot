package dev.skypilot.mod.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChannelClassifierTest {
	private static void check(String line, Channel ch, String sender, String body) {
		ChannelClassifier.Classified c = ChannelClassifier.classify(line);
		assertEquals(ch, c.channel(), line);
		assertEquals(sender, c.sender(), line);
		assertEquals(body, c.body(line), line);
	}

	@Test
	void hypixelChatFormats() {
		check("Party > [MVP+] Steve: anyone f7?", Channel.PARTY, "Steve", "anyone f7?");
		check("Co-op > Alex: sell the gems", Channel.PARTY, "Alex", "sell the gems");
		check("Guild > [VIP] Alex [Officer]: gg", Channel.GUILD, "Alex", "gg");
		check("Officer > Bob: meeting soon", Channel.GUILD, "Bob", "meeting soon");
		check("From [MVP++] Notch: hi there", Channel.PRIVATE, "Notch", "hi there");
		check("To Jeb_: thanks!", Channel.PRIVATE, "Jeb_", "thanks!");
		check("[NPC] Rhys: Welcome to the Forge!", Channel.NPC, "Rhys", "Welcome to the Forge!");
		check("[312] [MVP+] Steve: lf mage for m7", Channel.PLAYER, "Steve", "lf mage for m7");
		check("Steve: hello", Channel.PLAYER, "Steve", "hello");
		check("You earned 1,234 coins!", Channel.SYSTEM, null, "You earned 1,234 coins!");
		check("", Channel.SYSTEM, null, "");
	}

	/** Real formats from SkyHanni's PlayerChatManager patterns (beta, Sept 2026). */
	@Test
	void emblemsGuestsAndSystemLookalikes() {
		check("[312] ♲ [MVP+] Steve: anyone selling a hype", Channel.PLAYER, "Steve", "anyone selling a hype");
		check("[287] Ⓑ Alex: bingo card?", Channel.PLAYER, "Alex", "bingo card?");
		check("[105] ☀ [VIP] Bob: stranded gang", Channel.PLAYER, "Bob", "stranded gang");
		check("[✌] [MVP+] Steve: nice island", Channel.PLAYER, "Steve", "nice island");
		check("From [PIG+++] Technoblade: hi", Channel.PRIVATE, "Technoblade", "hi");
		check("From stash: You picked up 64 items", Channel.SYSTEM, null, "From stash: You picked up 64 items");
		check("Guild > [MVP++] Notch [GM]: welcome", Channel.GUILD, "Notch", "welcome");
		check("☠ Mana: 120/500", Channel.SYSTEM, null, "☠ Mana: 120/500");
	}
}
