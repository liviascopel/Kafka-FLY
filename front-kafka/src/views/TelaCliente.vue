<script>
export default {
    name: 'TelaCliente',
    props: {
        // dadosVoo: {
        //     type: Object,
        //     required: true
        // },
        // alertaAproximacao: {
        //     type: Boolean,
        //     default: false
        // }
    }
}
</script>

<template>
    <div class="row">
        <div class="col-md-6 offset-md-3">
            <div class="card shadow">
                <div class="card-header bg-primary text-white">
                    Painel do Passageiro
                </div>
                <div class="card-body">
                    <!-- <ul class="list-group list-group-flush">
                        <li class="list-group-item"><strong>Velocidade:</strong> {{ dadosVoo.velocidade }} km/h</li>
                        <li class="list-group-item"><strong>Distância Restante:</strong> {{ dadosVoo.distanciaRestante }} km</li>
                        <li class="list-group-item"><strong>Tempo Estimado:</strong> {{ dadosVoo.tempoEstimado }}</li>
                    </ul>
                    
                    <div v-if="alertaAproximacao" class="alert alert-success mt-3">
                        O voo está se aproximando do destino final.
                    </div> -->
                </div>
            </div>
        </div>
    </div>
</template>