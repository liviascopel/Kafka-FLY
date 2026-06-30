<script>
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import iconeAviaoSrc from '@/assets/airplane-air-plane-fly-airport-svgrepo-com.svg';

export default {
    name: 'TelaMapa',
    data() {
        return {
            inputLat: null,
            inputLng: null,
            rota: [] 
        };
    },
    mounted() {
        this.$nextTick(() => {
            this.inicializarMapa();
        });
    },
    methods: {
        inicializarMapa() {
            const latInicial = -20.3155;
            const lngInicial = -40.3128;
            
            this.mapa = L.map(this.$refs.containerDoMapa).setView([latInicial, lngInicial], 10);

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors',
                maxZoom: 19
            }).addTo(this.mapa);

            const iconeAviao = L.icon({
                iconUrl: iconeAviaoSrc,
                iconSize: [32, 32],
                iconAnchor: [16, 16]
            });  

            this.marcadorAviao = L.marker([latInicial, lngInicial], {
                icon: iconeAviao 
            }).addTo(this.mapa);   

            this.rota.push([latInicial, lngInicial]);
            this.linhaRota = null;

            this.mapa.on('zoom move', () => {
                this.rotacionaAviao();
            });
        },

        // Para rotacionar o aviao
        calcularAzimute(lat1, lon1, lat2, lon2) {
            const toRad = (valor) => valor * Math.PI / 180;
            const toDeg = (valor) => valor * 180 / Math.PI;

            const phi1 = toRad(lat1);
            const phi2 = toRad(lat2);
            const deltaLambda = toRad(lon2 - lon1);

            const y = Math.sin(deltaLambda) * Math.cos(phi2);
            const x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);

            const theta = Math.atan2(y, x);
            return (toDeg(theta) + 360) % 360;
        },

        rotacionaAviao() {
            if (!this.marcadorAviao) return;
            
            /* Aguarda a conclusao das transicoes assincronas do Leaflet */
            setTimeout(() => {
                const elementoIcone = this.marcadorAviao.getElement();
                if (elementoIcone) {
                    elementoIcone.style.transformOrigin = 'center center';
                    const transformAtual = elementoIcone.style.transform;
                    const transformLimpo = transformAtual.replace(/ rotate\([^)]+\)/g, '');
                    elementoIcone.style.transform = `${transformLimpo} rotate(${this.anguloAtual}deg)`;
                }
            }, 50);
        },

        atualizarPosicao() {
            if (this.inputLat === null || this.inputLng === null) return;

            const posicaoAntiga = this.rota[this.rota.length - 1];
            this.anguloAtual = this.calcularAzimute(posicaoAntiga[0], posicaoAntiga[1], this.inputLat, this.inputLng);
            const novaCoordenada = [this.inputLat, this.inputLng];

            this.rota.push(novaCoordenada);
            this.marcadorAviao.setLatLng(novaCoordenada);

            /* Inicializa ou atualiza o caminha do aviao */
            if (!this.linhaRota) {
                this.linhaRota = L.polyline(this.rota, { color: 'blue', weight: 3 }).addTo(this.mapa);
            } else {
                this.linhaRota.setLatLngs(this.rota);
            }

            this.mapa.panTo(novaCoordenada);
            this.rotacionaAviao();
        }
    },
    beforeUnmount() {
        if (this.mapa) {
            this.mapa.off();
            this.mapa.remove();
            this.mapa = null;
        }
    }
}
</script>

<template>
    <div class="container mt-3">
        <!-- Usando para testar o caminho e a rotacao -->
        <div class="row mb-3 align-items-end">
            <div class="col-md-4">
                <label class="form-label font-weight-bold">Nova Latitude</label>
                <input type="number" step="any" v-model.number="inputLat" class="form-control" placeholder="Ex: -20.2900">
            </div>
            <div class="col-md-4">
                <label class="form-label font-weight-bold">Nova Longitude</label>
                <input type="number" step="any" v-model.number="inputLng" class="form-control" placeholder="Ex: -40.3000">
            </div>
            <div class="col-md-4">
                <button @click="atualizarPosicao" class="btn btn-primary w-100">Atualizar Posição do Voo</button>
            </div>
        </div>
        <div ref="containerDoMapa" class="estilo-mapa"></div>
    </div>
    
</template>

<style scoped>
.estilo-mapa {
    /* height: 50vh;
    width: 200%; */
    height: 600px;
    width: 100%;
    z-index: 1;
    border-radius: 8px; 
    box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1);
}
</style>