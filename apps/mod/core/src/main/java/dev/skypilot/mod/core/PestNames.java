package dev.skypilot.mod.core;

import java.util.Optional;
import java.util.regex.Pattern;

/** Exact pest names in public mob labels; never matches traps, chat or ordinary animals. */
public final class PestNames {
    private static final Pattern LABEL = Pattern.compile("^(?:\\[Lv\\d+\\]\\s*)?(Beetle|Cricket|Earthworm|Field Mouse|Fly|Locust|Lunar Moth|Mite|Mosquito|Moth|Rat|Slug)(?:\\s+(?:[\\d,.]+[kKmMbB]?)(?:/[\\d,.]+[kKmMbB]?)?\\s*❤)?$");
    private PestNames() {}
    public static Optional<String> identify(String label) {
        if (label == null) return Optional.empty();
        var match = LABEL.matcher(Text.strip(label).strip());
        return match.matches() ? Optional.of(match.group(1)) : Optional.empty();
    }
}
