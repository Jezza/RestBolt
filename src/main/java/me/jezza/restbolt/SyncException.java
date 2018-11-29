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

	/**
	 * Note: this is the only used constructor.
	 * The other two are if people want to construct one manually, for whatever reason...
	 *
	 * @param cause - the cause of this exception.  (A {@code null} value is
	 *              permitted, and indicates that the cause is nonexistent or
	 *              unknown.)
	 */
	public SyncException(Throwable cause) {
		super(cause);
	}
}
