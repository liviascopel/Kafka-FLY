<script>
import 'leaflet/dist/leaflet.css';
import L from 'leaflet';
import iconeAviaoSrc from '@/assets/airplane-air-plane-fly-airport-svgrepo-com.svg';
import { flightCache } from '@/services/sse';

export default {
    name: 'TelaMapa',
    data() {
        return {
            voosAtivos: {}, // Key: flight identifier (e.g. AD-4050), Value: { data, marker, polyline, history }
            vooSelecionadoKey: null,
            inputLat: null,
            inputLng: null
        };
    },
    computed: {
        listaVoos() {
            return Object.values(this.voosAtivos).map(v => v.data);
        },
        dadosVooAtivo() {
            if (!this.vooSelecionadoKey) return null;
            return this.voosAtivos[this.vooSelecionadoKey]?.data || null;
        }
    },
    watch: {
        vooSelecionadoKey(newKey) {
            if (newKey && this.voosAtivos[newKey]) {
                const flight = this.voosAtivos[newKey];
                this.inputLat = flight.data.latitude;
                this.inputLng = flight.data.longitude;
                
                // Highlight polyline route
                Object.keys(this.voosAtivos).forEach(key => {
                    const poly = this.voosAtivos[key].polyline;
                    if (poly) {
                        if (key === newKey) {
                            poly.setStyle({ color: '#0d6efd', weight: 5, opacity: 0.9, dashArray: null });
                            poly.bringToFront();
                        } else {
                            poly.setStyle({ color: '#6c757d', weight: 3, opacity: 0.4, dashArray: '5, 8' });
                        }
                    }
                });
            }
        }
    },
    mounted() {
        this.$nextTick(() => {
            this.inicializarMapa();
            
            // Load existing flights from global cache on mount
            if (flightCache && flightCache.voosAtivos) {
                Object.values(flightCache.voosAtivos).forEach(flightData => {
                    this.adicionarOuAtualizarAviaoNoMapa(flightData);
                });
            }
            
            // Register standard JavaScript event listeners for backend SSE events
            window.addEventListener('complete-flights', this.lidarComNovoVoo);
        });
    },
    activated() {
        if (this.mapa) {
            this.$nextTick(() => {
                this.mapa.invalidateSize();
                this.rotacionarTodosAvioes();
            });
        }
    },
    methods: {
        inicializarMapa() {
            // Centers view between SP (GRU) and Vitória (VIX)
            const latCentro = -21.8471;
            const lngCentro = -43.3826;
            
            this.mapa = L.map(this.$refs.containerDoMapa).setView([latCentro, lngCentro], 7);
 
            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                attribution: '&copy; OpenStreetMap contributors',
                maxZoom: 19
            }).addTo(this.mapa);
 
            this.iconeAviao = L.icon({
                iconUrl: iconeAviaoSrc,
                iconSize: [32, 32],
                iconAnchor: [16, 16],
                popupAnchor: [0, -16]
            });
 
            this.mapa.on('zoomend moveend', () => {
                this.rotacionarTodosAvioes();
            });
        },
 
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
 
        rotacionarTodosAvioes() {
            Object.keys(this.voosAtivos).forEach(key => {
                const flight = this.voosAtivos[key];
                if (flight.marker && flight.angulo !== undefined) {
                    setTimeout(() => {
                        const elementoIcone = flight.marker.getElement();
                        if (elementoIcone) {
                            elementoIcone.style.transformOrigin = 'center center';
                            const transformAtual = elementoIcone.style.transform;
                            const transformLimpo = transformAtual.replace(/ rotate\([^)]+\)/g, '');
                            elementoIcone.style.transform = `${transformLimpo} rotate(${flight.angulo}deg)`;
                        }
                    }, 50);
                }
            });
        },
 
        selecionarVoo(key) {
            this.vooSelecionadoKey = key;
            const flight = this.voosAtivos[key];
            if (flight && flight.data.latitude && flight.data.longitude) {
                this.mapa.panTo([flight.data.latitude, flight.data.longitude]);
                flight.marker.openPopup();
            }
        },
 
        atualizarPosicao() {
            if (!this.vooSelecionadoKey || this.inputLat === null || this.inputLng === null) return;
            
            const flight = this.voosAtivos[this.vooSelecionadoKey];
            if (!flight) return;
 
            const oldLat = flight.data.latitude;
            const oldLng = flight.data.longitude;
            const newLat = parseFloat(this.inputLat);
            const newLng = parseFloat(this.inputLng);
 
            const angulo = this.calcularAzimute(oldLat, oldLng, newLat, newLng);
 
            // Update flight data copy
            flight.data.latitude = newLat;
            flight.data.longitude = newLng;
            flight.data.direction = angulo;
            flight.angulo = angulo;
 
            // Add to history and update polyline
            flight.history.push([newLat, newLng]);
            flight.marker.setLatLng([newLat, newLng]);
            if (flight.polyline) {
                flight.polyline.setLatLngs(flight.history);
            }
 
            // Update popup content
            this.atualizarPopupContent(this.vooSelecionadoKey);
 
            this.mapa.panTo([newLat, newLng]);
            this.rotacionarTodosAvioes();
        },
 
        atualizarPopupContent(key) {
            const flight = this.voosAtivos[key];
            if (!flight) return;
            const d = flight.data;
 
            const popupContent = `
                <div style="font-family: sans-serif; min-width: 170px; line-height: 1.4;">
                    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px;">
                        <h6 style="margin: 0; font-weight: bold; color: #1e3c72;">✈️ ${d.voo}</h6>
                        <span style="font-size: 0.7rem; padding: 2px 6px; border-radius: 10px; font-weight: bold; background: ${d.status === 'landed' ? '#d1e7dd' : '#fff3cd'}; color: ${d.status === 'landed' ? '#0f5132' : '#664d03'};">
                            ${d.status === 'landed' ? 'POUSOU' : 'EM VOO'}
                        </span>
                    </div>
                    <small style="color: #6c757d; font-weight: 600; display: block; margin-bottom: 6px;">${d.airline}</small>
                    <hr style="margin: 6px 0; border: 0; border-top: 1px solid #dee2e6;">
                    <table style="width: 100%; font-size: 0.75rem; border-collapse: collapse;">
                        <tr><td style="color: #6c757d; padding: 2px 0;">Rota:</td><td style="text-align: right; font-weight: bold; color: #212529;">${d.origemIata} ➔ ${d.destinoIata}</td></tr>
                        <tr><td style="color: #6c757d; padding: 2px 0;">Velocidade:</td><td style="text-align: right; font-weight: bold; color: #212529;">${d.velocidade} km/h</td></tr>
                        <tr><td style="color: #6c757d; padding: 2px 0;">Altitude:</td><td style="text-align: right; font-weight: bold; color: #212529;">${d.altitude} m</td></tr>
                        <tr><td style="color: #6c757d; padding: 2px 0;">Restante:</td><td style="text-align: right; font-weight: bold; color: #0d6efd;">${d.distanciaRestante} km</td></tr>
                    </table>
                </div>
            `;
 
            flight.marker.setPopupContent(popupContent);
        },
 
        lidarComNovoVoo(event) {
            const data = event.detail;
            this.adicionarOuAtualizarAviaoNoMapa(data);
        },

        adicionarOuAtualizarAviaoNoMapa(data) {
            if (!data || !data.voo) return;
 
            console.log('Mapa processando voo:', data);
 
            const key = data.voo;
            const lat = parseFloat(data.latitude);
            const lng = parseFloat(data.longitude);
 
            if (isNaN(lat) || isNaN(lng)) return;
 
            if (this.voosAtivos[key]) {
                // Update existing flight path and marker
                const flight = this.voosAtivos[key];
                const oldLat = flight.data.latitude;
                const oldLng = flight.data.longitude;
 
                // Update data object
                flight.data = data;
 
                // Calculate azimuth rotation angle if speed is horizontal or moving
                let angulo = data.direction;
                if (angulo === undefined || angulo === 0) {
                    angulo = this.calcularAzimute(oldLat, oldLng, lat, lng);
                }
                flight.angulo = angulo;
 
                // Update marker position
                flight.marker.setLatLng([lat, lng]);
 
                // Append coordinates to history
                flight.history.push([lat, lng]);
 
                // Update polyline route
                if (flight.polyline) {
                    flight.polyline.setLatLngs(flight.history);
                }
 
                // Update popup content
                this.atualizarPopupContent(key);
 
                if (this.vooSelecionadoKey === key) {
                    this.inputLat = lat;
                    this.inputLng = lng;
                }
            } else {
                // Create new plane marker
                const marker = L.marker([lat, lng], {
                    icon: this.iconeAviao
                }).addTo(this.mapa);
 
                // Add click listener to select flight
                marker.on('click', () => {
                    this.vooSelecionadoKey = key;
                });
 
                // Set up flight route polyline
                const history = [[lat, lng]];
                const color = this.vooSelecionadoKey === key ? '#0d6efd' : '#6c757d';
                const dashArray = this.vooSelecionadoKey === key ? null : '5, 8';
                const opacity = this.vooSelecionadoKey === key ? 0.9 : 0.4;
                const weight = this.vooSelecionadoKey === key ? 5 : 3;
 
                const polyline = L.polyline(history, { 
                    color, 
                    weight, 
                    opacity, 
                    dashArray 
                }).addTo(this.mapa);
 
                // Store in active registry
                this.voosAtivos[key] = {
                    data,
                    marker,
                    polyline,
                    history,
                    angulo: data.direction || 0
                };
 
                // Create and set popup
                this.atualizarPopupContent(key);
 
                // Auto-select first flight
                if (!this.vooSelecionadoKey) {
                    this.vooSelecionadoKey = key;
                }
            }
 
            this.rotacionarTodosAvioes();
        }
    },
    beforeUnmount() {
        if (this.mapa) {
            this.mapa.off();
            this.mapa.remove();
            this.mapa = null;
        }

        // Clean up listeners
        window.removeEventListener('complete-flights', this.lidarComNovoVoo);
    }
}
</script>

