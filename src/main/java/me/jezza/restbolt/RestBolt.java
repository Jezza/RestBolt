package me.jezza.restbolt;

import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.ASTORE;
import static org.objectweb.asm.Opcodes.ATHROW;
import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.DRETURN;
import static org.objectweb.asm.Opcodes.DUP;
import static org.objectweb.asm.Opcodes.FRETURN;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;
import static org.objectweb.asm.Opcodes.IFNONNULL;
import static org.objectweb.asm.Opcodes.ILOAD;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static org.objectweb.asm.Opcodes.INVOKESTATIC;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static org.objectweb.asm.Opcodes.IRETURN;
import static org.objectweb.asm.Opcodes.ISTORE;
import static org.objectweb.asm.Opcodes.LRETURN;
import static org.objectweb.asm.Opcodes.MONITORENTER;
import static org.objectweb.asm.Opcodes.MONITOREXIT;
import static org.objectweb.asm.Opcodes.NEW;
import static org.objectweb.asm.Opcodes.PUTFIELD;
import static org.objectweb.asm.Opcodes.RETURN;
import static org.objectweb.asm.Opcodes.SWAP;
import static org.objectweb.asm.Opcodes.V11;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
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
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.jezza.restbolt.annotations.Body;
import me.jezza.restbolt.annotations.CUSTOM;
import me.jezza.restbolt.annotations.DELETE;
import me.jezza.restbolt.annotations.GET;
import me.jezza.restbolt.annotations.HEAD;
import me.jezza.restbolt.annotations.Header;
import me.jezza.restbolt.annotations.OPTIONS;
import me.jezza.restbolt.annotations.POST;
import me.jezza.restbolt.annotations.PUT;
import me.jezza.restbolt.annotations.Path;
import me.jezza.restbolt.annotations.Query;
import me.jezza.restbolt.annotations.TRACE;

/**
 * @author Jezza
 */
public final class RestBolt {
	private static final Logger log = LoggerFactory.getLogger(RestBolt.class);
	private static final String DEBUG_OUTPUT_FOLDER = RestBolt.class.getName().concat(".outputGenClass");

	public static final MethodType PUBLISHER_FACTORY_SIGNATURE = methodType(void.class, Method.class, MethodVisitor.class, String[].class, int[].class, int.class);

	public static final String PUBLISHER_NO_BODY = "me.jezza.restbolt.RestBolt.buildNoBody";
	public static final String PUBLISHER_URL_ENCODED = "me.jezza.restbolt.RestBolt.buildURLEncoded";

	private static final String OBJECT_INTERNAL = "java/lang/Object";

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

	private static final Type PUBLISHER_TYPE = Type.getType(BodyPublisher.class);
	private static final String PUBLISHER_INTERNAL = PUBLISHER_TYPE.getInternalName();
	private static final String PUBLISHER_DESCRIPTOR = PUBLISHER_TYPE.getDescriptor();

	private static final Type HANDLER_TYPE = Type.getType(BodyHandler.class);
	private static final String HANDLER_INTERNAL = HANDLER_TYPE.getInternalName();
	private static final String HANDLER_DESCRIPTOR = HANDLER_TYPE.getDescriptor();

	private RestBolt() {
		throw new IllegalStateException();
	}

	public static <T> T bind(String uri, Class<T> type, Lookup lookup) {
		URI hostUri = URI.create(uri);
		MethodHandle constructor = createImpl(type, lookup);
		try {
			return (T) constructor.invoke(hostUri);
		} catch (Throwable t) {
			throw new IllegalStateException("Failed to instantiate class", t);
		}
	}

	public static <T> Binder<T> binder(Class<T> type, Lookup lookup) {
		MethodHandle constructor = createImpl(type, lookup);
		return new Binder<>(constructor);
	}

