package me.jezza.restbolt;

/**
 * @author Jezza
 */
public final class SyncException extends Exception {
	private static final long serialVersionUID = 3123911718119372237L;

	public SyncException(String message) {
		super(message);
	}

	public SyncException(String message, Throwable cause) {
		super(message, cause);
	}

	public SyncException(Throwable cause) {
		super(cause);
	}
}
