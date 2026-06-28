package br.ufes.soe.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.ufes.soe.dataTranferObject.CriaUsuarioRequest;
import br.ufes.soe.domain.Endereco;
import br.ufes.soe.domain.Usuario;
import br.ufes.soe.repository.UsuarioRepository;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;

    public UsuarioService(UsuarioRepository usuarioRepository){
        this.usuarioRepository = usuarioRepository;
    }

    public void registraUsuario(CriaUsuarioRequest request){
        
        //confere se usuario ja nao esta cadastrado
        if(usuarioRepository.existsByCpf(request.getCpf())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Um usuario com esse CPF ja existe");
        }

        Endereco novoEnd = new Endereco(request.getEndereco().getCidade(), request.getEndereco().getBairro(), request.getEndereco().getRua(), request.getEndereco().getNumero());
        Usuario novoUs = new Usuario(request.getNome(), request.getEmail(), request.getCpf(), novoEnd);

        usuarioRepository.save(novoUs);
    }

}
