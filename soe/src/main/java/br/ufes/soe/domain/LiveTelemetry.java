package br.ufes.soe.domain;

import java.time.Instant;

public record LiveTelemetry(
    Instant updated, 
    double latitude, 
    double longitude, 
    double altitude, 
    double direction,
    double speed_horizontal,
    double speed_vertical,
    boolean is_ground
) {}
