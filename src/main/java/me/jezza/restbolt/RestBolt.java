package me.jezza.restbolt;

import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import me.jezza.restbolt.interfaces.CUSTOM;
import me.jezza.restbolt.interfaces.GET;
import me.jezza.restbolt.interfaces.Header;
import me.jezza.restbolt.interfaces.Path;
import me.jezza.restbolt.interfaces.Query;

/**
 * @author Jezza
 */
public final class RestBolt {
	private static final Type OBJECT_TYPE = Type.getType(Object.class);
	private static final String OBJECT_INTERNAL = OBJECT_TYPE.getInternalName();

	private static final Type STRING_TYPE = Type.getType(String.class);
	private static final String STRING_INTERNAL = STRING_TYPE.getInternalName();
	private static final String STRING_DESCRIPTOR = STRING_TYPE.getDescriptor();

	private static final Type URI_TYPE = Type.getType(URI.class);
	private static final String URI_INTERNAL = URI_TYPE.getInternalName();
	private static final String URI_DESCRIPTOR = URI_TYPE.getDescriptor();

	private static final String CONSTRUCTOR_DESCRIPTOR = '(' + URI_DESCRIPTOR + ")V";

	private static final Type CLIENT_TYPE = Type.getType(HttpClient.class);
	private static final String CLIENT_INTERNAL = CLIENT_TYPE.getInternalName();
	private static final String CLIENT_DESCRIPTOR = CLIENT_TYPE.getDescriptor();

	private static final Type CLIENT_BUILDER_TYPE = Type.getType(HttpClient.Builder.class);
	private static final String CLIENT_BUILDER_INTERNAL = CLIENT_BUILDER_TYPE.getInternalName();
	private static final String CLIENT_BUILDER_DESCRIPTOR = CLIENT_BUILDER_TYPE.getDescriptor();

	private static final Type REQUEST_TYPE = Type.getType(HttpRequest.class);
	private static final String REQUEST_INTERNAL = REQUEST_TYPE.getInternalName();
	private static final String REQUEST_DESCRIPTOR = REQUEST_TYPE.getDescriptor();

	private static final Type REQUEST_BUILDER_TYPE = Type.getType(HttpRequest.Builder.class);
	private static final String REQUEST_BUILDER_INTERNAL = REQUEST_BUILDER_TYPE.getInternalName();
	private static final String REQUEST_BUILDER_DESCRIPTOR = REQUEST_BUILDER_TYPE.getDescriptor();

	private static final Type RESPONSE_TYPE = Type.getType(HttpResponse.class);
	private static final String RESPONSE_INTERNAL = RESPONSE_TYPE.getInternalName();
	private static final String RESPONSE_DESCRIPTOR = RESPONSE_TYPE.getDescriptor();

	private static final Type BODY_PUBLISHER_TYPE = Type.getType(BodyPublisher.class);
	private static final Type BODY_PUBLISHERS_TYPE = Type.getType(BodyPublishers.class);

	private static final String BODY_HANDLER_DESCRIPTOR = Type.getDescriptor(BodyHandler.class);

	private static final String URI_RESOLVE = '(' + STRING_DESCRIPTOR + ')' + URI_TYPE;
	private static final String REQUEST_NEW_BUILDER = Type.getMethodDescriptor(REQUEST_BUILDER_TYPE, URI_TYPE);

