package cz.insuit.ares;

/** The register answered 404: no subject has this IČO. An answer, not a failure; retrying changes nothing. */
public final class AresNotFoundException extends AresException {
    public AresNotFoundException(String ico) {
        super(ico, "no subject with IČO " + ico + " in ARES", null);
    }
}
