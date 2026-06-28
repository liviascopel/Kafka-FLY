package br.ufes.soe.domain;

import jakarta.persistence.Embeddable;

@Embeddable
public class Endereco {
    private String cidade;
    private String bairro;
    private String rua;
    private int numero;

    public Endereco(String cidade, String bairro, String rua, int numero){
        setCidade(cidade);
        setBairro(bairro);
        setRua(rua);
        setNumero(numero);
    }

    protected Endereco(){}
    
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
}