	public static <T> T bind(String uri, Class<T> type) {
		if (!type.isInterface()) {
			throw new IllegalStateException("Type (\"" + type.getName() + "\" not an interface.");
		}
		URI hostUri = URI.create(uri);

		String internalName = Type.getInternalName(type);
		String generatedName = internalName.concat("Proxy");

		ClassWriter writer = new ClassWriter(COMPUTE_FRAMES);

		writer.visit(Opcodes.V11, Modifier.PUBLIC | Modifier.FINAL, generatedName, null, OBJECT_INTERNAL, new String[]{internalName});

		// private final URI host;
		writer.visitField(Modifier.PRIVATE | Modifier.FINAL, "host", URI_DESCRIPTOR, null, null);

		// private volatile HttpClient client;
		writer.visitField(Modifier.PRIVATE | Modifier.VOLATILE, "client", CLIENT_DESCRIPTOR, null, null);

		// public Constructor(URI host);
		MethodVisitor constructor = writer.visitMethod(Modifier.PUBLIC, "<init>", CONSTRUCTOR_DESCRIPTOR, null, null);
		constructor.visitVarInsn(ALOAD, 0);
		constructor.visitMethodInsn(INVOKESPECIAL, OBJECT_INTERNAL, "<init>", "()V", false);
		constructor.visitVarInsn(ALOAD, 0);
		constructor.visitVarInsn(ALOAD, 1);
		constructor.visitFieldInsn(PUTFIELD, generatedName, "host", URI_DESCRIPTOR);
		constructor.visitInsn(RETURN);
		constructor.visitMaxs(0, 0);

		// private HttpClient client();
		buildClient(writer, generatedName);

		// all interface methods
		for (Method method : type.getDeclaredMethods()) {
			GET get = method.getDeclaredAnnotation(GET.class);
			if (get != null) {
//				long start = System.nanoTime();
				describe(writer, method, generatedName, "GET", get.value());
//				long end = System.nanoTime();
//				System.out.println((end - start) + " ns = " + method.getName() + Type.getMethodDescriptor(method));
				continue;
			}
			// POST, PUT, etc

			CUSTOM custom = method.getDeclaredAnnotation(CUSTOM.class);
			if (custom != null) {
//				long start = System.nanoTime();
				describe(writer, method, generatedName, custom.method(), custom.value());
//				long end = System.nanoTime();
//				System.out.println((end - start) + " ns = " + method.getName() + Type.getMethodDescriptor(method));
				continue;
			}
		}

		byte[] classData = writer.toByteArray();

		try {
			Files.write(Paths.get("C:\\Users\\Jezza\\Desktop\\JavaProjects\\rest-bolt\\" + generatedName.substring(generatedName.lastIndexOf('/') + 1) + ".class"), classData, StandardOpenOption.CREATE);
		} catch (IOException e) {
			e.printStackTrace();
		}

		Class<?> clazz;
		try {
			clazz = MethodHandles.lookup().defineClass(classData);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Failed to declare class", e);
		}

		Constructor<?> generatedConstructor;
		try {
			generatedConstructor = clazz.getDeclaredConstructor(URI.class);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException("Failed to locate constructor", e);
		}

		try {
			return (T) generatedConstructor.newInstance(hostUri);
		} catch (InstantiationException e) {
			throw new IllegalStateException("Failed to instantiate class", e);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Unable to access class", e);
		} catch (InvocationTargetException e) {
			throw new IllegalStateException("Failed to invoke constructor on generated class", e);
		}
	}

	// Type layout:
	// 00000000_00000000_00000000_00001111 = META_INFO
	// 00000000_00000000_00000000_11110000 = SORT_INFO
	// 11111111_00000000_00000000_00000000 = SLOT_INFO

	private static final int TYPE_SIZE = Integer.SIZE;

	private static final int UNUSED = 0;

	// META_INFO

	private static final int PATH = 0b1;
	private static final int QUERY = 0b10;
	private static final int HEADER = 0b100;
	private static final int RESERVED = 0b1000;

	private static final int META_MASK = 0b1111;

	// SORT_INFO

	private static final int BOOLEAN = 1;
	private static final int CHAR = 2;
	private static final int BYTE = 3;
	private static final int SHORT = 4;
	private static final int INT = 5;
	private static final int FLOAT = 6;
	private static final int LONG = 7;
	private static final int DOUBLE = 8;
	private static final int ARRAY = 9;
	private static final int OBJECT = 10;
	private static final int STRING = 11;

	private static final int SORT_SHIFT = TYPE_SIZE - Integer.numberOfLeadingZeros(META_MASK);
	private static final int SORT_MASK = 0b1111 << SORT_SHIFT;

	// SLOT_INFO

