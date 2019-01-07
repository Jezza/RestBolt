package me.jezza.restbolt;

import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LRETURN;

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Type;

/**
 * @author Jezza
 */
public final class Internals {
	public static final String OBJECT_INTERNAL = "java/lang/Object";
	public static final String EXCEPTION_INTERNAL = Type.getInternalName(SyncException.class);

	private static final Type STRING_TYPE = Type.getType(String.class);
	public static final String STRING_INTERNAL = STRING_TYPE.getInternalName();
	public static final String STRING_DESCRIPTOR = STRING_TYPE.getDescriptor();

	private static final Type URI_TYPE = Type.getType(URI.class);
	public static final String URI_INTERNAL = URI_TYPE.getInternalName();
	public static final String URI_DESCRIPTOR = URI_TYPE.getDescriptor();

	public static final String CONSTRUCTOR_DESCRIPTOR = '(' + URI_DESCRIPTOR + ")V";

	private static final Type CLIENT_TYPE = Type.getType(HttpClient.class);
	public static final String CLIENT_INTERNAL = CLIENT_TYPE.getInternalName();
	public static final String CLIENT_DESCRIPTOR = CLIENT_TYPE.getDescriptor();

	private static final Type CLIENT_BUILDER_TYPE = Type.getType(HttpClient.Builder.class);
	public static final String CLIENT_BUILDER_INTERNAL = CLIENT_BUILDER_TYPE.getInternalName();
	public static final String CLIENT_BUILDER_DESCRIPTOR = CLIENT_BUILDER_TYPE.getDescriptor();

	private static final Type REQUEST_TYPE = Type.getType(HttpRequest.class);
	public static final String REQUEST_INTERNAL = REQUEST_TYPE.getInternalName();
	public static final String REQUEST_DESCRIPTOR = REQUEST_TYPE.getDescriptor();

	private static final Type REQUEST_BUILDER_TYPE = Type.getType(HttpRequest.Builder.class);
	public static final String REQUEST_BUILDER_INTERNAL = REQUEST_BUILDER_TYPE.getInternalName();
	public static final String REQUEST_BUILDER_DESCRIPTOR = REQUEST_BUILDER_TYPE.getDescriptor();

	private static final Type RESPONSE_TYPE = Type.getType(HttpResponse.class);
	public static final String RESPONSE_INTERNAL = RESPONSE_TYPE.getInternalName();
	public static final String RESPONSE_DESCRIPTOR = RESPONSE_TYPE.getDescriptor();

	private static final Type PUBLISHER_TYPE = Type.getType(BodyPublisher.class);
	public static final String PUBLISHER_INTERNAL = PUBLISHER_TYPE.getInternalName();
	public static final String PUBLISHER_DESCRIPTOR = PUBLISHER_TYPE.getDescriptor();

	private static final Type HANDLER_TYPE = Type.getType(BodyHandler.class);
	public static final String HANDLER_INTERNAL = HANDLER_TYPE.getInternalName();
	public static final String HANDLER_DESCRIPTOR = HANDLER_TYPE.getDescriptor();

	public static final String STANDARD_CHARSETS_INTERNAL = Type.getInternalName(StandardCharsets.class);

	private static final Type CHARSET_TYPE = Type.getType(Charset.class);
	public static final String CHARSET_INTERNAL = CHARSET_TYPE.getInternalName();
	public static final String CHARSET_DESCRIPTOR = CHARSET_TYPE.getDescriptor();

	public static final String URL_ENCODER_INTERNAL = Type.getInternalName(URLEncoder.class);
	public static final String STRINGBUILDER_INTERNAL = Type.getInternalName(StringBuilder.class);

	// Type layout:
	// 00000000_00000000_00000000_00001111 = META_INFO
	// 00000000_00000000_00000000_11110000 = SORT_INFO
	// 11111111_00000000_00000000_00000000 = SLOT_INFO

	public static final int TYPE_SIZE = Integer.SIZE;

	public static final int UNUSED = 0;

	// META_INFO

	public static final int PATH = 0b1;
	public static final int QUERY = 0b10;
	public static final int HEADER = 0b100;
	public static final int BODY = 0b1000;

	public static final int META_MASK = 0b1111;

	// SORT_INFO

