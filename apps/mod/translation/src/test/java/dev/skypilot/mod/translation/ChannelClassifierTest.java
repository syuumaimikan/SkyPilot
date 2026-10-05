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
}
