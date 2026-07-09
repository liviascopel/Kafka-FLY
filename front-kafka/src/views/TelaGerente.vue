<script>
import { flightCache } from '@/services/sse';

export default {
    name: 'TelaGerente',
    data() {
        return {
            vooSelecionadoKey: null,
            voosAtivos: flightCache.voosAtivos, // Reference to global singleton cache
            meteoPorAeroporto: flightCache.meteoPorAeroporto, // Reference to global singleton cache
            rankingAirlines: flightCache.rankingAirlines, // Reference to global singleton cache
            alertaAproximacao: false,
            logsEventos: flightCache.logsEventos // Reference to global singleton cache
        };
    },
    computed: {
        listaVoos() {
            return Object.values(this.voosAtivos);
        },
        dadosVoo() {
            if (!this.vooSelecionadoKey) return null;
            return this.voosAtivos[this.vooSelecionadoKey] || null;
        },
        dadosMeteo() {
            if (!this.dadosVoo || !this.dadosVoo.destinoIata) return null;
            return this.meteoPorAeroporto[this.dadosVoo.destinoIata] || null;
        },
        alertaExposicaoClimatica() {
            if (!this.dadosVoo) return null;
            const icao = (this.dadosVoo.raw?.flight?.icao || this.dadosVoo.voo || '').toUpperCase().replace(/[^A-Za-z0-9]/g, '');
            for (const key in flightCache.alertasExposicao) {
                const normKey = key.toUpperCase().replace(/[^A-Za-z0-9]/g, '');
                if (normKey === icao || icao.endsWith(normKey) || normKey.endsWith(icao)) {
                    return flightCache.alertasExposicao[key];
                }
            }
            return null;
        }
    },
    mounted() {
        // Register standard JavaScript event listeners for backend SSE events
        window.addEventListener('complete-flights', this.lidarComDadosVoo);
        window.addEventListener('airline-ranking-output', this.lidarComRanking);
        window.addEventListener('climate-alert', this.lidarComAlertaClimatico);
        window.addEventListener('meteo-raw', this.lidarComMeteoRaw);
        window.addEventListener('climate-exposure-alert', this.lidarComAlertaExposicao);

        // Auto-select first flight if none is selected and cached flights exist
        if (!this.vooSelecionadoKey && Object.keys(this.voosAtivos).length > 0) {
            this.vooSelecionadoKey = Object.keys(this.voosAtivos)[0];
            this.atualizarAlerta();
        }
    },
    methods: {
        adicionarLog(topico, payload) {
            const timestamp = new Date().toLocaleTimeString();
            this.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico,
                payload: JSON.stringify(payload)
            });
            // Keep last 15 logs
            if (this.logsEventos.length > 15) {
                this.logsEventos.pop();
            }
        },
        selecionarVoo(key) {
            this.vooSelecionadoKey = key;
            this.atualizarAlerta();
        },
        atualizarAlerta() {
            const data = this.dadosVoo;
            if (data && data.distanciaRestante > 0 && data.distanciaRestante < 50 && data.status !== 'landed') {
                this.alertaAproximacao = true;
            } else {
                this.alertaAproximacao = false;
            }
        },
        lidarComDadosVoo(event) {
            const data = event.detail;
            if (!data || !data.voo) return;

            console.log('Gerente processando complete-flights:', data);
            
            // Add or update dictionary of flights
            this.voosAtivos[data.voo] = data;

            // Initialize weather for the destination if not exists
            const destino = data.destinoIata;
            if (destino && !this.meteoPorAeroporto[destino]) {
                this.meteoPorAeroporto[destino] = {
                    iata: destino,
                    airportName: data.destino || 'Aeroporto',
                    temperatura: '25.0',
                    condicao: 'Céu Limpo',
                    ventoVelocidade: '12.0',
                    ventoDirecao: 'NE',
                    umidade: 70,
                    pressao: '1013'
                };
            }
            
            // Auto-select first flight if none is selected
            if (!this.vooSelecionadoKey) {
                this.vooSelecionadoKey = data.voo;
            }
            
            if (this.vooSelecionadoKey === data.voo) {
                this.atualizarAlerta();
            }
            this.adicionarLog('complete-flights', data);
        },
        lidarComRanking(event) {
            const data = event.detail;
            if (!data) return;

            console.log('Gerente processando airline-ranking-output:', data);
            
            let updatedList = [...this.rankingAirlines];
            
            // Handle array of rankings (mock/simulated) or single objects (real Kafka metrics)
            if (Array.isArray(data)) {
                data.forEach(item => {
                    const name = item.airlineName || item.airline;
                    const index = updatedList.findIndex(a => a.airlineName === name);
                    const normalized = {
                        airlineName: name,
                        score: parseFloat(item.score || 0),
                        totalFlights: item.totalFlights || item.flightsCount || 0,
                        delayedFlights: item.delayedFlights || 0,
                        cancelledFlights: item.cancelledFlights || 0
                    };
                    if (index !== -1) {
                        updatedList[index] = normalized;
                    } else {
                        updatedList.push(normalized);
                    }
                });
            } else {
                const name = data.airlineName || data.airline;
                if (name) {
                    const index = updatedList.findIndex(a => a.airlineName === name);
                    const normalized = {
                        airlineName: name,
                        score: parseFloat(data.score || 0),
                        totalFlights: data.totalFlights || data.flightsCount || 0,
                        delayedFlights: data.delayedFlights || 0,
                        cancelledFlights: data.cancelledFlights || 0
                    };
                    if (index !== -1) {
                        updatedList[index] = normalized;
                    } else {
                        updatedList.push(normalized);
                    }
                }
            }
            
            // Sort descending by score
            updatedList.sort((a, b) => b.score - a.score);
            
            // Assign rank dynamically and mutate the array in-place to keep reference to flightCache.rankingAirlines
            const ranked = updatedList.map((item, index) => ({
                ...item,
                rank: index + 1
            }));
            this.rankingAirlines.splice(0, this.rankingAirlines.length, ...ranked);

            this.adicionarLog('airline-ranking-output', data);
        },
        lidarComAlertaClimatico(event) {
            const data = event.detail;
            if (!data) return;

            if (data.airportIata) {
                // Update the airport weather to match the severe condition in the alert
                this.meteoPorAeroporto[data.airportIata] = {
                    iata: data.airportIata,
                    airportName: data.airportName || 'Aeroporto',
                    temperatura: '18.0',
                    condicao: data.condition || 'Condição Severa',
                    ventoVelocidade: '35.0',
                    ventoDirecao: 'S',
                    umidade: 95,
                    pressao: '998'
                };
            }
            this.adicionarLog('climate-alert', data);
        },
        lidarComMeteoRaw(event) {
            const data = event.detail;
            if (!data || !data.iata) return;

            console.log('Gerente processando meteo-raw:', data);
            
            this.meteoPorAeroporto[data.iata] = data;
            this.adicionarLog('meteo-raw', data);
        },
        lidarComAlertaExposicao(event) {
            const data = event.detail;
            if (!data) return;

            console.log('Gerente processando climate-exposure-alert:', data);
            this.adicionarLog('climate-exposure-alert', data);
        }
    },
    beforeUnmount() {
        // Clean up listeners
        window.removeEventListener('complete-flights', this.lidarComDadosVoo);
        window.removeEventListener('airline-ranking-output', this.lidarComRanking);
        window.removeEventListener('climate-alert', this.lidarComAlertaClimatico);
        window.removeEventListener('meteo-raw', this.lidarComMeteoRaw);
        window.removeEventListener('climate-exposure-alert', this.lidarComAlertaExposicao);
    }
}
</script>

