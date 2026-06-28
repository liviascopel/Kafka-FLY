package br.ufes.soe.dataTranferObject;

public class EnderecoRequest {
    private String cidade;
    private String bairro;
    private String rua;
    private int numero;

    public EnderecoRequest(String cidade, String bairro, String rua, int numero){
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

}
