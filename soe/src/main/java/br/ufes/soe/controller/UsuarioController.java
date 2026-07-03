package br.ufes.soe.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufes.soe.dto.CriaUsuarioRequest;
import br.ufes.soe.service.UsuarioService;

@RestController
@RequestMapping("/usuario")
public class UsuarioController {
    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService){
        this.usuarioService = usuarioService;
    }

    /**
     * Caso queiram testar no postman:
     * Metodo POST
     * URL http://localhost:8080/usuario/cadastra
     * JSON
     * {
     *  "nome": "Thais",
     *  "email": "thais@gmail.com",
     *  "cpf": "123456789-10",
     *  "endereco": {
     *      "estado": "Espirito Santo",
     *      "cidade": "Vitória",
     *      "bairro": "Jardim da Penha",
     *      "rua": "Rua Jahira Santos Rodrigues",
     *      "numero": 114
     *  }
     * }
     * 
     * @param request
     * @return
     */
    @PostMapping("/cadastra")
    public ResponseEntity<Void> registraUsuario(@RequestBody CriaUsuarioRequest request){
        usuarioService.registraUsuario(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
