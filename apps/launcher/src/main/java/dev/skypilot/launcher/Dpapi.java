package dev.skypilot.launcher;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

/**
 * Windows Data Protection (DPAPI, {@code CryptProtectData}): encrypts a secret with a key that
 * only this Windows user on this PC can use. The saved Microsoft sign-in is stored this way;
 * copying the file to another account or PC makes it useless. Called through Java's foreign
 * function API (no extra libraries).
 */
public final class Dpapi {
	/** CRYPTPROTECT_UI_FORBIDDEN: never show a prompt. */
	private static final int UI_FORBIDDEN = 0x1;
	private static final byte[] ENTROPY = "SkyPilot Launcher account".getBytes(StandardCharsets.UTF_8);
	/** typedef struct { DWORD cbData; BYTE *pbData; } DATA_BLOB; */
	private static final MemoryLayout BLOB = MemoryLayout.structLayout(JAVA_INT.withName("cbData"), MemoryLayout.paddingLayout(4), ADDRESS.withName("pbData"));

	private Dpapi() {
	}

	public static boolean available() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") && "64".equals(System.getProperty("sun.arch.data.model"));
	}

	public static byte[] protect(byte[] data) {
		return call("CryptProtectData", data);
	}

	public static byte[] unprotect(byte[] data) {
		return call("CryptUnprotectData", data);
	}

	private static byte[] call(String function, byte[] data) {
		if (!available()) throw new IllegalStateException("Windows Data Protection is not available");
		Linker linker = Linker.nativeLinker();
		try (Arena arena = Arena.ofConfined()) {
			SymbolLookup crypt32 = SymbolLookup.libraryLookup("crypt32", arena);
			SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", arena);
			// BOOL CryptProtectData(DATA_BLOB*, LPCWSTR, DATA_BLOB*, PVOID, PROMPTSTRUCT*, DWORD, DATA_BLOB*)
			// BOOL CryptUnprotectData(DATA_BLOB*, LPWSTR*, DATA_BLOB*, PVOID, PROMPTSTRUCT*, DWORD, DATA_BLOB*)
			MethodHandle fn = linker.downcallHandle(crypt32.find(function).orElseThrow(),
					FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
			MethodHandle localFree = linker.downcallHandle(kernel32.find("LocalFree").orElseThrow(), FunctionDescriptor.of(ADDRESS, ADDRESS));
			MemorySegment in = blob(arena, data);
			MemorySegment entropy = blob(arena, ENTROPY);
			MemorySegment out = arena.allocate(BLOB);
			int ok = (int) fn.invokeExact(in, MemorySegment.NULL, entropy, MemorySegment.NULL, MemorySegment.NULL, UI_FORBIDDEN, out);
			if (ok == 0) throw new IllegalStateException(function + " failed");
			int len = out.get(JAVA_INT, 0);
			MemorySegment p = out.get(ADDRESS, 8).reinterpret(len);
			try {
				return p.toArray(JAVA_BYTE);
			} finally {
				MemorySegment ignored = (MemorySegment) localFree.invokeExact(p);
			}
		} catch (IllegalStateException e) {
			throw e;
		} catch (Throwable t) {
			throw new IllegalStateException(function + " failed", t);
		}
	}

	private static MemorySegment blob(Arena arena, byte[] data) {
		MemorySegment bytes = arena.allocate(Math.max(1, data.length));
		MemorySegment.copy(data, 0, bytes, JAVA_BYTE, 0, data.length);
		MemorySegment b = arena.allocate(BLOB);
		b.set(JAVA_INT, 0, data.length);
		b.set(ADDRESS, 8, bytes);
		return b;
	}
}
