package br.ufes.soe.domain.flight;

import java.time.Instant;

import com.fasterxml.jackson.databind.JsonNode;

public record OpenSkyState(
    String icao24,
    String callsign,
    String originCountry,
    Long timePosition,
    Long lastContact,
    Double longitude,
    Double latitude,
    Double barometricAltitude,
    Boolean onGround,
    Double velocity,
    Double trueTrack,
    Double verticalRate,
    Double geoAltitude
) {
    public static OpenSkyState fromJson(JsonNode state) {
        return new OpenSkyState(
            textAt(state, 0),
            normalizeCallsign(textAt(state, 1)),
            textAt(state, 2),
            longAt(state, 3),
            longAt(state, 4),
            doubleAt(state, 5),
            doubleAt(state, 6),
            doubleAt(state, 7),
            booleanAt(state, 8),
            doubleAt(state, 9),
            doubleAt(state, 10),
            doubleAt(state, 11),
            doubleAt(state, 13)
        );
    }

    public LiveTelemetry toLiveTelemetry() {
        Long updatedAt = lastContact != null ? lastContact : timePosition;
        return new LiveTelemetry(
            updatedAt != null ? Instant.ofEpochSecond(updatedAt) : null,
            latitude,
            longitude,
            geoAltitude != null ? geoAltitude : barometricAltitude,
            trueTrack,
            velocity,
            verticalRate,
            onGround
        );
    }

    public String flightStatus(String aviationStackStatus) {
        if (Boolean.FALSE.equals(onGround)) {
            return "active";
        }

        if (Boolean.TRUE.equals(onGround)) {
            if ("active".equalsIgnoreCase(aviationStackStatus)) {
                return "landed";
            }
            if ("scheduled".equalsIgnoreCase(aviationStackStatus)) {
                return "scheduled";
            }
        }

        return aviationStackStatus != null ? aviationStackStatus : "scheduled";
    }

    public static String normalizeCallsign(String callsign) {
        if (callsign == null || callsign.isBlank()) {
            return null;
        }
        return callsign.trim().replaceAll("\\s+", "").toUpperCase();
    }

    private static String textAt(JsonNode state, int index) {
        JsonNode value = state.get(index);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Long longAt(JsonNode state, int index) {
        JsonNode value = state.get(index);
        return value == null || value.isNull() ? null : value.longValue();
    }

    private static Double doubleAt(JsonNode state, int index) {
        JsonNode value = state.get(index);
        return value == null || value.isNull() ? null : value.doubleValue();
    }

    private static Boolean booleanAt(JsonNode state, int index) {
        JsonNode value = state.get(index);
        return value == null || value.isNull() ? null : value.asBoolean();
    }
}