	public static final int BOOLEAN = 1;
	public static final int CHAR = 2;
	public static final int BYTE = 3;
	public static final int SHORT = 4;
	public static final int INT = 5;
	public static final int FLOAT = 6;
	public static final int LONG = 7;
	public static final int DOUBLE = 8;
	public static final int ARRAY = 9;
	public static final int OBJECT = 10;
	public static final int STRING = 11;
	public static final int MAP = 12;
	public static final int LIST = 13;
	public static final int FILE = 14;
	public static final int INPUT_STREAM = 15;

	public static final int SORT_SHIFT = TYPE_SIZE - Integer.numberOfLeadingZeros(META_MASK);
	public static final int SORT_MASK = 0b1111 << SORT_SHIFT;

	// SLOT_INFO

	public static final int SLOT_MASK = 0b11111111_00000000_00000000_00000000;
	public static final int SLOT_SHIFT = Integer.numberOfTrailingZeros(SLOT_MASK);

	/**
	 * The descriptors of the primitive types.
	 */
	public static final String PRIMITIVE_DESCRIPTORS = "VZCBSIFJD";

	private Internals() {
		throw new IllegalStateException();
	}

	public static int determineSort(Class<?> type) {
		if (!type.isPrimitive()) {
			if (type.isArray()) {
				return ARRAY;
			}
			if (type == String.class) {
				return STRING;
			}
			if (Map.class.isAssignableFrom(type)) {
				return MAP;
			}
			if (List.class.isAssignableFrom(type)) {
				return LIST;
			}
			if (File.class.isAssignableFrom(type)) {
				return FILE;
			}
			if (InputStream.class.isAssignableFrom(type)) {
				return INPUT_STREAM;
			}
			return OBJECT;
		}
		if (type == Boolean.TYPE) {
			return BOOLEAN;
		}
		if (type == Character.TYPE) {
			return CHAR;
		}
		if (type == Byte.TYPE) {
			return BYTE;
		}
		if (type == Short.TYPE) {
			return SHORT;
		}
		if (type == Integer.TYPE) {
			return INT;
		}
		if (type == Float.TYPE) {
			return FLOAT;
		}
		if (type == Long.TYPE) {
			return LONG;
		}
		if (type == Double.TYPE) {
			return DOUBLE;
		}
		throw new AssertionError();
	}

	public static int op(int opcode, int sort) {
//		System.out.println("opcode: " + opcode + ", sort: " + sort);
//		if (opcode == IALOAD || opcode == IASTORE) {
//			switch (sort) {
//				case BOOLEAN:
//				case BYTE:
//					return opcode + (BALOAD - IALOAD);
//				case CHAR:
//					return opcode + (CALOAD - IALOAD);
//				case SHORT:
//					return opcode + (SALOAD - IALOAD);
//				case INT:
//					return opcode;
//				case FLOAT:
//					return opcode + (FALOAD - IALOAD);
//				case LONG:
//					return opcode + (LALOAD - IALOAD);
//				case DOUBLE:
//					return opcode + (DALOAD - IALOAD);
//				case ARRAY:
//				case MAP:
//				case LIST:
//				case STRING:
//				case OBJECT:
//					return opcode + (AALOAD - IALOAD);
//				default:
//					throw new AssertionError();
//			}
//		}
		switch (sort) {
//			case VOID:
//				if (opcode != IRETURN) {
//					throw new UnsupportedOperationException();
//				}
//				return RETURN;
			case BOOLEAN:
			case BYTE:
			case CHAR:
			case SHORT:
			case INT:
				return opcode;
			case FLOAT:
				return opcode + (FRETURN - IRETURN);
			case LONG:
				return opcode + (LRETURN - IRETURN);
			case DOUBLE:
				return opcode + (DRETURN - IRETURN);
			case ARRAY:
			case MAP:
			case LIST:
			case STRING:
			case OBJECT:
				if (opcode != ILOAD && opcode != ISTORE && opcode != IRETURN) {
					throw new UnsupportedOperationException();
				}
				return opcode + (ARETURN - IRETURN);
			default:
				throw new AssertionError();
		}
	}

	public static int size(int sort) {
		return sort == LONG || sort == DOUBLE
				? 2
				: 1;
	}
}
