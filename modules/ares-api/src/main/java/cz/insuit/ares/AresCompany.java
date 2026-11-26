package cz.insuit.ares;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * A subject as ARES lists it. Only {@code ico} and {@code name} are always there; the register leaves out the rest
 * for many subjects.
 *
 * @param ico the eight-digit IČO it was looked up by
 * @param dic the VAT number, {@code CZ} and digits, for a VAT payer
 * @param name the business name, {@code obchodniJmeno}
 * @param legalForm the {@code pravniForma} code, such as {@code 112} for a limited company; {@link
 *     AresLegalFormGroup#of} sorts it
 * @param established the day the subject came into being
 * @param address the registered seat, every part of it possibly null
 * @param contacts only for a health provider (NRPZS) or a school (RŠ); null for everyone else, or when that register
 *     did not answer
 * @param soleTrader the person a sole trader is, from the trade register (RŽP); null for anyone else, or when RŽP did
 *     not answer
 */
public record AresCompany(
        String ico,
        @Nullable String dic,
        String name,
        @Nullable String legalForm,
        @Nullable LocalDate established,
        AresAddress address,
        @Nullable AresContacts contacts,
        @Nullable AresSoleTrader soleTrader) {}