	private static MethodHandle createImpl(Class<?> type, Lookup lookup) {
		if (!type.isInterface()) {
			throw new IllegalStateException("Type (\"" + type.getName() + "\" not an interface.");
		}
		String internalName = Type.getInternalName(type);
		String generatedName = internalName.concat("Proxy");

		ClassWriter writer = new ClassWriter(COMPUTE_FRAMES);

		writer.visit(V11, Modifier.PUBLIC | Modifier.FINAL, generatedName, null, OBJECT_INTERNAL, new String[]{internalName});

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
		long total = 0;
		for (Method method : type.getDeclaredMethods()) {
			String verb = null;
			String path = null;
			String publisher = null;

			Annotation[] annotations = method.getDeclaredAnnotations();
			for (Annotation annotation : annotations) {
				Class<? extends Annotation> annotationType = annotation.annotationType();
				if (annotationType == GET.class) {
					GET get = ((GET) annotation);
					verb = "GET";
					path = get.value();
					publisher = PUBLISHER_NO_BODY;
					break;
				}
				if (annotationType == HEAD.class) {
					HEAD head = ((HEAD) annotation);
					verb = "HEAD";
					path = head.value();
					publisher = PUBLISHER_NO_BODY;
					break;
				}
				if (annotationType == POST.class) {
					POST post = ((POST) annotation);
					verb = "POST";
					path = post.value();
					publisher = post.publisher();
					break;
				}
				if (annotationType == PUT.class) {
					PUT put = ((PUT) annotation);
					verb = "PUT";
					path = put.value();
					publisher = put.publisher();
					break;
				}
				if (annotationType == DELETE.class) {
					DELETE delete = ((DELETE) annotation);
					verb = "DELETE";
					path = delete.value();
					publisher = PUBLISHER_NO_BODY;
					break;
				}
				if (annotationType == OPTIONS.class) {
					OPTIONS options = ((OPTIONS) annotation);
					verb = "OPTIONS";
					path = options.value();
					publisher = options.publisher();
					break;
				}
				if (annotationType == TRACE.class) {
					TRACE trace = ((TRACE) annotation);
					verb = "TRACE";
					path = trace.value();
					publisher = PUBLISHER_NO_BODY;
					break;
				}
				if (annotationType == CUSTOM.class) {
					CUSTOM custom = ((CUSTOM) annotation);
					verb = custom.verb();
					path = custom.value();
					publisher = custom.publisher();
					break;
				}
			}
			if (verb == null) {
				continue;
			}

			long start = System.nanoTime();
			writeMethod(lookup, writer, method, generatedName, verb, path, publisher);
			long end = System.nanoTime();
			long time = end - start;
			total += time;
			log.info(time + " ns = " + method.getName() + Type.getMethodDescriptor(method));
		}
		log.info(total + " ns = " + type.getName());

		byte[] classData = writer.toByteArray();

		String folder = System.getProperty(DEBUG_OUTPUT_FOLDER);
		if (folder != null) {
			String fileName = folder + generatedName.substring(generatedName.lastIndexOf('/') + 1) + ".class";
			try {
				Files.write(Paths.get(fileName), classData, StandardOpenOption.CREATE);
			} catch (IOException e) {
				log.warn("Failed to write generated class data to \"" + fileName + "\".", e);
			}
		}

		Class<?> clazz;
		try {
			clazz = lookup.defineClass(classData);
		} catch (IllegalAccessException e) {
			throw new IllegalStateException("Failed to declare class", e);
		}

		try {
			return lookup.findConstructor(clazz, methodType(void.class, URI.class));
		} catch (NoSuchMethodException | IllegalAccessException e) {
			// Shouldn't happen as we build the class with a constructor with this exact signature...
			throw new IllegalStateException("Failed to locate constructor", e);
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
	private static final int BODY = 0b1000;

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

	private static final String HTTP_RESPONSE = "java.net.http.HttpResponse";
	private static final String VOID = "java.lang.Void";

	private static void writeMethod(Lookup lookup, ClassWriter writer, Method method, String generatedName, String verb, String path, String publisher) {
		if (path.charAt(0) != '/') {
			// Yeah, we could patch it up for them, but I'd rather enforce a consistent style then have to read a mixture of the two in source...
			throw new IllegalStateException("Path must start with a '/'");
		}

		boolean async; // true, if the method is expecting the CompletableFuture that shall result from the call.
		boolean response; // true, if the method is expecting the HttpResponse itself, and not just the value.
		java.lang.reflect.Type responseType; // == null ? discarding
		{
			// Here be dragons...
			// This isn't pretty, and I do want to pull it into a method, but I return three parameters, and I can't be fucked making a POJO for it, so Ima just leave it for now...
			// I also don't want to put two booleans and a fucking pointer on the heap, just so I can return it...
			// I want tuples... Rust as ruined me...
			// Interestingly, this scope is actually the part that makes it too complex for IntelliJ to analyse.
			// @TODO Jezza - 28 Nov. 2018: Yes, move this into another method _nicely_...
			var returnType = method.getGenericReturnType();
			if (returnType instanceof ParameterizedType) {
				// Some parameterised type. (eg, Map<String, String>, List<String>, HttpResponse<?>, CompletableFuture<HttpResponse<String>>)
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
					// If the type itself is a wildcard, then they couldn't give two shits about the response type, so we're just gonna discard the body and not care.
					if (name.equals("?")) {
						responseType = null;
					} else if (name.startsWith(HTTP_RESPONSE)) {
						if (!(argument instanceof ParameterizedType)) {
							throw new AssertionError(HTTP_RESPONSE + " not parameterised.");
						}
						java.lang.reflect.Type[] responseArguments = ((ParameterizedType) argument).getActualTypeArguments();
						if (responseArguments.length != 1) {
							throw new AssertionError();
						}
						responseType = responseArguments[0];
					} else {
						// Check if the generic actually returns back a HttpResponse, as that's what the client returns, and the person writing the interface
						// could have easily forgot and just wrote something like "CompletableFuture<String>" instead of "CompletableFuture<HttpResponse<String>>".
						String methodDescription = method.getName() + Type.getMethodDescriptor(method);
						throw new IllegalStateException("[ERROR] Return type must be of CompletableFuture<HttpResponse<_>> on \"" + methodDescription + "\".");
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
					if (name.equals("?") || name.equals(VOID)) {
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

			boolean found = false;
			for (Class<?> exceptionType : method.getExceptionTypes()) {
				if (exceptionType == SyncException.class) {
					if (async) {
						String exception = SyncException.class.getName();
						String methodDescription = method.getName() + Type.getMethodDescriptor(method);
						log.warn("[WARN] " + exception + " will never be thrown from \"" + methodDescription + "\".");
					}
					found = true;
					break;
				}
			}
			if (!found && !async) {
				String exception = SyncException.class.getName();
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				throw new IllegalStateException("[ERROR] " + exception + " is not declared on \"" + methodDescription + "\".");
			}
			if ("HEAD".equals(verb) && responseType != null) {
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				throw new IllegalStateException("[ERROR] A \"HEAD\" request will never return a body with \"" + methodDescription + "\".");
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
		int max = 1;
		for (int i = 0, l = params.length; i < l; i++) {
			Parameter parameter = params[i];
			if (parameter.getType() == Map.class || parameter.getType() == List.class || parameter.getType().isArray()) {
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				String parameterType = parameter.getType().getName();
				log.warn("[WARN] Not yet supported: " + methodDescription + " => " + parameterType);
				continue;
			}
			int type = UNUSED;
			for (Annotation annotation : parameter.getAnnotations()) {
				Class<? extends Annotation> annotationType = annotation.annotationType();
				if (annotationType == Path.class) {
					Path segment = (Path) annotation;
					names[i] = segment.value();
					type |= PATH;
				} else if (annotationType == Query.class) {
					Query query = (Query) annotation;
					names[i] = query.value();
					type |= QUERY;
				} else if (annotationType == Header.class) {
					Header header = (Header) annotation;
					names[i] = header.value();
					type |= HEADER;
					if (!header.data().isEmpty()) {
						String methodDescription = method.getName() + Type.getMethodDescriptor(method);
						throw new IllegalStateException("Dynamic @Header with static \"data\" in " + methodDescription);
					}
				} else if (annotationType == Body.class) {
					Body body = (Body) annotation;
					names[i] = body.value();
					type |= BODY;
				}
			}
			int sort = determineSort(parameter.getType());
			types[i] = max << SLOT_SHIFT | sort << SORT_SHIFT | type;
			max += size(sort);
		}

		if (log.isDebugEnabled()) {
			log.debug("Parameters: " + Arrays.toString(names));
			log.debug("Types: " + Arrays.toString(types));
		}

		MethodVisitor impl = writer.visitMethod(Modifier.PUBLIC | Modifier.FINAL, method.getName(), Type.getMethodDescriptor(method), null, exceptions);

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
			if (log.isDebugEnabled()) {
				log.debug("[STA] " + segment);
			}
			impl.visitLdcInsn(segment);
			impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

			start = end + 1;

			end = path.indexOf('}', start);
			if (end == -1) {
				throw new IllegalStateException("Unclosed '{' at position " + start);
			}

			// dynamic segment.
			String param = path.substring(start, end);
			if (log.isDebugEnabled()) {
				log.debug("[DYN] " + param);
			}
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
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				throw new IllegalStateException("Unknown path segment \"" + param + "\" on \"" + methodDescription + "\".");
			}

			start = end + 1;
		}

		// static segment (Just the remaining stuff)
		if (start != path.length()) {
			String segment = path.substring(start);
			if (!hasQuerySegment) {
				hasQuerySegment = segment.indexOf('?') != -1;
			}
			if (log.isDebugEnabled()) {
				log.debug("[STA] " + segment);
			}
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
				if (log.isDebugEnabled()) {
					log.debug("query: \"" + query + "\".");
				}
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

		// The stack currently looks like this:
		//
		// [builder]
		//
		// After this call, the stack should have a publisher sitting on top.
		//
		// [builder, publisher]
		//
		// Any other state is undefined behaviour. (As of the time of writing this, it will crash. The builder itself is the receiver, so if you fuck with that... Welp, your own fault.)

		{
			// Pull apart the publisher string into the component parts:
			String owner;
			String methodName;

			int methodStart = publisher.lastIndexOf('.');
			owner = publisher.substring(0, methodStart).replace('/', '.');
			methodName = publisher.substring(methodStart + 1);

			try {
				// @MAYBE Jezza - 27 Nov. 2018: I think I've forgotten something here with module access...
				Class<?> target = lookup.findClass(owner);
				Lookup bypass = MethodHandles.privateLookupIn(target, lookup);
				MethodHandle handle = bypass.findStatic(target, methodName, PUBLISHER_FACTORY_SIGNATURE);
				handle.invokeExact(method, impl, names, types, max);
			} catch (Throwable t) {
				throw new IllegalStateException(t);
			}
		}

		// At this point, there should be a builder and a publisher sitting on the stack:
		//
		// [builder, publisher]
		//
		// We need to load the HTTP verb/method so we can call the #method method on the builder.
		// This defines the HTTP verb/method for the request.
		// But it takes the parameter first and the publisher second, so we load it up and then swap them.

		// [builder, publisher, verb]
		impl.visitLdcInsn(verb);

		// [builder, verb, publisher]
		impl.visitInsn(SWAP);

		// Finalise and construct the request.
		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "method", '(' + STRING_DESCRIPTOR + PUBLISHER_DESCRIPTOR + ')' + REQUEST_BUILDER_DESCRIPTOR, true);
		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "build", "()" + REQUEST_DESCRIPTOR, true);
		impl.visitVarInsn(ASTORE, max + 1);

		// Do handler shit...
		if (responseType == String.class) {
			impl.visitFieldInsn(GETSTATIC, Type.getInternalName(StandardCharsets.class), "UTF_8", Type.getDescriptor(Charset.class));
			impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyHandlers.class), "ofString", '(' + Type.getDescriptor(Charset.class) + ')' + HANDLER_DESCRIPTOR, false);
		} else if (responseType != null) {
			throw new IllegalStateException("Not yet supported: " + responseType.getTypeName());
		} else {
			impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyHandlers.class), "discarding", "()" + HANDLER_DESCRIPTOR, false);
		}
		impl.visitVarInsn(ASTORE, max + 2);

		impl.visitVarInsn(ALOAD, 0);
		impl.visitMethodInsn(INVOKESPECIAL, generatedName, "client", "()" + CLIENT_DESCRIPTOR, false);
		impl.visitVarInsn(ALOAD, max + 1);
		impl.visitVarInsn(ALOAD, max + 2);

		if (async) {
			impl.visitMethodInsn(INVOKEVIRTUAL, CLIENT_INTERNAL, "sendAsync", '(' + REQUEST_DESCRIPTOR + HANDLER_DESCRIPTOR + ')' + Type.getDescriptor(CompletableFuture.class), false);
			impl.visitInsn(ARETURN);
		} else {
			impl.visitMethodInsn(INVOKEVIRTUAL, CLIENT_INTERNAL, "send", '(' + REQUEST_DESCRIPTOR + HANDLER_DESCRIPTOR + ')' + RESPONSE_DESCRIPTOR, false);
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

	private static void buildNoBody(Method method, MethodVisitor impl, String[] names, int[] types, int max) {
		impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyPublishers.class), "noBody", "()" + PUBLISHER_DESCRIPTOR, false);
	}

