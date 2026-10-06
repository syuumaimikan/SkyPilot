package dev.skypilot.launcher;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Extra Java arguments for the profile. Only tuning flags are accepted: anything that loads code
 * (agents, class paths, extra mods), runs commands (OnError…) or reads option files is refused,
 * and memory is set with the memory setting instead.
 */
public final class JvmArgs {
	private static final Pattern XX = Pattern.compile("-XX:([+-]?)([A-Za-z][A-Za-z0-9]*)(=[A-Za-z0-9._%:-]{1,64})?");
	private static final Pattern X = Pattern.compile("-X(ss|mn)[0-9]{1,6}[kKmMgG]?");
	private static final Pattern D = Pattern.compile("-D([a-zA-Z0-9_.]{1,64})=([A-Za-z0-9._:-]{0,64})");
	private static final Set<String> BLOCKED_XX = Set.of("onerror", "onoutofmemoryerror", "flags", "vmoptionsfile", "compilecommandfile", "compilecommand", "errorfile", "heapdumppath",
			"logfile", "jvmciclasspathappend", "startflightrecording", "flightrecorderoptions");
	/** Harmless properties only (fabric.* could add mods, java.* changes class loading). */
	private static final Set<String> ALLOWED_D = Set.of("file.encoding", "stdout.encoding", "stderr.encoding", "sun.stdout.encoding", "sun.stderr.encoding", "java.net.preferIPv4Stack",
			"sun.java2d.d3d", "sun.java2d.opengl");

	private JvmArgs() {
	}

	/** Problems with the text (empty = fine). */
	public static List<String> problems(String args) {
		List<String> out = new ArrayList<>();
		if (args == null || args.isBlank()) return out;
		if (args.length() > 600) out.add("too long");
		for (String a : args.strip().split("\\s+")) {
			if (a.startsWith("-Xmx") || a.startsWith("-Xms")) {
				out.add(a + ": use the memory setting");
				continue;
			}
			var xx = XX.matcher(a);
			if (xx.matches()) {
				if (BLOCKED_XX.contains(xx.group(2).toLowerCase(Locale.ROOT))) out.add(a + ": not allowed");
				continue;
			}
			if (X.matcher(a).matches()) continue;
			var d = D.matcher(a);
			if (d.matches()) {
				if (!ALLOWED_D.contains(d.group(1))) out.add(a + ": only encoding/network/rendering properties are allowed");
				continue;
			}
			out.add(a + ": not allowed");
		}
		return out;
	}

	public static String clean(String args) {
		return args == null ? "" : args.strip().replaceAll("\\s+", " ");
	}
}
