package br.ufes.soe.domain.flight;

import java.util.List;

public record ApiResponse(
    List<Flight> data // ja mapeia data para uma lista de records automaticamente
) {}

