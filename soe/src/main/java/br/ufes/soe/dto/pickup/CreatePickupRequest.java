package br.ufes.soe.dto.pickup;

public class CreatePickupRequest {
    private Long userId;
    private String flightIcao;

    public String getFlightIcao() {
        return flightIcao;
    }

    public void setFlightIcao(String flightIcao) {
        this.flightIcao = flightIcao;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
