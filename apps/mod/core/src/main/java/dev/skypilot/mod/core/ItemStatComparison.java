package dev.skypilot.mod.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Compares only explicit English SkyBlock lore values; absent stats remain unknown. */
public final class ItemStatComparison {
    private static final List<String> NAMES = List.of("Damage", "Strength", "Crit Chance", "Crit Damage", "Health", "Defense", "Intelligence", "Speed",
            "Magic Find", "Pet Luck", "Mining Speed", "Mining Fortune", "Farming Fortune", "Foraging Fortune", "Sea Creature Chance", "Fishing Speed", "Vitality", "Ferocity");
    private static final Pattern VALUE = Pattern.compile("^\\s*(?:[^\\p{L}\\p{N}]*)([A-Za-z ]+):\\s*([+-]?[\\d,]+(?:\\.\\d+)?)\\s*(%?)(?:\\s*\\([^)]*\\))*\\s*$");
    public record Stat(double value, boolean percent) {}
    public record Delta(String name, double amount, boolean percent) {}
    private ItemStatComparison() {}
    public static Map<String, Stat> parse(List<String> lore) {
        var out = new LinkedHashMap<String, Stat>();
        for (String line : lore) {
            var match = VALUE.matcher(Text.strip(line));
            if (!match.matches()) continue;
            String name = match.group(1).strip();
            if (!NAMES.contains(name) || out.containsKey(name)) continue;
            try {
                double value = Double.parseDouble(match.group(2).replace(",", ""));
                if (Double.isFinite(value)) out.put(name, new Stat(value, "%".equals(match.group(3))));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }
    public static List<Delta> compare(List<String> candidate, List<String> equipped) {
        var a = parse(candidate); var b = parse(equipped);
        return NAMES.stream().filter(n -> a.containsKey(n) && b.containsKey(n) && a.get(n).percent() == b.get(n).percent())
                .map(n -> new Delta(n, a.get(n).value() - b.get(n).value(), a.get(n).percent())).filter(d -> Math.abs(d.amount()) > .0001).toList();
    }
}
