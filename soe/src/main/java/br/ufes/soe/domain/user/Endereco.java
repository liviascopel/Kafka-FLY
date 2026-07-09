package br.ufes.soe.domain.user;

import jakarta.persistence.Embeddable;

@Embeddable
public class Endereco {
    private String estado;
    private String cidade;
    private String bairro;
    private String rua;
    private int numero;

    private Double latitude;
    private Double longitude;
    
    public Endereco(String estado, String cidade, String bairro, String rua, int numero){
        setEstado(estado);
        setCidade(cidade);
        setBairro(bairro);
        setRua(rua);
        setNumero(numero);
    }
    
    protected Endereco(){}
    
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

    
    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getRua() {
        return rua;
    }

    public void setRua(String rua) {
        this.rua = rua;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}
