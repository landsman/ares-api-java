package cz.insuit.ares;

import java.util.Set;
import org.jspecify.annotations.Nullable;

/** What kind of subject a {@code pravniForma} code is, from ARES's {@code PravniForma} code list. */
public enum AresLegalFormGroup {
    /** A natural person in business: trade licence, farmer, other laws, or a foreign one. */
    SOLE_TRADER,

    /** A legal entity that exists to make money, and whatever else the register lists that is neither of the others. */
    LEGAL_ENTITY,

    /** Foundations, public benefit companies, associations, churches, unions, chambers, hunting societies. */
    NON_PROFIT;

    private static final Set<String> SOLE_TRADERS =
            Set.of("101", "102", "103", "104", "105", "106", "107", "108", "424", "425");

    private static final Set<String> NON_PROFITS = Set.of(
            "116", "117", "118", "141", "161", "422", "423", "701", "702", "703", "706", "707", "708", "721", "722",
            "723", "731", "733", "736", "741", "745", "751", "761", "906", "907", "908", "921", "922", "936", "937",
            "938");

    /** Null when the register does not know: no code, or {@code 000}, its own "not determined yet". */
    public static @Nullable AresLegalFormGroup of(@Nullable String legalForm) {
        if (legalForm == null || legalForm.isEmpty() || legalForm.equals("000")) {
            return null;
        }
        if (SOLE_TRADERS.contains(legalForm)) {
            return SOLE_TRADER;
        }
        return NON_PROFITS.contains(legalForm) ? NON_PROFIT : LEGAL_ENTITY;
    }
}
