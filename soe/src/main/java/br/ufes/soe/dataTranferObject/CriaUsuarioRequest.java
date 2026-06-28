package br.ufes.soe.dataTranferObject;

public class CriaUsuarioRequest {
    private String nome;
    private String email;
    private String cpf;
    private EnderecoRequest endereco;

    public CriaUsuarioRequest(String nome, String email, String cpf, EnderecoRequest enderecoRequest){
        setNome(nome);
        setEmail(email);
        setCpf(cpf);
        setEndereco(enderecoRequest);
    }

    protected CriaUsuarioRequest(){}
    
    public EnderecoRequest getEndereco() {
        return endereco;
    }

    public void setEndereco(EnderecoRequest endereco) {
        this.endereco = endereco;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
}
