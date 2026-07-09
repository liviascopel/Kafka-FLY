package br.ufes.soe.domain.pickup;

import java.io.Serializable;

public record PickupRequest(
    Long userId,
    String userName,
    String userEmail,
    String flightIcao,
    Double userLatitude,
    Double userLongitude
) implements Serializable{}
