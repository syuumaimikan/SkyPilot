package dev.skypilot.launcher;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** Only one launcher window at a time (two would write the same files). */
public final class SingleInstance {
	private static FileChannel channel;
	private static FileLock lock;

	private SingleInstance() {
	}

	/** True when this is the only running launcher. */
	public static synchronized boolean acquire(Path launcherDir) {
		try {
			Files.createDirectories(launcherDir);
			channel = FileChannel.open(launcherDir.resolve("launcher.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			lock = channel.tryLock();
			return lock != null;
		} catch (IOException | java.nio.channels.OverlappingFileLockException e) {
			return false;
		}
	}
}
