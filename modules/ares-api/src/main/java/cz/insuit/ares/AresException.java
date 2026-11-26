package cz.insuit.ares;

import org.jspecify.annotations.Nullable;

/**
 * A lookup that did not return a subject, one subclass per thing a caller does about it, so a {@code switch} over
 * them is exhaustive. The constructors are public so a caller's own tests can throw them from a fake.
 */
public abstract sealed class AresException extends RuntimeException
        permits AresNotFoundException, AresUnavailableException {

    private final String ico;

    AresException(String ico, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.ico = ico;
    }

    /** The IČO that was looked up. */
    public String ico() {
        return ico;
    }
}
