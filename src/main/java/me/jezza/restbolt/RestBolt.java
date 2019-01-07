package me.jezza.restbolt;

import static java.lang.invoke.MethodType.methodType;
import static org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static org.objectweb.asm.Opcodes.*;
import static me.jezza.restbolt.Internals.*;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Predicate;

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
import me.jezza.restbolt.annotations.RestService;
import me.jezza.restbolt.annotations.TRACE;
import me.jezza.restbolt.form.multipart.Multipart;
import me.jezza.restbolt.form.UrlEncoded;

/**
 * @author Jezza
 */
public final class RestBolt {
	private static final Logger log = LoggerFactory.getLogger(RestBolt.class);
	private static final String DEBUG_OUTPUT_FOLDER = RestBolt.class.getName().concat(".outputGenClass");

	private static final MethodType PUBLISHER_FACTORY_SIGNATURE = methodType(void.class, Method.class, MethodVisitor.class, String[].class, int[].class, int.class);

	public static final String PUBLISHER_NO_BODY = "me.jezza.restbolt.RestBolt.buildNoBody";
	public static final String PUBLISHER_URL_ENCODED = UrlEncoded.PUBLISHER_PATH;
	public static final String PUBLISHER_MULTIPART = Multipart.PUBLISHER_PATH;

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