<template>
    <div class="container py-4">
        <!-- Dashboard Header -->
        <div class="row mb-4">
            <div class="col-12 text-center text-md-start">
                <span class="badge bg-primary rounded-pill mb-2">Painel de Supervisão</span>
                <h2 class="fw-bold text-dark">Painel de Controle de Operações</h2>
                <p class="text-muted small">Gerenciamento de rankings das companhias, dados de telemetria agregados e clima local.</p>
            </div>
        </div>

        <div class="row g-4">
            <!-- Left side: Airline rankings -->
            <div class="col-lg-5">
                <div class="card custom-card border-0 shadow h-100">
                    <div class="card-body p-4">
                        <h4 class="fw-bold text-dark mb-3 d-flex align-items-center gap-2">
                            <span class="text-warning"></span> Ranking de Companhias Aéreas
                        </h4>
                        <p class="text-muted small mb-4 font-semibold">Desempenho calculado em tempo real via Kafka Streams.</p>

                        <div v-if="rankingAirlines.length > 0" class="pe-2" style="max-height: 580px; overflow-y: auto;">
                            <div class="d-flex flex-column gap-3">
                                <div 
                                    v-for="airline in rankingAirlines" 
                                    :key="airline.airlineName" 
                                    class="p-3 bg-light rounded-4 border d-flex flex-column gap-2 transition-all hover-shadow"
                                >
                                    <div class="d-flex justify-content-between align-items-center">
                                        <div class="d-flex align-items-center gap-2">
                                            <span class="badge bg-dark rounded-circle px-2 py-1" style="font-size: 0.8rem; min-width: 25px; text-align: center;">{{ airline.rank }}</span>
                                            <span class="fw-bold text-dark">{{ airline.airlineName }}</span>
                                        </div>
                                        <span class="badge bg-primary rounded-pill px-3 py-1.5">{{ airline.score.toFixed(1) }} pts</span>
                                    </div>
                                    <div class="progress" style="height: 6px; border-radius: 3px;">
                                        <div 
                                            class="progress-bar bg-primary rounded-pill" 
                                            role="progressbar" 
                                            :style="{ width: airline.score + '%' }"
                                        ></div>
                                    </div>
                                    <div class="d-flex justify-content-between text-muted" style="font-size: 0.75rem;">
                                        <span>Voos: <strong>{{ airline.totalFlights }}</strong></span>
                                        <span>Atrasos: <strong class="text-danger">{{ airline.delayedFlights }}</strong></span>
                                        <span>Cancelados: <strong class="text-danger">{{ airline.cancelledFlights }}</strong></span>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <div class="text-center py-5" v-else>
                            <p class="text-muted mb-0">Aguardando atualização do ranking das companhias...</p>
                            <div class="spinner-border spinner-border-sm text-warning mt-2" role="status"></div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right side: Active Flights list + Telemetry + weather status -->
            <div class="col-lg-7">
                <div class="d-flex flex-column gap-4">
                    
                    <!-- Table: Active Flights in Transit -->
                    <div class="card custom-card border-0 shadow-sm">
                        <div class="card-body p-4">
                            <h5 class="fw-bold text-dark mb-3 d-flex align-items-center gap-2">
                                Voos Ativos sob Supervisão ({{ listaVoos.length }})
                            </h5>
                            <div class="table-responsive" style="max-height: 180px; overflow-y: auto;">
                                <table class="table table-hover align-middle" style="font-size: 0.85rem;">
                                    <thead class="table-light">
                                        <tr>
                                            <th>Código</th>
                                            <th>Companhia</th>
                                            <th>Rota</th>
                                            <th>Status</th>
                                            <th>Ações</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        <tr v-for="voo in listaVoos" :key="voo.voo" 
                                            :class="vooSelecionadoKey === voo.voo ? 'table-primary font-semibold' : ''"
                                            @click="selecionarVoo(voo.voo)" style="cursor: pointer;">
                                            <td class="font-monospace text-primary fw-bold">{{ voo.voo }}</td>
                                            <td>{{ voo.airline }}</td>
                                            <td class="font-monospace">{{ voo.origemIata }} ➔ {{ voo.destinoIata }}</td>
                                            <td>
                                                <span class="badge rounded-pill" :class="voo.status === 'landed' ? 'bg-success' : 'bg-warning text-dark'">
                                                    {{ voo.status === 'landed' ? 'Pousou' : 'Ativo' }}
                                                </span>
                                            </td>
                                            <td>
                                                <button class="btn btn-sm btn-primary rounded-pill px-2 py-0.5" style="font-size: 0.7rem;">Inspecionar</button>
                                            </td>
                                        </tr>
                                        <tr v-if="listaVoos.length === 0">
                                            <td colspan="5" class="text-center py-3 text-muted">Aguardando telemetria de voos...</td>
                                        </tr>
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>

                    <!-- Active Telemetry details for selected flight -->
                    <div class="card custom-card border-0 shadow-sm overflow-hidden" v-if="dadosVoo">
                        <div class="gradient-header py-3 px-4 d-flex justify-content-between align-items-center">
                            <h6 class="mb-0">Telemetria Consolidada: {{ dadosVoo.voo }}</h6>
                            <span class="badge bg-light text-primary font-monospace">{{ dadosVoo.status === 'landed' ? 'POUSOU' : 'EM VOO' }}</span>
                        </div>
                        <div class="card-body p-0">
                            <ul class="list-group list-group-flush border-0">
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 bg-transparent border-0">
                                    <span class="text-muted small fw-semibold">Companhia Aérea</span>
                                    <span class="fw-bold text-dark">{{ dadosVoo.airline }}</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 bg-transparent border-0">
                                    <span class="text-muted small fw-semibold">Velocidade Horizontal</span>
                                    <span class="fw-bold text-dark">{{ dadosVoo.velocidade }} km/h</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 bg-transparent border-0">
                                    <span class="text-muted small fw-semibold">Altitude de Voo</span>
                                    <span class="fw-bold text-dark">{{ dadosVoo.altitude }} m</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 bg-transparent border-0">
                                    <span class="text-muted small fw-semibold">Distância Restante</span>
                                    <span class="fw-bold text-dark">{{ dadosVoo.distanciaRestante }} km</span>
                                </li>
                                <li class="list-group-item d-flex justify-content-between align-items-center py-2.5 px-4 bg-transparent border-0 border-bottom-0">
                                    <span class="text-muted small fw-semibold">Estimativa de Pouso (ETA)</span>
                                    <span class="fw-bold text-success">{{ dadosVoo.tempoEstimado }}</span>
                                </li>
                            </ul>
                            
                            <div v-if="alertaAproximacao" class="m-3 alert alert-danger border-0 d-flex align-items-center gap-2 p-3 rounded-3 mb-3">
                                <span class="fs-4">🚨</span>
                                <div style="font-size: 0.85rem;">
                                    <strong>Alerta crítico de proximidade!</strong> O voo {{ dadosVoo.voo }} está a menos de 50km da pista de destino.
                                </div>
                            </div>

                            <div v-if="alertaExposicaoClimatica" class="m-3 alert alert-warning border-0 d-flex align-items-center gap-2 p-3 rounded-3 mb-3">
                                <span class="fs-4">⛈️</span>
                                <div style="font-size: 0.85rem;">
                                    <strong>Alerta de Exposição Climática!</strong> O voo {{ dadosVoo.voo }} passará por complicações climáticas em {{ alertaExposicaoClimatica.airportName }} ({{ alertaExposicaoClimatica.airportIata }}). Relação temporal: <strong>{{ alertaExposicaoClimatica.allenRelation }}</strong>.
                                </div>
                            </div>
                        </div>
                    </div>

                    <!-- Weather Info for selected flight destination -->
                    <div class="card custom-card border-0 shadow-sm" v-if="dadosVoo">
                        <div class="card-body p-4">
                            <h6 class="fw-bold text-dark mb-3 d-flex align-items-center gap-2">
                                Status Meteorológico do Destino ({{ dadosVoo.destinoIata }})
                            </h6>
                            <div v-if="dadosMeteo" class="d-flex align-items-center justify-content-between">
                                <div>
                                    <span class="display-6 fw-bold text-dark">{{ dadosMeteo.temperatura }}°C</span>
                                    <span class="d-block text-muted small mt-1">Condição: <strong>{{ dadosMeteo.condicao }}</strong></span>
                                </div>
                                <div class="text-end text-muted small">
                                    <span class="d-block">Vento: <strong>{{ dadosMeteo.ventoVelocidade }} km/h {{ dadosMeteo.ventoDirecao }}</strong></span>
                                    <span class="d-block">Umidade: <strong>{{ dadosMeteo.umidade }}%</strong></span>
                                    <span class="d-block">Pressão: <strong>{{ dadosMeteo.pressao }} hPa</strong></span>
                                    <span class="d-block mt-1 font-monospace text-primary" style="font-size: 0.7rem;">{{ dadosMeteo.airportName }}</span>
                                </div>
                            </div>
                            <div v-else class="text-muted small text-center py-2">
                                Aguardando dados climáticos de {{ dadosVoo.destinoIata }}...
                            </div>
                        </div>
                    </div>

                </div>
            </div>
        </div>

        <!-- Row: Event logs (terminal-style dashboard) -->
        <div class="row mt-4">
            <div class="col-12">
                <div class="card custom-card border-0 shadow-sm bg-dark text-light">
                    <div class="card-header bg-black text-white-50 py-3 px-4 d-flex justify-content-between align-items-center border-bottom border-secondary">
                        <span class="font-monospace fw-bold" style="font-size: 0.85rem; color: #38ef7d;">🟢 KAFKA STREAM EVENT LOGGER (Real-time)</span>
                        <span class="badge bg-secondary rounded-pill font-monospace" style="font-size: 0.7rem;">Event handlers active</span>
                    </div>
                    <div class="card-body p-0">
                        <div class="p-3 font-monospace bg-black" style="height: 250px; overflow-y: auto; font-size: 0.75rem; color: #a9ffc6;">
                            <div v-if="logsEventos.length === 0" class="text-muted text-center py-5">
                                [Aguardando mensagens de tópicos do Kafka via EventStream...]
                            </div>
                            <div v-for="log in logsEventos" :key="log.id" class="mb-2">
                                <span class="text-white-50">[{{ log.timestamp }}]</span>
                                <span class="badge bg-secondary mx-2 font-monospace" style="font-size: 0.65rem; color: white;">{{ log.topico }}</span>
                                <span class="text-success">{{ log.payload }}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<style scoped>
.hover-shadow:hover {
    box-shadow: 0 4px 15px rgba(0, 0, 0, 0.05);
}
</style>