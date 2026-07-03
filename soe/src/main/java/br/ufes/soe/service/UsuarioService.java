package br.ufes.soe.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.ufes.soe.domain.Coordenadas;
import br.ufes.soe.domain.Endereco;
import br.ufes.soe.domain.Usuario;
import br.ufes.soe.dto.CriaUsuarioRequest;
import br.ufes.soe.repository.UsuarioRepository;

@Service
public class UsuarioService {
    private final UsuarioRepository usuarioRepository;
    private final GeocodingService geocodingService;

    public UsuarioService(UsuarioRepository usuarioRepository, GeocodingService geocodingService){
        this.usuarioRepository = usuarioRepository;
        this.geocodingService = geocodingService;
    }

    public void registraUsuario(CriaUsuarioRequest request){
        
        //confere se usuario ja nao esta cadastrado
        if(usuarioRepository.existsByCpf(request.getCpf())){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Um usuario com esse CPF ja existe");
        }

        Endereco novoEnd = new Endereco(request.getEndereco().getEstado(), request.getEndereco().getCidade(), request.getEndereco().getBairro(), request.getEndereco().getRua(), request.getEndereco().getNumero());
        
        String endTexto = novoEnd.getRua() + "," +
                          novoEnd.getNumero() + "," +
                          novoEnd.getBairro() + "," +
                          novoEnd.getCidade() + ", Brasil";

        Coordenadas coordenadas = geocodingService.calcularCoordenadas(endTexto);
        
        novoEnd.setLatitude(coordenadas.getLatitude());
        novoEnd.setLongitude(coordenadas.getLongitude());

        Usuario novoUs = new Usuario(request.getNome(), request.getEmail(), request.getCpf(), novoEnd);

        usuarioRepository.save(novoUs);
    }

}
