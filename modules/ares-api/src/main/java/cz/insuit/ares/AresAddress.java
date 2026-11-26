package cz.insuit.ares;

import org.jspecify.annotations.Nullable;

/**
 * A registered seat, {@code sidlo}, as the register splits it.
 *
 * @param countryCode ISO 3166-1 alpha-2, {@code CZ}
 * @param houseNumber číslo popisné (or evidenční), the {@code 778} of {@code 778/3a}
 * @param orientationNumber číslo orientační, the {@code 3} of {@code 778/3a}
 * @param orientationNumberLetter the {@code a} of {@code 778/3a}
 * @param postalCode a number, so {@code 01001} arrives as {@code 1001}: pad it to five digits to write it
 * @param cityDistrict a Prague district or a city's own, {@code Praha 4}
 * @param textAddress the whole address in one line, as the register writes it
 */
public record AresAddress(
        @Nullable String countryCode,
        @Nullable String city,
        @Nullable String street,
        @Nullable Integer houseNumber,
        @Nullable Integer orientationNumber,
        @Nullable String orientationNumberLetter,
        @Nullable Integer postalCode,
        @Nullable String cityDistrict,
        @Nullable String textAddress) {}
