package br.ufes.soe.domain.pickup;

import java.io.Serializable;

public record PickupAlert(
    Long userId,
    String userName,
    String flightIcao,
    String arrivalAirportIata,
    Integer travelTimeMinutes,
    Integer etaMinutes,
    String message
) implements Serializable {}
