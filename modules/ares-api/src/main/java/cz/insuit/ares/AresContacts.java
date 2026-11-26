package cz.insuit.ares;

import org.jspecify.annotations.Nullable;

/**
 * What a health provider or a school publishes about itself; ARES has no contacts for anyone else.
 *
 * @param email the school register lists several; this is the first
 */
public record AresContacts(
        @Nullable String email,
        @Nullable String phone,
        @Nullable String fax,
        @Nullable String www) {}
