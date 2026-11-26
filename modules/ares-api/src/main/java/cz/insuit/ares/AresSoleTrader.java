package cz.insuit.ares;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/** A sole trader (OSVČ) as the trade register names them. The register publishes the date of birth. */
public record AresSoleTrader(
        @Nullable String firstName,
        @Nullable String lastName,
        @Nullable LocalDate dateOfBirth) {}
