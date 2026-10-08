package dev.skypilot.mod.core;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A Garden visitor's offer as shown in the lore of the offer item: the lines under
 * "Items Required:" ("Enchanted Hay Bale x2") and the copper under "Rewards:" ("+12 Copper").
 */
public record VisitorOffer(List<Need> needs, OptionalDouble copper, List<Need> rewards) {
	public record Need(String name, int amount) {
	}

	private static final Pattern NEED = Pattern.compile("^(.+?)(?:\\s+x\\s?([\\d,]+))?$");
	private static final Pattern COPPER = Pattern.compile("^\\+([\\d,]+) Copper$");

	/** Null when the lore has no "Items Required:" section. */
	public static VisitorOffer parse(List<String> lore) {
		List<Need> needs = new ArrayList<>();
		List<Need> rewards = new ArrayList<>();
		OptionalDouble copper = OptionalDouble.empty();
		boolean inNeeds = false;
		boolean inRewards = false;
		boolean found = false;
		for (String raw : lore) {
			String line = Text.strip(raw).strip();
			if (line.equals("Items Required:")) {
				inNeeds = true;
				inRewards = false;
				found = true;
				continue;
			}
			if (line.equals("Rewards:")) {
				inRewards = true;
				inNeeds = false;
				continue;
			}
			if (line.isEmpty()) {
				inNeeds = false;
				inRewards = false;
				continue;
			}
			if (inNeeds && needs.size() < 8) {
				Matcher m = NEED.matcher(line);
				if (m.matches()) {
					int amount = m.group(2) == null ? 1 : Integer.parseInt(m.group(2).replace(",", ""));
					needs.add(new Need(m.group(1).strip(), amount));
				}
			} else if (inRewards) {
				Matcher m = COPPER.matcher(line);
				if (m.matches()) copper = OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", "")));
				else if (!line.contains(" XP") && !line.contains("Copper") && !line.contains("Garden Experience") && !line.contains("Bits") && !line.contains("Sowdust")) {
					Matcher reward=Pattern.compile("^\\+([\\d,]+) (.+)$").matcher(line);
					if(reward.matches())try{rewards.add(new Need(reward.group(2).strip(),Integer.parseInt(reward.group(1).replace(",",""))));}catch(NumberFormatException ignored){}
				}
			}
		}
		return found && !needs.isEmpty() ? new VisitorOffer(needs, copper, List.copyOf(rewards)) : null;
	}
}
