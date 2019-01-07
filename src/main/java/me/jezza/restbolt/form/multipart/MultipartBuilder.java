package me.jezza.restbolt.form.multipart;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author Jezza
 */
public final class MultipartBuilder {
	private final Object[] parts;
	private final String boundary;

	public MultipartBuilder(int count) {
		this(count, generateBoundary());
	}

	public MultipartBuilder(int count, String boundary) {
		System.out.println("Count: " + count);
		parts = new Object[count + 1];
		this.boundary = boundary;
	}

	public String contentType() {
		return "multipart/form-data; boundary=".concat(boundary);
	}

	public String boundary() {
		return boundary;
	}

	private static final char[] FIELD_SEP = {':', ' '};
	private static final char[] CR_LF = {'\r', '\n'};
	private static final char[] TWO_DASHES = {'-', '-'};
	private static final String MIME = "Content-Disposition: form-data; name=\"";

	private StringBuilder writeFieldHeader(String name) {
		StringBuilder b = new StringBuilder(32);
		b.append(TWO_DASHES);
		b.append(boundary);
		b.append(CR_LF);

		b.append(MIME);
		b.append(name);
		b.append('"');

		return b;
	}

	private byte[] writeField(String name, Consumer<StringBuilder> content) {
		StringBuilder b = writeFieldHeader(name);
		b.append(CR_LF);
		b.append(CR_LF);

		content.accept(b);
		b.append(CR_LF);

		return b.toString().getBytes(StandardCharsets.UTF_8);
	}

	public void add(int index, String name, boolean value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, char value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, byte value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, short value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, int value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, float value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, long value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, double value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, Object[] value) {
		System.out.println(name + ':' + Arrays.toString(value));
	}

	public void add(int index, String name, Object value) {
		System.out.println(name + ':' + value);
	}

	public void add(int index, String name, String value) {
		System.out.println(name + ':' + value);
		parts[index] = writeField(name, b -> b.append(value));
	}

	public void add(int index, String name, Map<?, ?> value) {
		System.out.println(name + ':' + value);
	}

	public void add(int index, String name, List<?> value) {
		System.out.println(name + ':' + value);
	}

	public void add(int index, String name, File value) {
		System.out.println(name + ':' + value);
	}

	public void add(int index, String name, InputStream value) {
		System.out.println(name + ':' + value);
	}

	public BodyPublisher build() {
		System.out.println("Build");
		Object[] parts = this.parts;
		StringBuilder b = new StringBuilder();
		b.append(TWO_DASHES);
		b.append(boundary);
		b.append(TWO_DASHES);
		b.append(CR_LF);
		parts[parts.length - 1] = b.toString().getBytes(StandardCharsets.UTF_8);

		int count = 0;
		for (Object part : parts) {
			if (part instanceof byte[]) {
				count += ((byte[]) part).length;
			}
		}

		byte[] data = new byte[count];
		int index = 0;
		for (Object part : parts) {
			if (part instanceof byte[]) {
				byte[] array = (byte[]) part;
				int length = array.length;
				System.arraycopy(array, 0, data, index, length);
				index += length;
			}
		}
//		return BodyPublishers.ofByteArray(data);
		return BodyPublishers.ofInputStream(() -> new MultipartStream(parts));
	}

	private static final class MultipartStream extends InputStream {
		private final Object[] parts;

		private Object active;
		private int index;
		private int bufferIndex;

		public MultipartStream(Object[] parts) {
			this.parts = parts;
			active = nextPair();
		}

		private Object nextPair() {
			int index = this.index;
			Object[] parts = this.parts;
			if (index >= parts.length) {
				return null;
			}
			this.index++;
			return parts[index];
		}

		@Override
		public int read() throws IOException {
			// So the reason I throw an exception here, and don't use the code below is because I can't
			// communicate all of the states of the code with this method.
			// 
			// All of the information that I can return amounts to:
			// 
			// This read method returns a byte that was read or -1.
			// The other read method returns a int that was the number of bytes read.
			//
			// The mistake is that in the second method read method.
			// The one with the byte array can return a 0.

			throw new IOException("Cannot chunk input...");
//			byte[] single = this.single;
//			if (single == null) {
//				single = new byte[1];
//				this.single = single;
//			}
//			return read(single, 0, 1) != -1
//					? single[0]
//					: -1;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			Object active = this.active;
			if (active == null) {
				return -1;
			}
			int count;
			if (active instanceof byte[]) {
				byte[] data = (byte[]) active;
				int l = data.length;
				int src;
				if (l < len) {
					src = 0;
					count = l;
				} else {
					src = bufferIndex;
					if (l - len >= src) {
						count = l - src;
					} else {
						count = len;
						this.bufferIndex = src + len;
					}
				}
				System.arraycopy(data, src, b, off, count);
			} else if (active instanceof MultipartSegment) {
				count = ((MultipartSegment) active).read(b, off, len);
			} else {
				System.out.println("[ERROR] Unknown part type: " + active);
				return -1;
			}
			if (count == -1) {
				Object next = nextPair();
				this.active = next;
				bufferIndex = 0;
				return next != null
						? 0
						: -1;
			} else if (count != len) {
				this.active = nextPair();
				bufferIndex = 0;
			}
			return count;
		}

		@Override
		public void close() {
			active = null;
		}
	}

	public static String generateBoundary() {
		return "boundary";
	}
}