		// (public | private) HttpClient client();
		buildClient(writer, generatedName, RestService.class.isAssignableFrom(type));

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
				throw new IllegalStateException("Unknown method on interface: " + method.getName() + Type.getMethodDescriptor(method));
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
			java.nio.file.Path file = Paths.get(folder, generatedName.substring(generatedName.lastIndexOf('/') + 1) + ".class");
			log.info("Writing class file to \"" + file + "\".");
			try {
				Files.write(file, classData, StandardOpenOption.CREATE);
			} catch (IOException e) {
				log.warn("Failed to write generated class data to \"" + file + "\".", e);
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

	private static void writeMethod(Lookup lookup, ClassWriter writer, Method method, String generatedName, String verb, String path, String publisher) {
		if (path.charAt(0) != '/') {
			// Yeah, we could patch it up for them, but I'd rather enforce a consistent style then have to read a mixture of the two in source...
			throw new IllegalStateException("Path must start with a '/'");
		}

		MethodDetails inspection = MethodDetails.discover(method);
		boolean async = inspection.async;
		boolean response = inspection.response;
		java.lang.reflect.Type responseType = inspection.responseType;

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

		Parameter[] params = method.getParameters();
		int count = params.length;

		String[] names = new String[count];
		int[] types = new int[count];
		int max = 1; // Start at 1, because of the receiver ('this').

		for (int i = 0, l = params.length; i < l; i++) {
			Parameter parameter = params[i];
			int sort = determineSort(parameter.getType());
			if (sort == ARRAY || sort == MAP || sort == LIST || sort == FILE || sort == INPUT_STREAM) {
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				String parameterType = parameter.getType().getName();
				log.warn("[WARN] Not yet supported: " + methodDescription + " => " + parameterType);
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
			types[i] = max << SLOT_SHIFT | sort << SORT_SHIFT | type;
			max += size(sort);
		}

		if (log.isDebugEnabled()) {
			log.debug("Parameters: " + Arrays.toString(names));
			log.debug("Types: " + Arrays.toString(types));
		}

		MethodVisitor impl = writer.visitMethod(Modifier.PUBLIC | Modifier.FINAL, method.getName(), Type.getMethodDescriptor(method), null, null);

		// Load the host field for later. (I don't really use locals all that much...)
		impl.visitVarInsn(ALOAD, 0);
		impl.visitFieldInsn(GETFIELD, generatedName, "host", URI_DESCRIPTOR);

		// A bit of a quick and easy optimisation.
		// If the path contains no tokens, then we know it's a bit static block, and we can just load it all at once.
		// The only other thing standing in our way is the query parameters...
		boolean dynamic = path.indexOf('{') != -1;
		if (dynamic) {
			// Construct the StringBuilder that's used to concat the path segments (static and dynamic).
			impl.visitTypeInsn(NEW, STRINGBUILDER_INTERNAL);
			impl.visitInsn(DUP);
			impl.visitLdcInsn(32);
			impl.visitMethodInsn(INVOKESPECIAL, STRINGBUILDER_INTERNAL, "<init>", "(I)V", false);
		
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
			Consumer<String> statics = segment -> {
				if (log.isDebugEnabled()) {
					log.debug("[STA] \"" + segment + '"');
				}
				impl.visitLdcInsn(segment);
				impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
			};
			Consumer<String> dynamics = param -> {
				if (log.isDebugEnabled()) {
					log.debug("[DYN] \"" + param + '"');
				}
				for (int i = 0, l = types.length; i < l; i++) {
					int type = types[i];
					if ((type & META_MASK) == PATH) {
						String knownSegment = names[i];
						if (knownSegment != null && knownSegment.equals(param)) {
							int sort = (type & SORT_MASK) >> SORT_SHIFT;
							int slot = (type & SLOT_MASK) >> SLOT_SHIFT;
							if (sort == ARRAY || sort == MAP || sort == LIST || sort == FILE || sort == INPUT_STREAM) {
								throw new IllegalStateException("Not yet supported");
							}
		
							impl.visitVarInsn(op(ILOAD, sort), slot);
							if (sort == STRING) {
								impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
							} else if (sort == OBJECT) {
								impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;", false);
							} else {
								char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
								impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(" + character + ")Ljava/lang/StringBuilder;", false);
							}
							return;
						}
					}
				}
				String methodDescription = method.getName() + Type.getMethodDescriptor(method);
				throw new IllegalStateException("Unknown path segment \"" + param + "\" on \"" + methodDescription + "\".");
			};
			segment(path, statics, dynamics);
		}

		boolean firstQuerySegment = path.indexOf('?') == -1;

		// Insert the query parameters.
		// While we were checking and adding the path segments, we also checked if there was a '?', if there was, the path contains a hardcoded query segment
		for (int i = 0, l = types.length; i < l; i++) {
			int type = types[i];
			if ((type & META_MASK) == QUERY) {
				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

				if (sort == ARRAY || sort == MAP || sort == LIST || sort == OBJECT || sort == FILE || sort == INPUT_STREAM) {
					log.warn("Support not yet added :: " + sort);
					continue;
				}

				String query = names[i];
				if (log.isDebugEnabled()) {
					log.debug("query: \"" + query + "\".");
				}

				char queryChar;
				if (firstQuerySegment) {
					firstQuerySegment = false;
					queryChar = '?';
				} else {
					queryChar = '&';
				}

				if (!dynamic) {
					dynamic = true;

					// Welp, if the path was static, the rest of the url isn't, because we just found a query parameter.
					// So, we need to reconstruct all of the previous stuff we would have done, had we done it normally.
					// This means we need to put a StringBuilder on the stack, complete with the path.

					// Construct the StringBuilder that's used to concat the path segments (static and dynamic).
					impl.visitTypeInsn(NEW, STRINGBUILDER_INTERNAL);
					impl.visitInsn(DUP);
					impl.visitLdcInsn(32);
					impl.visitMethodInsn(INVOKESPECIAL, STRINGBUILDER_INTERNAL, "<init>", "(I)V", false);

					// We know all of the path is static at this point, so we can just dump it all into the StringBuilder in one go.
					// We also already know the first query name, as we're processing it right now.
					// So we smoosh the two into a constant, and load it all at once.
					impl.visitLdcInsn(path + queryChar + URLEncoder.encode(query, StandardCharsets.UTF_8) + '=');
				} else {
					impl.visitLdcInsn(queryChar + URLEncoder.encode(query, StandardCharsets.UTF_8) + '=');
				}
				impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);

				impl.visitVarInsn(op(ILOAD, sort), slot);
				if (sort == STRING) {
					impl.visitFieldInsn(GETSTATIC, STANDARD_CHARSETS_INTERNAL, "UTF_8", CHARSET_DESCRIPTOR);
					impl.visitMethodInsn(INVOKESTATIC, URL_ENCODER_INTERNAL, "encode", "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;", false);
					impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
				} else {
					char character = PRIMITIVE_DESCRIPTORS.charAt(sort);
					impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "append", "(" + character + ")Ljava/lang/StringBuilder;", false);
				}
			}
		}

		if (dynamic) {
			// We've finished building the URI, and now we just convert it to a string, and resolve it against the host.
			impl.visitMethodInsn(INVOKEVIRTUAL, STRINGBUILDER_INTERNAL, "toString", "()Ljava/lang/String;", false);
		} else {
			// If we've gotten here, then we're targeting a completely static url, with no query parameters.
			// That means we can just hard-load the path and save constructing a StringBuilder and all of that jazz...
			impl.visitLdcInsn(path);
		}
		impl.visitMethodInsn(INVOKEVIRTUAL, URI_INTERNAL, "resolve", "(Ljava/lang/String;)" + URI_DESCRIPTOR, false);

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
				int sort = (type & SORT_MASK) >> SORT_SHIFT;
				int slot = (type & SLOT_MASK) >> SLOT_SHIFT;

				if (sort == ARRAY || sort == MAP || sort == LIST || sort == OBJECT || sort == FILE || sort == INPUT_STREAM) {
					log.warn("Support not yet added :: " + sort);
					continue;
				}

				impl.visitLdcInsn(names[i]);
				impl.visitVarInsn(op(ILOAD, sort), slot);
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
			Label catchStart = new Label();
			impl.visitLabel(catchStart);
			impl.visitMethodInsn(INVOKEVIRTUAL, CLIENT_INTERNAL, "send", '(' + REQUEST_DESCRIPTOR + HANDLER_DESCRIPTOR + ')' + RESPONSE_DESCRIPTOR, false);
			Label catchEnd = new Label();
			impl.visitLabel(catchEnd);
			if (response) {
				impl.visitInsn(ARETURN);
			} else if (responseType != null) {
				impl.visitMethodInsn(INVOKEINTERFACE, RESPONSE_INTERNAL, "body", "()Ljava/lang/Object;", true);
				impl.visitTypeInsn(CHECKCAST, responseType.getTypeName().replace('.', '/'));
				impl.visitInsn(ARETURN);
			} else {
				impl.visitInsn(RETURN);
			}

			Label catchHandler = new Label();
			impl.visitLabel(catchHandler);
			// Yes, I know, stomping locals, we're not gonna need them where we're going...
			impl.visitVarInsn(ASTORE, 0);
			impl.visitTypeInsn(NEW, EXCEPTION_INTERNAL);
			impl.visitInsn(DUP);
			impl.visitVarInsn(ALOAD, 0);
			impl.visitMethodInsn(INVOKESPECIAL, EXCEPTION_INTERNAL, "<init>", "(Ljava/lang/Throwable;)V", false);
			impl.visitInsn(ATHROW);

			impl.visitTryCatchBlock(catchStart, catchEnd, catchHandler, Type.getInternalName(IOException.class));
			impl.visitTryCatchBlock(catchStart, catchEnd, catchHandler, Type.getInternalName(InterruptedException.class));
		}
		impl.visitMaxs(0, 0);
	}

	private static void buildClient(ClassWriter writer, String generatedName, boolean visible) {
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
		MethodVisitor client = writer.visitMethod(visible ? Modifier.PUBLIC | Modifier.FINAL : Modifier.PRIVATE, "client", "()" + CLIENT_DESCRIPTOR, null, null);
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

	private static boolean split(String input, Predicate<? super String> operation) {
		int start0 = 0;
		int end0;
		while ((end0 = input.indexOf(',', start0)) != -1) {
			if (start0 == end0) {
				++start0;
				continue;
			}
			String key = input.substring(start0, end0).trim();
			if (operation.test(key)) {
				return true;
			}
			start0 = end0 + 1;
		}
		if (start0 != input.length()) {
			String key = input.substring(start0).trim();
			return operation.test(key);
		}
		return false;
	}

	private static void segment(String input, Consumer<? super String> remaining, Consumer<? super String> tokens) {
		int start = 0;
		int end;
		while ((end = input.indexOf('{', start)) != -1) {
			// Everything up to the first opening brace.
			String segment = input.substring(start, end);
			remaining.accept(segment);
			start = end + 1;

			end = input.indexOf('}', start);
			if (end == -1) {
				throw new IllegalStateException("Unclosed '{' at position " + start);
			}

			// The segment inbetween the opening and closing brace. 
			String param = input.substring(start, end);
			tokens.accept(param);
			start = end + 1;
		}

		if (start != input.length()) {
			String segment = input.substring(start);
			remaining.accept(segment);
		}
	}
}