	private static final int SLOT_MASK = 0b11111111_00000000_00000000_00000000;
	private static final int SLOT_SHIFT = Integer.numberOfTrailingZeros(SLOT_MASK);

	/**
	 * The descriptors of the primitive types.
	 */
	private static final String PRIMITIVE_DESCRIPTORS = "VZCBSIFJD";

	private static int determineSort(Class<?> type) {
		if (!type.isPrimitive()) {
			if (type.isArray()) {
				return ARRAY;
			}
			if (type == String.class) {
				return STRING;
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

	private static final String COMPLETABLE_FUTURE = "java.util.concurrent.CompletableFuture";
	private static final String HTTP_RESPONSE = "java.net.http.HttpResponse";

	private static void describe(ClassWriter writer, Method method, String generatedName, String httpMethod, String path) {
		if (path.charAt(0) != '/') {
			throw new IllegalStateException("Path must start with a '/'");
		}

		boolean async; // true if the method is expecting the CompletableFuture that shall result from the call.
		boolean response; // true if the method is expecting the HttpResponse itself, and not just the value.
		java.lang.reflect.Type responseType; // == null ? discarding
		{
			var returnType = method.getGenericReturnType();
			if (returnType instanceof ParameterizedType) {
				ParameterizedType parameterisedType = (ParameterizedType) returnType;
				var raw = parameterisedType.getRawType();
				if (raw == CompletableFuture.class) {
					async = true;
					response = false;

					// This should always have at least one type parameter.
					java.lang.reflect.Type[] arguments = parameterisedType.getActualTypeArguments();
					if (arguments.length != 1) {
						throw new AssertionError();
					}
					java.lang.reflect.Type argument = arguments[0];
					String name = argument.getTypeName();
					if (name.equals("?")) {
						responseType = null;
					} else if (name.startsWith(HTTP_RESPONSE)) {
						if (!(argument instanceof ParameterizedType)) {
							throw new AssertionError();
						}
						java.lang.reflect.Type[] responseArguments = ((ParameterizedType) argument).getActualTypeArguments();
						if (responseArguments.length != 1) {
							throw new AssertionError();
						}
						responseType = responseArguments[0];
					} else {
						// Check if the generic actually returns back a HttpResponse, as that's what the client returns, and the person writing the interface
						// could have easily forgot and just wrote something like "CompletableFuture<String>" instead of "CompletableFuture<HttpResponse<String>>".
						throw new IllegalStateException("[ERROR] Return type must be of CompletableFuture<HttpResponse<_>> on \"" + method + "\".");
					}
				} else if (raw == HttpResponse.class) {
					async = false;
					response = true;
					java.lang.reflect.Type[] responseArguments = parameterisedType.getActualTypeArguments();
					if (responseArguments.length != 1) {
						throw new AssertionError();
					}
					responseType = responseArguments[0];
					String name = responseType.getTypeName();
					if (name.equals("?") || name.equals("java.lang.Void")) {
						responseType = null;
					}
				} else {
					// This is just some parameterised type, like Map or List.
					async = false;
					response = false;
					responseType = returnType;
				}
			} else {
				async = false;
				response = false;
				responseType = returnType != Void.TYPE
						? returnType
						: null;
			}

			Class<?>[] types = method.getExceptionTypes();
			boolean found = false;
			for (Class<?> exception : types) {
				found = exception == SyncException.class;
				if (found) {
					if (async) {
						System.out.println("[WARN] " + SyncException.class.getName() + " will never be thrown from \"" + method + "\".");
					}
					found = true;
					break;
				}
			}
			if (!found && !async) {
				throw new IllegalStateException("[ERROR] " + SyncException.class.getName() + " is not declared on \"" + method + "\".");
			}
		}

		Class<?>[] exceptionTypes = method.getExceptionTypes();
		String[] exceptions = new String[exceptionTypes.length];
		for (int i = 0, l = exceptionTypes.length; i < l; i++) {
			exceptions[i] = Type.getInternalName(exceptionTypes[i]);
		}

		int count = method.getParameterCount();

		// Some String that is relevant based on the meta type.
		String[] names = new String[count];
		int[] types = new int[count];

		Parameter[] params = method.getParameters();
		int slotMax = 1;
		for (int i = 0, l = params.length; i < l; i++) {
			Parameter parameter = params[i];
			if (parameter.getType() == Map.class || parameter.getType() == List.class) {
				continue;
			}
			int type = UNUSED;
			for (Annotation annotation : parameter.getAnnotations()) {
				if (annotation.annotationType() == Path.class) {
					Path segment = (Path) annotation;
					names[i] = segment.value();
					type |= PATH;
				} else if (annotation.annotationType() == Query.class) {
					Query query = (Query) annotation;
					names[i] = query.value();
					type |= QUERY;
				} else if (annotation.annotationType() == Header.class) {
					Header header = (Header) annotation;
					names[i] = header.value();
					type |= HEADER;
					if (!header.data().isEmpty()) {
						throw new IllegalStateException("Dynamic @Header with static \"data\" in " + method.getName() + Type.getMethodDescriptor(method));
					}
				}
			}
			int sort = determineSort(parameter.getType());
			types[i] = slotMax << SLOT_SHIFT | sort << SORT_SHIFT | type;
			slotMax += size(sort);
//			System.out.println(Integer.toBinaryString(types[i]));
		}

//		System.out.println("Parameters: " + Arrays.toString(names));
//		System.out.println("Types: " + Arrays.toString(types));

		String name = method.getName();
		String descriptor = Type.getMethodDescriptor(method);
		MethodVisitor impl = writer.visitMethod(Modifier.PUBLIC | Modifier.FINAL, name, descriptor, null, exceptions);

		// Load the host field for later. (I don't really use locals all that much...)
		impl.visitVarInsn(ALOAD, 0);
		impl.visitFieldInsn(GETFIELD, generatedName, "host", URI_DESCRIPTOR);

		// Construct the StringBuilder that's used to concat the path segments (static and dynamic).
		impl.visitTypeInsn(NEW, Type.getInternalName(StringBuilder.class));
		impl.visitInsn(DUP);
		impl.visitLdcInsn(32);
		impl.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "(I)V", false);

		// Start segmenting the path into sections that are either static or dynamic.
		//
		// 1) Static segments are sections that can be inserted directly into the path without issue.
		// 		(Technically, it is possible to insert a check here to see if the static segment is a valid segment.).
		//
		// 2) Dynamic segments are sections that are inserted based on the parameters.
		// They're dealt with by just encoding them inline and appending them. (eg, .append(URLEncoder.encode(value, UTF_8))).
		//
		//
		// The code itself locates an opening brace ('{'), if any, finds a matching closing brace ('}'), anything outside of those braces is static, everything inside is dynamic.
		//
		// "/users/{id}/name"
		//
		// static: ["/users/", "/name"]
		// dynamic: ["id"]
		boolean hasQuerySegment = false;
		int start = 0;
		int end;
		while (true) {
			end = path.indexOf('{', start);
			if (end == -1) {
				break;
			}

			// static segment.
			String segment = path.substring(start, end);
			if (!hasQuerySegment) {
				hasQuerySegment = segment.indexOf('?') != -1;
			}
//			System.out.println("[STA] " + segment);
			impl.visitLdcInsn(segment);
			impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

			start = end + 1;

			end = path.indexOf('}', start);
			if (end == -1) {
				throw new IllegalStateException("Unclosed '{' at position " + start);
			}

			// dynamic segment.
			String param = path.substring(start, end);
//			System.out.println("[DYN] " + param);
			boolean found = false;
			for (int i = 0, l = types.length; i < l; i++) {
				int type = types[i];
				if ((type & META_MASK) == PATH) {
					String knownSegment = names[i];
					if (knownSegment != null && knownSegment.equals(param)) {
						int sort = (type & SORT_MASK) >> SORT_SHIFT;
						int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

						impl.visitVarInsn(op(ILOAD, sort), slot);
						if (sort == STRING) {
							impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
						} else if (sort == OBJECT) {
							impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
						} else if (sort == ARRAY) {
							throw new IllegalStateException("Not yet supported: ARRAY");
						} else {
							char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
							impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(" + character + ")Ljava/lang/StringBuilder;", false);
						}
						found = true;
						break;
					}
				}
			}
			if (!found) {
				throw new IllegalStateException("Unknown path segment \"" + param + "\" on \"" + method + "\".");
			}

			start = end + 1;
		}

		// static segment (Just the remaining stuff)
		if (start != path.length()) {
			String segment = path.substring(start);
			if (!hasQuerySegment) {
				hasQuerySegment = segment.indexOf('?') != -1;
			}
//			System.out.println("[STA] " + segment);
			impl.visitLdcInsn(segment);
			impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
		}

		// Insert the query parameters.
		// While we were checking and adding the path segments, we also checked if there was a '?', if there was, the path contains a hardcoded query segment
		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == QUERY) {
				char queryChar;
				if (!hasQuerySegment) {
					hasQuerySegment = true;
					queryChar = '?';
				} else {
					queryChar = '&';
				}

				String query = names[i];
//				System.out.println("query: \"" + query + "\".");
				impl.visitLdcInsn(queryChar + URLEncoder.encode(query, StandardCharsets.UTF_8) + '=');
				impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

				impl.visitVarInsn(op(ILOAD, sort), slot);
				if (sort == ARRAY || sort == STRING || sort == OBJECT) {
					if (sort != STRING) {
						throw new IllegalStateException("Support not yet added :: " + sort);
//						impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(Integer.class), "toString", "(I)Ljava/lang/String;", false);
					}
					impl.visitFieldInsn(GETSTATIC, Type.getInternalName(StandardCharsets.class), "UTF_8", Type.getDescriptor(Charset.class));
					impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(URLEncoder.class), "encode", "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;", false);
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				} else {
					char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
					impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(" + character + ")Ljava/lang/StringBuilder;", false);
				}
			}
		}