	private static void buildURLEncoded(Method method, MethodVisitor impl, String[] names, int[] types, int max) {
		// [Builder]

		impl.visitInsn(DUP);
		impl.visitLdcInsn("Content-Type");
		impl.visitLdcInsn("application/x-www-form-urlencoded");

		impl.visitMethodInsn(INVOKEINTERFACE, REQUEST_BUILDER_INTERNAL, "header", "(Ljava/lang/String;Ljava/lang/String;)" + REQUEST_BUILDER_DESCRIPTOR, true);

		// new StringBuilder(32)
		impl.visitTypeInsn(NEW, Type.getInternalName(StringBuilder.class));
		impl.visitInsn(DUP);
		impl.visitLdcInsn(32);
		impl.visitMethodInsn(INVOKESPECIAL, Type.getInternalName(StringBuilder.class), "<init>", "(I)V", false);
		// [Builder, StringBuilder]

		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == BODY) {
				// We abuse that form body parsers split on ampersands. (That way we don't have to track which body part is the last...)
				impl.visitLdcInsn('&' + URLEncoder.encode(names[i], StandardCharsets.UTF_8) + '=');
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

		// [Builder, StringBuilder]
		impl.visitMethodInsn(INVOKEVIRTUAL, Type.getInternalName(StringBuilder.class), "toString", "()Ljava/lang/String;", false);
		// [Builder, String]

		impl.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BodyPublishers.class), "ofString", "(Ljava/lang/String;)" + PUBLISHER_DESCRIPTOR, false);
		// [Builder, Publisher]
	}
}
