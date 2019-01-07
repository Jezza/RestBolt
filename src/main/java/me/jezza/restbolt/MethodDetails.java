package me.jezza.restbolt;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import org.objectweb.asm.Type;

/**
 * @author Jezza
 */
final class MethodDetails {
	private static final String HTTP_RESPONSE = "java.net.http.HttpResponse";
	private static final String VOID = "java.lang.Void";

	/**
	 * true, if the method is expecting the CompletableFuture that shall result from the call.
	 */
	final boolean async;

	/**
	 * true, if the method is expecting the HttpResponse itself, and not just the value.
	 */
	final boolean response;

	/**
	 * == null ? discarding
	 */
	final java.lang.reflect.Type responseType;

	private MethodDetails(boolean async, boolean response, java.lang.reflect.Type responseType) {
		this.async = async;
		this.response = response;
		this.responseType = responseType;
	}

	static MethodDetails discover(Method method) {
		boolean async;
		boolean response;
		java.lang.reflect.Type responseType;

		// I want tuples... Rust as ruined me...
		// Here be dragons...
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
					name = responseType.getTypeName();
					if (name.equals("?") || name.equals(VOID)) {
						responseType = null;
					}
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
		return new MethodDetails(async, response, responseType);
	}
}
