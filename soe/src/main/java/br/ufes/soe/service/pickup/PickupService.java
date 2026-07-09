package br.ufes.soe.service.pickup;

import org.springframework.stereotype.Service;

import br.ufes.soe.domain.pickup.PickupRequest;
import br.ufes.soe.domain.user.Usuario;
import br.ufes.soe.dto.pickup.CreatePickupRequest;
import br.ufes.soe.repository.user.UsuarioRepository;

@Service
public class PickupService {
    private final UsuarioRepository usuarioRepository;
    private final PickupProducer pickupProducer;

    public PickupService(UsuarioRepository usuarioRepository, PickupProducer pickupProducer){
        this.usuarioRepository = usuarioRepository;
        this.pickupProducer = pickupProducer;
    }

    public void createPickupRequest(CreatePickupRequest request){
        Usuario usuario = usuarioRepository.findById(request.getUserId()).orElseThrow();

        PickupRequest event = new PickupRequest(
            usuario.getId(),
            usuario.getNome(),
            usuario.getEmail(),
            request.getFlightIcao(),
            usuario.getEndereco().getLatitude(),
            usuario.getEndereco().getLongitude()
        );


        pickupProducer.send(event);
    }
}
