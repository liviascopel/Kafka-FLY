package br.ufes.soe.dto.user;

public class EnderecoRequest {
    private String estado;
    private String cidade;
    private String bairro;
    private String rua;
    private int numero;

    public EnderecoRequest(String estado, String cidade, String bairro, String rua, int numero){
        setEstado(estado);
        setCidade(cidade);
        setBairro(bairro);
        setRua(rua);
        setNumero(numero);
    }

    protected EnderecoRequest(){}
    
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

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

}
