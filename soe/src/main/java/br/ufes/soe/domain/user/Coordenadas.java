package br.ufes.soe.domain.user;

public class Coordenadas {
    private Double latitude;
    private Double longitude;
    
    public Coordenadas(Double latitude, Double longitude){
        setLatitude(latitude);
        setLongitude(longitude);
    }
    
    protected Coordenadas(){}
    
    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
}