		// We've finished building the URI, and now we just convert it to a string, and resolve it against the host.
		impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString", "()Ljava/lang/String;", false);
		impl.visitMethodInsn(INVOKEVIRTUAL, URI_INTERNAL, "resolve", "(Ljava/lang/String;)".concat(URI_DESCRIPTOR), false);

		// %request%
		impl.visitMethodInsn(INVOKESTATIC, REQUEST_INTERNAL, "newBuilder", '(' + URI_DESCRIPTOR + ')' + REQUEST_BUILDER_DESCRIPTOR, false);
		// %request%

		// Static headers
		Header[] headers = method.getDeclaredAnnotationsByType(Header.class);
		for (Header header : headers) {
			impl.visitLdcInsn(header.value());
			impl.visitLdcInsn(header.data());
			impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "header", '(' + STRING_DESCRIPTOR + STRING_DESCRIPTOR + ')' + REQUEST_BUILDER_DESCRIPTOR, true);
		}

		// Dynamic headers
		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == HEADER) {
				impl.visitLdcInsn(names[i]);

				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

				impl.visitVarInsn(op(ILOAD, sort), slot);
				if (sort == ARRAY) {
					throw new IllegalStateException("Support not yet added :: " + sort);
				}
				if (sort != STRING) {
					char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
					impl.visitMethodInsn(INVOKESTATIC, STRING_INTERNAL, "valueOf", "(" + character + ')' + STRING_DESCRIPTOR, false);
				}
				impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "header", '(' + STRING_DESCRIPTOR + STRING_DESCRIPTOR + ')' + REQUEST_BUILDER_DESCRIPTOR, true);
			}
		}

		// Load http method and prepare publisher.
		impl.visitLdcInsn(httpMethod);

		// Do publisher shit...
		if (httpMethod.equals("GET")) {
			impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyPublishers.class), "noBody", "()" + BODY_PUBLISHER_TYPE.getDescriptor(), false);
		} else {
			throw new IllegalStateException("Unsupported http method (\"" + httpMethod + "\")");
		}


		// Finalise and construct the request
		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "method", '(' + STRING_DESCRIPTOR + BODY_PUBLISHER_TYPE.getDescriptor() + ')' + REQUEST_BUILDER_DESCRIPTOR, true);
		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "build", "()" + REQUEST_DESCRIPTOR, true);
		impl.visitVarInsn(ASTORE, slotMax + 1);

		// Do handler shit...
		if (responseType == String.class) {
			impl.visitFieldInsn(GETSTATIC, Type.getInternalName(StandardCharsets.class), "UTF_8", Type.getDescriptor(Charset.class));
			impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyHandlers.class), "ofString", '(' + Type.getDescriptor(Charset.class) + ')' + BODY_HANDLER_DESCRIPTOR, false);
		} else if (responseType != null) {
			throw new IllegalStateException("Not yet supported: " + responseType.getTypeName());
		} else {
			impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyHandlers.class), "discarding", "()" + BODY_HANDLER_DESCRIPTOR, false);	
		}
		impl.visitVarInsn(ASTORE, slotMax + 2);

		impl.visitVarInsn(ALOAD, 0);
		impl.visitMethodInsn(INVOKESPECIAL, generatedName, "client", "()" + CLIENT_DESCRIPTOR, false);
		impl.visitVarInsn(ALOAD, slotMax + 1);
		impl.visitVarInsn(ALOAD, slotMax + 2);

		if (async) {
			impl.visitMethodInsn(INVOKEVIRTUAL, CLIENT_INTERNAL, "sendAsync", '(' + REQUEST_DESCRIPTOR + BODY_HANDLER_DESCRIPTOR + ')' + Type.getDescriptor(CompletableFuture.class), false);
			impl.visitInsn(ARETURN);
		} else {
			impl.visitMethodInsn(INVOKEVIRTUAL, CLIENT_INTERNAL, "send", '(' + REQUEST_DESCRIPTOR + BODY_HANDLER_DESCRIPTOR + ')' + RESPONSE_DESCRIPTOR, false);
			if (response) {
				impl.visitInsn(ARETURN);
			} else if (responseType != null) {
				impl.visitMethodInsn(INVOKEINTERFACE, RESPONSE_INTERNAL, "body", "()Ljava/lang/Object;", true);
				impl.visitTypeInsn(CHECKCAST, responseType.getTypeName().replace('.', '/'));
				impl.visitInsn(ARETURN);
			} else {
				impl.visitInsn(RETURN);
			}
		}
		impl.visitMaxs(0, 0);
	}

	private static int op(int opcode, int sort) {
//		System.out.println("opcode: " + opcode + ", sort: " + sort);
		if (opcode == Opcodes.IALOAD || opcode == Opcodes.IASTORE) {
			switch (sort) {
				case BOOLEAN:
				case BYTE:
					return opcode + (Opcodes.BALOAD - Opcodes.IALOAD);
				case CHAR:
					return opcode + (Opcodes.CALOAD - Opcodes.IALOAD);
				case SHORT:
					return opcode + (Opcodes.SALOAD - Opcodes.IALOAD);
				case INT:
					return opcode;
				case FLOAT:
					return opcode + (Opcodes.FALOAD - Opcodes.IALOAD);
				case LONG:
					return opcode + (Opcodes.LALOAD - Opcodes.IALOAD);
				case DOUBLE:
					return opcode + (Opcodes.DALOAD - Opcodes.IALOAD);
				case ARRAY:
				case STRING:
				case OBJECT:
					return opcode + (Opcodes.AALOAD - Opcodes.IALOAD);
				default:
					throw new AssertionError();
			}
		}
		switch (sort) {
//			case VOID:
//				if (opcode != Opcodes.IRETURN) {
//					throw new UnsupportedOperationException();
//				}
//				return Opcodes.RETURN;
			case BOOLEAN:
			case BYTE:
			case CHAR:
			case SHORT:
			case INT:
				return opcode;
			case FLOAT:
				return opcode + (Opcodes.FRETURN - IRETURN);
			case LONG:
				return opcode + (Opcodes.LRETURN - IRETURN);
			case DOUBLE:
				return opcode + (Opcodes.DRETURN - IRETURN);
			case ARRAY:
			case STRING:
			case OBJECT:
				if (opcode != Opcodes.ILOAD && opcode != Opcodes.ISTORE && opcode != IRETURN) {
					throw new UnsupportedOperationException();
				}
				return opcode + (Opcodes.ARETURN - IRETURN);
			default:
				throw new AssertionError();
		}
	}

	private static int size(int sort) {
		return sort == LONG || sort == DOUBLE
				? 2
				: 1;
	}

	private static void buildClient(ClassWriter writer, String generatedName) {
		//	private HttpClient client() {
		//		HttpClient client = this.client;
		//		if (client == null) {
		//			synchronized (this) {
		//				client = this.client;
		//				if (client == null) {
		//					client = HttpClient.newBuilder()
		//							.build();
		//					this.client = client;
		//				}
		//			}
		//		}
		//		return client;
		//	}
		MethodVisitor client = writer.visitMethod(Modifier.PRIVATE, "client", "()" + CLIENT_DESCRIPTOR, null, null);
		client.visitVarInsn(ALOAD, 0);
		client.visitFieldInsn(GETFIELD, generatedName, "client", CLIENT_DESCRIPTOR);
		client.visitVarInsn(ASTORE, 1);
		client.visitVarInsn(ALOAD, 1);
		Label quickExit = new Label();
		client.visitJumpInsn(IFNONNULL, quickExit);

		client.visitVarInsn(ALOAD, 0);
		client.visitInsn(MONITORENTER);
		Label monitorStart = new Label();
		client.visitLabel(monitorStart);

		client.visitVarInsn(ALOAD, 0);
		client.visitFieldInsn(GETFIELD, generatedName, "client", CLIENT_DESCRIPTOR);
		client.visitVarInsn(ASTORE, 1);

		client.visitVarInsn(ALOAD, 1);
		Label slowExit = new Label();
		client.visitJumpInsn(IFNONNULL, slowExit);

		client.visitMethodInsn(INVOKESTATIC, CLIENT_INTERNAL, "newBuilder", "()" + CLIENT_BUILDER_DESCRIPTOR, false);
		client.visitMethodInsn(INVOKEINTERFACE, CLIENT_BUILDER_INTERNAL, "build", "()" + CLIENT_DESCRIPTOR, true);

		client.visitVarInsn(ASTORE, 1);
		client.visitVarInsn(ALOAD, 0);
		client.visitVarInsn(ALOAD, 1);
		client.visitFieldInsn(PUTFIELD, generatedName, "client", CLIENT_DESCRIPTOR);

		client.visitLabel(slowExit);

		client.visitVarInsn(ALOAD, 0);
		client.visitInsn(MONITOREXIT);
		Label monitorExit = new Label();
		client.visitLabel(monitorExit);

		client.visitLabel(quickExit);
		client.visitVarInsn(ALOAD, 1);
		client.visitInsn(ARETURN);

		Label handler = new Label();
		client.visitLabel(handler);
		client.visitVarInsn(ALOAD, 0);
		client.visitInsn(MONITOREXIT);
		client.visitInsn(ATHROW);

		client.visitTryCatchBlock(monitorStart, monitorExit, handler, null);

		client.visitMaxs(0, 0);
	}

	public static void main(String[] args) throws SyncException {
//		Runnable server = new Runnable() {
//			@Override
//			public void run() {
//				try (ServerSocket socket = new ServerSocket(23123)) {
//					try (Socket accepted = socket.accept()) {
//						InputStream in = accepted.getInputStream();
//						while (!accepted.isClosed()) {
//							System.out.print((char) in.read());
//						}
//						System.out.println("Closed");
//					}
//				} catch (IOException e) {
//					e.printStackTrace();
//				}
//			}
//		};
//		Thread thread = new Thread(server);
//		thread.setDaemon(true);
//		thread.start();

		Service service = bind("http://localhost:8080", Service.class);
//		service.pingSync0();
		int size = 100;
//		List<CompletableFuture<?>> futures = new ArrayList<>(size);
//		for (int i = 0; i < size; i++) {
//			futures.add(service.pingAsync());
//		}
//		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

//		String size = service.size();
//		System.out.println(size);
//		System.out.println(service.count());

		service.touch(123L);
		service.touch(456L);
	}

	public static final class ServiceImpl { // implements Service
		private final URI host;
		private volatile HttpClient client;

		ServiceImpl(URI host) {
			this.host = host;
		}

		private HttpClient client() {
			HttpClient client = this.client;
			if (client == null) {
				synchronized (this) {
					client = this.client;
					if (client == null) {
						this.client = client = HttpClient.newBuilder()
								.build();
					}
				}
			}
			return client;
		}

		//		@Override
		public String size() {
			URI path = host.resolve("/size");
			HttpRequest.Builder req = HttpRequest.newBuilder(path)
					.method("GET", BodyPublishers.noBody());
			BodyHandler<String> handler = BodyHandlers.ofString();
			try {
				return client().send(req.build(), handler)
						.body();
			} catch (IOException | InterruptedException e) {
				return null;
			}
		}

		//		@Override
		public String name0(String id, String sort, String auth) {
			StringBuilder b = new StringBuilder(32);
			// %path%
			b.append("/users/");
			b.append(id);
			b.append("/name");
			// %path%

			// %query%
			b.append('?');

			b.append("sort"); // = URLEncoder.encode("sort", StandardCharsets.UTF_8)
			if (sort != null) {
				b.append('=');
				b.append(URLEncoder.encode(sort, StandardCharsets.UTF_8));
			}
			// %query%

			URI uri = host.resolve(b.toString());

			// %request%
			Builder builder = HttpRequest.newBuilder(uri);
			// %request%

			// %header%
			builder.header("Accept", "application/vnd.github.v3.full+json");
			builder.header("User-Agent", "Retrofit-Sample-App");
			builder.header("Auth", auth);
			// %header%

			// %publisher%
			BodyPublisher publisher = BodyPublishers.noBody();
			// method == GET
			//   ? BodyPublishers.noBody()
			//   : !!!!!publishers.get(String);!!!!!

			// %publisher%

			builder.method("GET", publisher);

			HttpRequest request = builder.build();

			// %handler%
			BodyHandler<Void> handler = BodyHandlers.discarding();
			// %handler%

			// %return%sync
			try {
				HttpResponse<Void> response = client().send(request, handler);
//				return response;
			} catch (IOException | InterruptedException e) {
//				throw new SyncException(e);
			}
			// %return%?sync

			// %return%?async
			CompletableFuture<HttpResponse<Void>> future = client().sendAsync(request, handler);
//			return future;
			// %return%?async

			return null;
		}
	}

	public interface Service {
//		@GET("/size")
//		String size();

		@GET("/users/{user}/name")
		@Header(value = "Accept", data = "application/vnd.github.v3.full+json")
		@Header(value = "User-Agent", data = "Rest-Bolt-UserAgent")
		CompletableFuture<HttpResponse<String>> name(@Path("user") String id, @Query("sort") String sort, @Header("Auth") String auth);

		@GET("/touch/{id}")
		void touch(@Path("id") long id) throws SyncException;

		@GET("/ping")
		void ping(@Header("User-Agent") String userAgent) throws SyncException;

		@GET("/ping")
		CompletableFuture<?> pingAsync();

		@GET("/ping")
		void pingSync0() throws SyncException;

		@GET("/count")
		String count() throws SyncException;

		@GET("/ping")
		String pingSync1() throws SyncException;
//		HttpResponse<String> pingSync1() throws SyncException;

		@GET("/ping")
		HttpResponse<?> pingSync2() throws SyncException;

		@GET("/ping")
		HttpResponse<Void> pingSync3() throws SyncException;

		@GET("/asd")
		CompletableFuture<?> pingAsync2(@Query("cache") long test0, @Header("cache_updated") long test1);

		@GET("/ping?timeout=10")
		void test(@Header("*") Map<String, String> headers, @Query("test") Map<String, String> query, @Query("test") List<String> mQuery) throws SyncException;
	}
}
