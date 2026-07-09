package br.ufes.soe.controller.pickup;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.ufes.soe.dto.pickup.CreatePickupRequest;
import br.ufes.soe.service.pickup.PickupService;

@RestController
@RequestMapping("/pickup")
public class PickupController {
    private final PickupService pickupService;

    public PickupController(PickupService pickupService){
        this.pickupService = pickupService;
    }

    @PostMapping("/request")
    public ResponseEntity<Void> createPickupRequest(@RequestBody CreatePickupRequest request){
        pickupService.createPickupRequest(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
