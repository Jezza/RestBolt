package me.jezza.restbolt;

import java.lang.invoke.MethodHandle;
import java.net.URI;

/**
 * @author Jezza
 */
public final class Binder<T> {
	private final MethodHandle handle;

	Binder(MethodHandle handle) {
		this.handle = handle;
	}

	public T bind(String uri) {
		URI hostUri = URI.create(uri);
		return bind(hostUri);
	}

	public T bind(URI uri) {
		try {
			return (T) handle.invoke(uri);
		} catch (Throwable t) {
			throw new IllegalStateException("Failed to instantiate class", t);
		}
	}
}
