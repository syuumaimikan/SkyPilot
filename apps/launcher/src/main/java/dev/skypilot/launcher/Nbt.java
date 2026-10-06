package dev.skypilot.launcher;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal NBT reader/writer for {@code servers.dat}, which Minecraft 26.2 stores as uncompressed
 * NBT: a type byte, an empty name and the root compound ({@code NbtIo.read(Path)} /
 * {@code writeUnnamedTag}, checked in the 26.2 client). Every tag type round-trips unchanged, so
 * entries the launcher doesn't touch are kept exactly.
 */
public final class Nbt {
	/** A list keeps its element type, so empty lists are written back the same. */
	public record ListTag(byte type, List<Object> items) {
	}

	private static final int MAX_DEPTH = 64;

	private Nbt() {
	}

	public static Map<String, Object> read(InputStream in) throws IOException {
		DataInputStream data = new DataInputStream(in);
		byte type = data.readByte();
		if (type != 10) throw new IOException("not an NBT compound");
		data.readUTF();
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) payload(data, type, 0);
		return root;
	}

	public static void write(Map<String, Object> root, OutputStream out) throws IOException {
		DataOutputStream data = new DataOutputStream(out);
		data.writeByte(10);
		data.writeUTF("");
		writePayload(data, (byte) 10, root);
		data.flush();
	}

	private static Object payload(DataInput in, byte type, int depth) throws IOException {
		if (depth > MAX_DEPTH) throw new IOException("NBT nested too deeply");
		switch (type) {
			case 1:
				return in.readByte();
			case 2:
				return in.readShort();
			case 3:
				return in.readInt();
			case 4:
				return in.readLong();
			case 5:
				return in.readFloat();
			case 6:
				return in.readDouble();
			case 7: {
				int n = length(in.readInt());
				byte[] b = new byte[n];
				in.readFully(b);
				return b;
			}
			case 8:
				return in.readUTF();
			case 9: {
				byte el = in.readByte();
				int n = length(in.readInt());
				List<Object> items = new ArrayList<>(Math.min(n, 1024));
				for (int i = 0; i < n; i++) items.add(payload(in, el, depth + 1));
				return new ListTag(el, items);
			}
			case 10: {
				Map<String, Object> m = new LinkedHashMap<>();
				while (true) {
					byte t = in.readByte();
					if (t == 0) return m;
					String name = in.readUTF();
					m.put(name, new Tagged(t, payload(in, t, depth + 1)));
				}
			}
			case 11: {
				int n = length(in.readInt());
				int[] a = new int[n];
				for (int i = 0; i < n; i++) a[i] = in.readInt();
				return a;
			}
			case 12: {
				int n = length(in.readInt());
				long[] a = new long[n];
				for (int i = 0; i < n; i++) a[i] = in.readLong();
				return a;
			}
			default:
				throw new IOException("unknown NBT tag " + type);
		}
	}

	private static int length(int n) throws IOException {
		if (n < 0 || n > 10_000_000) throw new IOException("bad NBT length");
		return n;
	}

	/** A compound entry with its tag type (so numbers keep their exact width). */
	public record Tagged(byte type, Object value) {
	}

	private static void writePayload(DataOutput out, byte type, Object v) throws IOException {
		switch (type) {
			case 1 -> out.writeByte((Byte) v);
			case 2 -> out.writeShort((Short) v);
			case 3 -> out.writeInt((Integer) v);
			case 4 -> out.writeLong((Long) v);
			case 5 -> out.writeFloat((Float) v);
			case 6 -> out.writeDouble((Double) v);
			case 7 -> {
				byte[] b = (byte[]) v;
				out.writeInt(b.length);
				out.write(b);
			}
			case 8 -> out.writeUTF((String) v);
			case 9 -> {
				ListTag l = (ListTag) v;
				out.writeByte(l.items().isEmpty() && l.type() == 0 ? 0 : l.type());
				out.writeInt(l.items().size());
				for (Object o : l.items()) writePayload(out, l.type(), o);
			}
			case 10 -> {
				@SuppressWarnings("unchecked")
				Map<String, Object> m = (Map<String, Object>) v;
				for (Map.Entry<String, Object> e : m.entrySet()) {
					Tagged t = (Tagged) e.getValue();
					out.writeByte(t.type());
					out.writeUTF(e.getKey());
					writePayload(out, t.type(), t.value());
				}
				out.writeByte(0);
			}
			case 11 -> {
				int[] a = (int[]) v;
				out.writeInt(a.length);
				for (int i : a) out.writeInt(i);
			}
			case 12 -> {
				long[] a = (long[]) v;
				out.writeInt(a.length);
				for (long i : a) out.writeLong(i);
			}
			default -> throw new IOException("unknown NBT tag " + type);
		}
	}

	public static Tagged string(String s) {
		return new Tagged((byte) 8, s);
	}
}
