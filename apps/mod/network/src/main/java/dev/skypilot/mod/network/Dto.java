package dev.skypilot.mod.network;

import java.util.List;

/** JSON shapes exchanged with the SkyPilot API (mirrors packages/protocol mod_api / auth / translation). */
public final class Dto {
	private Dto() {
	}

	public record HudLine(String label, String value) {
	}

	public record HudContextBlock(String context, String title, List<HudLine> lines) {
	}

	public record HudState(boolean linked, String username, String goalTitle, Integer stepIndex, Integer stepCount, String stepTitle, Double stepProgress,
			Double stepCurrent, Double stepTarget, String stepUnit, String nextTitle, Double estimatedCost, Double overallProgress, HudContextBlock context,
			List<String> actions, long updatedAtMs, boolean stale, String message) {
	}

	public record PriceResponse(String skyblockId, String name, Double estimatedValue, Double median, Double lowestBin, Double bazaarBuy, Double bazaarSell,
			Double salesPerDay, Double confidence, String confidenceLabel, String priceKey, Long ageSeconds) {
	}

	public record TextResponse(String title, List<HudLine> lines) {
	}

	public record DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, String verificationUriComplete, long expiresIn, int interval) {
	}

	public record DeviceTokenResponse(String status, String accessToken, String scope) {
	}

	public record TranslateResponse(String translated, String provider, boolean cached, boolean unchanged) {
	}

	public record DictionaryEntry(String term, String translation, String category) {
	}

	public record TranslationDictionary(String version, List<String> protectedTerms, List<DictionaryEntry> phrases) {
	}
}
