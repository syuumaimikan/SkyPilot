package dev.skypilot.launcher;

import java.lang.management.ManagementFactory;

/** This computer's memory, for the memory recommendation. */
public final class SystemInfo {
	private SystemInfo() {
	}

	/** Physical memory in MB, or 0 when unknown. */
	public static long totalMemoryMb() {
		try {
			if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
				return os.getTotalMemorySize() / (1024 * 1024);
			}
		} catch (Throwable e) {
			// not available on this runtime
		}
		return 0;
	}

	/**
	 * Memory to give Minecraft: enough for SkyBlock with a typical set of mods, while leaving the
	 * rest of the computer room to breathe.
	 */
	public static int recommendedMb(long totalMb) {
		if (totalMb <= 0) return 4096;
		if (totalMb <= 6 * 1024) return 2048;
		if (totalMb <= 8 * 1024) return 3072;
		if (totalMb <= 12 * 1024) return 4096;
		if (totalMb <= 16 * 1024) return 6144;
		return 8192;
	}

	/** More than 3/4 of the computer's memory leaves too little for Windows and the browser. */
	public static boolean tooMuch(int memoryMb, long totalMb) {
		return totalMb > 0 && memoryMb > totalMb * 3 / 4;
	}

	public static String os() {
		return System.getProperty("os.name", "?") + " " + System.getProperty("os.version", "") + " (" + System.getProperty("os.arch", "") + ")";
	}

	public static String java() {
		return System.getProperty("java.version", "?") + " " + System.getProperty("java.vendor", "");
	}
}