<template>
    <div class="container py-4">
        <!-- Title and info header -->
        <div class="row mb-4">
            <div class="col-12">
                <div class="card custom-card border-0 shadow-sm">
                    <div class="card-body p-4 d-flex flex-wrap justify-content-between align-items-center gap-3">
                        <div>
                            <span class="badge bg-primary rounded-pill mb-2">Painel de Operações</span>
                            <h2 class="fw-bold mb-0 text-dark">Radar de Voo em Tempo Real</h2>
                            <p class="text-muted mb-0 small">Rastreando todos os voos transmitidos pelo Kafka Streams simultaneamente.</p>
                        </div>
                        <div v-if="dadosVooAtivo" class="d-flex align-items-center gap-3 bg-light p-3 rounded-4 border">
                            <div class="text-center px-2">
                                <small class="text-uppercase text-muted fw-bold d-block" style="font-size: 0.7rem;">Selecionado</small>
                                <span class="fw-bold text-primary">{{ dadosVooAtivo.voo }}</span>
                            </div>
                            <div class="vr"></div>
                            <div>
                                <span class="fw-semibold text-dark">{{ dadosVooAtivo.origemIata }}</span>
                                <span class="mx-2 text-muted">➔</span>
                                <span class="fw-semibold text-dark">{{ dadosVooAtivo.destinoIata }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="row g-4">
            <!-- Left panel: Map container -->
            <div class="col-lg-8">
                <div class="card custom-card border-0 overflow-hidden shadow">
                    <div ref="containerDoMapa" class="estilo-mapa"></div>
                </div>
            </div>

            <!-- Right panel: Active flights list & Telemetry view -->
            <div class="col-lg-4">
                <div class="d-flex flex-column gap-4">
                    
                    <!-- Active Flights Sidebar List -->
                    <div class="card custom-card border-0 shadow-sm">
                        <div class="card-body p-4">
                            <h5 class="fw-bold mb-3 d-flex align-items-center gap-2">
                                <span>✈️</span> Voos no Radar ({{ listaVoos.length }})
                            </h5>
                            
                            <div v-if="listaVoos.length > 0" class="d-flex flex-column gap-2 overflow-auto animate-list" style="max-height: 220px;">
                                <div 
                                    v-for="voo in listaVoos" 
                                    :key="voo.voo"
                                    @click="selecionarVoo(voo.voo)"
                                    class="p-2 border rounded-3 d-flex justify-content-between align-items-center cursor-pointer transition-all"
                                    :class="vooSelecionadoKey === voo.voo ? 'bg-primary bg-opacity-10 border-primary shadow-sm' : 'bg-light hover-bg'"
                                    style="cursor: pointer;"
                                >
                                    <div>
                                        <span class="fw-bold text-dark d-block" style="font-size: 0.85rem;">{{ voo.voo }}</span>
                                        <small class="text-muted font-monospace" style="font-size: 0.75rem;">{{ voo.origemIata }} ➔ {{ voo.destinoIata }}</small>
                                    </div>
                                    <div class="text-end">
                                        <span class="badge rounded-pill" :class="voo.status === 'landed' ? 'bg-success' : 'bg-warning text-dark'" style="font-size: 0.7rem;">
                                            {{ voo.status === 'landed' ? 'Pousou' : 'Ativo' }}
                                        </span>
                                        <small class="d-block text-muted" style="font-size: 0.7rem;">{{ voo.velocidade }} km/h</small>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="text-center py-4 text-muted" v-else>
                                Ninguém no radar. Aguardando voos...
                            </div>
                        </div>
                    </div>

                    <!-- Selected flight Telemetry status -->
                    <div class="card custom-card border-0 shadow-sm overflow-hidden" v-if="dadosVooAtivo">
                        <div class="gradient-header py-3 px-4 d-flex justify-content-between align-items-center">
                            <h6 class="mb-0 font-monospace">Telemetria - {{ dadosVooAtivo.voo }}</h6>
                            <span class="badge bg-light text-primary font-monospace" style="font-size: 0.75rem;">{{ dadosVooAtivo.airline }}</span>
                        </div>
                        <div class="card-body p-0">
                            <ul class="list-group list-group-flush border-0">
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 border-0">
                                    <span class="text-muted small">Velocidade Horizontal</span>
                                    <span class="fw-bold text-dark">{{ dadosVooAtivo.velocidade }} km/h</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 border-0">
                                    <span class="text-muted small">Altitude</span>
                                    <span class="fw-bold text-dark">{{ dadosVooAtivo.altitude }} m</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 border-0">
                                    <span class="text-muted small">Direção (Curso)</span>
                                    <span class="fw-bold text-dark">{{ dadosVooAtivo.direction }}°</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 border-0">
                                    <span class="text-muted small">Distância Restante</span>
                                    <span class="fw-bold text-dark">{{ dadosVooAtivo.distanciaRestante }} km</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 border-0 border-bottom-0">
                                    <span class="text-muted small">ETA Estimado</span>
                                    <span class="fw-bold text-primary">{{ dadosVooAtivo.tempoEstimado }}</span>
                                </li>
                            </ul>
                        </div>
                    </div>

                    <!-- Manual Position Override Controls for Testing -->
                    <div class="card custom-card border-0 shadow-sm" v-if="dadosVooAtivo">
                        <div class="card-body p-4">
                            <h5 class="fw-bold mb-3 d-flex align-items-center gap-2">
                                <span style="font-size: 1.1rem;">&nbsp;</span> Corrigir Posição de {{ dadosVooAtivo.voo }}
                            </h5>
                            
                            <div class="mb-3">
                                <label class="form-label fw-semibold text-muted small">Nova Latitude</label>
                                <input type="number" step="any" v-model.number="inputLat" class="form-control rounded-3" placeholder="Ex: -20.2900">
                            </div>
                            
                            <div class="mb-3">
                                <label class="form-label fw-semibold text-muted small">Nova Longitude</label>
                                <input type="number" step="any" v-model.number="inputLng" class="form-control rounded-3" placeholder="Ex: -40.3000">
                            </div>

                            <button @click="atualizarPosicao" class="btn btn-primary w-100 py-2 rounded-3 fw-semibold transition-all">
                                Atualizar Posição
                            </button>
                        </div>
                    </div>

                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.estilo-mapa {
    height: 550px;
    width: 100%;
    z-index: 1;
}
.hover-bg:hover {
    background-color: #e9ecef !important;
}
.cursor-pointer {
    cursor: pointer;
}
.animate-list {
    transition: all 0.3s ease;
}
</style>