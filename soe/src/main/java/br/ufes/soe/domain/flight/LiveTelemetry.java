package br.ufes.soe.domain.flight;

import java.time.Instant;

public record LiveTelemetry(
    Instant updated, 
    Double latitude, 
    Double longitude, 
    Double altitude, 
    Double direction,
    Double speed_horizontal,
    Double speed_vertical,
    boolean is_ground
) {}
