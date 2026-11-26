package cz.insuit.ares;

import org.jspecify.annotations.Nullable;

/**
 * No usable answer, after the retries: a timeout, a refused connection, a 5xx, any other status, or a body this
 * library cannot read. Worth another try later; the cause says which.
 */
public final class AresUnavailableException extends AresException {
    public AresUnavailableException(String ico, String message, @Nullable Throwable cause) {
        super(ico, message, cause);
    }
}
