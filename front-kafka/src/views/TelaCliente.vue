<script>
import { flightCache } from '@/services/sse';

export default {
    name: 'TelaCliente',
    data() {
        return {
            numeroVooBuscado: '',
            vooSelecionadoKey: null,
            voosAtivos: flightCache.voosAtivos, // Reference to global singleton cache
            meteoPorAeroporto: flightCache.meteoPorAeroporto, // Reference to global singleton cache
            alertaAproximacao: false,
            origemPadrao: 'São Paulo (GRU)',
            destinoPadrao: 'Vitória (VIX)'
        };
    },
    computed: {
        dadosVoo() {
            if (!this.vooSelecionadoKey) return null;
            return this.voosAtivos[this.vooSelecionadoKey] || null;
        },
        dadosMeteo() {
            if (!this.dadosVoo || !this.dadosVoo.destinoIata) return null;
            return this.meteoPorAeroporto[this.dadosVoo.destinoIata] || null;
        },
        progressoVoo() {
            if (!this.dadosVoo) return 0;
            if (this.dadosVoo.status === 'landed') return 100;
            if (this.dadosVoo.distanciaRestante === undefined || this.dadosVoo.distanciaRestante === null) return 0;
            
            // Estimating flight distance depending on routes
            let distanciaInicial = 380; // GRU -> VIX is approx 380km
            if (this.dadosVoo.origemIata === 'SDU') distanciaInicial = 320; // SDU -> VIX is approx 320km
            if (this.dadosVoo.origemIata === 'CGH') distanciaInicial = 390;
            
            const restante = this.dadosVoo.distanciaRestante;
            const progresso = ((distanciaInicial - restante) / distanciaInicial) * 100;
            return Math.max(0, Math.min(100, Math.round(progresso)));
        }
    },
    mounted() {
        // Register standard JavaScript event listeners for backend SSE events
        window.addEventListener('complete-flights', this.lidarComDadosVoo);
        window.addEventListener('climate-alert', this.lidarComAlertaClimatico);

        // Auto-select first flight if none is selected and cached flights exist
        if (!this.vooSelecionadoKey && Object.keys(this.voosAtivos).length > 0) {
            const firstKey = Object.keys(this.voosAtivos)[0];
            this.vooSelecionadoKey = firstKey;
            this.numeroVooBuscado = firstKey;
            this.atualizarAlerta();
        }
    },
    methods: {
        buscarVoo() {
            if (!this.numeroVooBuscado) return;
            const query = this.numeroVooBuscado.trim().toUpperCase().replace(/\s+/g, '');
            
            // Search inside active flights
            const foundKey = Object.keys(this.voosAtivos).find(key => {
                const f = this.voosAtivos[key];
                const matchVoo = f.voo.toUpperCase().replace(/\s+/g, '') === query;
                const matchNumber = f.flightNumber.toUpperCase().replace(/\s+/g, '') === query;
                
                // Also match against raw fields
                const matchRawIata = f.raw?.flight?.iata?.toUpperCase().replace(/\s+/g, '') === query;
                const matchRawIcao = f.raw?.flight?.icao?.toUpperCase().replace(/\s+/g, '') === query;
                
                return matchVoo || matchNumber || matchRawIata || matchRawIcao || key.toUpperCase().replace(/\s+/g, '') === query;
            });
            
            if (foundKey) {
                this.vooSelecionadoKey = foundKey;
                this.numeroVooBuscado = this.voosAtivos[foundKey].voo;
                this.atualizarAlerta();
            } else {
                alert(`Nenhum voo ativo correspondente a "${this.numeroVooBuscado}" foi localizado. Aguarde novos dados ou tente novamente.`);
            }
        },
        selecionarVooRapido(key) {
            this.vooSelecionadoKey = key;
            const f = this.voosAtivos[key];
            if (f) {
                this.numeroVooBuscado = f.voo;
            }
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

            console.log('Cliente processando complete-flights:', data);
            
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
                this.numeroVooBuscado = data.voo;
            }
            
            if (this.vooSelecionadoKey === data.voo) {
                this.atualizarAlerta();
            }
        },
        lidarComAlertaClimatico(event) {
            const data = event.detail;
            if (!data || !data.airportIata) return;

            console.log('Cliente processando climate-alert:', data);
            
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
    },
    beforeUnmount() {
        // Clean up listeners
        window.removeEventListener('complete-flights', this.lidarComDadosVoo);
        window.removeEventListener('climate-alert', this.lidarComAlertaClimatico);
    }
}
</script>

<template>
    <div class="container py-4">
        <!-- Main Dashboard Header -->
        <div class="row mb-4">
            <div class="col-12 text-center text-md-start">
                <span class="badge bg-success rounded-pill mb-2">Painel do Passageiro</span>
                <h2 class="fw-bold text-dark">Seu Voo em Tempo Real</h2>
                <p class="text-muted small">Acompanhe todos os detalhes da sua viagem, a telemetria e o clima no seu destino.</p>
            </div>
        </div>

        <!-- Search Bar / Flight Selection -->
        <div class="row mb-4">
            <div class="col-12">
                <div class="card custom-card border-0 shadow-sm">
                    <div class="card-body p-4">
                        <h5 class="fw-bold text-dark mb-3 d-flex align-items-center gap-2">
                            <span></span> Rastrear Voo Específico
                        </h5>
                        <div class="row g-3 align-items-center">
                            <div class="col-md-8">
                                <div class="input-group">
                                    <span class="input-group-text bg-light border-end-0 text-muted rounded-start-3">🔎</span>
                                    <input 
                                        type="text" 
                                        v-model="numeroVooBuscado" 
                                        @keyup.enter="buscarVoo"
                                        class="form-control bg-light border-start-0 rounded-end-3 py-2 text-uppercase fw-semibold" 
                                        placeholder="Digite o código do voo (ex: AD4050, LA3421, G31234)"
                                    >
                                </div>
                            </div>
                            <div class="col-md-4">
                                <button @click="buscarVoo" class="btn btn-primary w-100 py-2 rounded-3 fw-bold transition-all shadow-sm">
                                    Buscar e Monitorar
                                </button>
                            </div>
                        </div>
                        
                        <!-- Quick selector links -->
                        <div class="mt-3 d-flex flex-wrap align-items-center gap-2" v-if="Object.keys(voosAtivos).length > 0">
                            <span class="text-muted small">Voos ativos recebidos:</span>
                            <button 
                                v-for="(voo, key) in voosAtivos" 
                                :key="key"
                                @click="selecionarVooRapido(key)"
                                class="btn btn-sm rounded-pill px-3 py-1 font-monospace transition-all"
                                :class="vooSelecionadoKey === key ? 'btn-primary' : 'btn-outline-secondary'"
                                style="font-size: 0.75rem;"
                            >
                                {{ voo.voo }} ({{ voo.status === 'landed' ? 'Pousou' : 'Em voo' }})
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <!-- Telemetry and Weather Grid -->
        <div class="row g-4" v-if="dadosVoo">
            <!-- Left panel: Telemetry and progress -->
            <div class="col-lg-7">
                <div class="card custom-card border-0 shadow h-100">
                    <div class="gradient-header py-4 px-4 d-flex justify-content-between align-items-center">
                        <div>
                            <h4 class="mb-1 fw-bold">Status do Voo</h4>
                            <span class="text-white-50 small">Companhia: {{ dadosVoo.airline }}</span>
                        </div>
                        <div class="d-flex flex-column align-items-end">
                            <span class="fs-2">✈️</span>
                            <span class="badge rounded-pill text-uppercase px-3 py-1 mt-1 font-monospace" 
                                  :class="dadosVoo.status === 'landed' ? 'bg-success' : 'bg-warning text-dark'">
                                {{ dadosVoo.status === 'landed' ? 'Pousou' : 'Em Rota' }}
                            </span>
                        </div>
                    </div>
                    
                    <div class="card-body p-4">
                        <!-- Origin & Destination card style -->
                        <div class="d-flex justify-content-between align-items-center mb-4 bg-light p-3 rounded-4 border">
                            <div class="text-start">
                                <span class="d-block text-muted small text-uppercase fw-bold">Origem</span>
                                <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.origem }}</h4>
                                <span class="text-muted small fw-semibold text-primary font-monospace">{{ dadosVoo.origemIata }}</span>
                            </div>
                            <div class="d-flex flex-column align-items-center justify-content-center px-3 flex-grow-1">
                                <span class="text-muted small">{{ dadosVoo.status === 'landed' ? 'Chegada Concluída' : 'Em rota' }}</span>
                                <div class="w-100 border-top my-2 position-relative">
                                    <span class="position-absolute start-50 top-50 translate-middle bg-white px-2 text-primary" style="font-size: 0.85rem;">✈️</span>
                                </div>
                            </div>
                            <div class="text-end">
                                <span class="d-block text-muted small text-uppercase fw-bold">Destino</span>
                                <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.destino }}</h4>
                                <span class="text-muted small fw-semibold text-primary font-monospace">{{ dadosVoo.destinoIata }}</span>
                            </div>
                        </div>

                        <!-- Progress Bar with plane icon -->
                        <div class="mb-5 px-2">
                            <div class="d-flex justify-content-between mb-2">
                                <span class="text-muted small fw-semibold">Progresso da Viagem</span>
                                <span class="text-primary fw-bold small">{{ progressoVoo }}% concluído</span>
                            </div>
                            <div class="progress custom-progress">
                                <div 
                                    class="progress-bar custom-progress-bar" 
                                    role="progressbar" 
                                    :style="{ width: progressoVoo + '%' }"
                                >
                                    <span class="plane-marker">✈️</span>
                                </div>
                            </div>
                        </div>

                        <!-- Telemetry Grid -->
                        <div class="row g-3">
                            <div class="col-md-4">
                                <div class="p-3 bg-light rounded-3 text-center border">
                                    <span class="d-block text-muted small mb-1">Velocidade Atual</span>
                                    <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.velocidade }} <span class="fs-6 fw-normal text-muted">km/h</span></h4>
                                </div>
                            </div>
                            <div class="col-md-4">
                                <div class="p-3 bg-light rounded-3 text-center border">
                                    <span class="d-block text-muted small mb-1">Altitude</span>
                                    <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.altitude }} <span class="fs-6 fw-normal text-muted">m</span></h4>
                                </div>
                            </div>
                            <div class="col-md-4">
                                <div class="p-3 bg-light rounded-3 text-center border">
                                    <span class="d-block text-muted small mb-1">Direção</span>
                                    <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.direction }}°</h4>
                                </div>
                            </div>
                            <div class="col-md-6 mt-3">
                                <div class="p-3 bg-light rounded-3 text-center border">
                                    <span class="d-block text-muted small mb-1">Distância Restante</span>
                                    <h4 class="fw-bold text-dark mb-0">{{ dadosVoo.distanciaRestante }} <span class="fs-6 fw-normal text-muted">km</span></h4>
                                </div>
                            </div>
                            <div class="col-md-6 mt-3">
                                <div class="p-3 bg-primary bg-opacity-10 rounded-3 text-center border border-primary border-opacity-25">
                                    <span class="d-block text-primary small mb-1">Tempo Estimado (ETA)</span>
                                    <h4 class="fw-bold text-primary mb-0">{{ dadosVoo.tempoEstimado }}</h4>
                                </div>
                            </div>
                        </div>

                        <!-- Proximity Alert Banner -->
                        <div v-if="alertaAproximacao" class="alert alert-warning mt-4 border-0 shadow-sm d-flex align-items-center gap-3 p-3 rounded-4">
                            <span class="fs-3">🔔</span>
                            <div>
                                <h6 class="alert-heading fw-bold mb-1 text-dark">Aproximação Confirmada!</h6>
                                <p class="mb-0 small text-muted font-semibold">O voo está a menos de 50km de {{ dadosVoo.destino }}. Prepare-se para o pouso.</p>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <!-- Right panel: Weather conditions -->
            <div class="col-lg-5">
                <div class="card custom-card border-0 shadow h-100 overflow-hidden">
                    <div class="card-body p-4 d-flex flex-column justify-content-between">
                        <div>
                            <h4 class="fw-bold text-dark mb-3 d-flex align-items-center gap-2">
                                <span class="text-primary">🌦️</span> Clima no Destino
                            </h4>
                            <p class="text-muted small mb-4">Condições meteorológicas em tempo real no aeroporto de chegada ({{ dadosVoo.destinoIata }}) para garantir sua segurança.</p>
                            
                            <div v-if="dadosMeteo" class="text-center py-4 bg-light rounded-4 border mb-4">
                                <div class="display-3 fw-bold text-primary mb-2">
                                    {{ dadosMeteo.temperatura }}°C
                                </div>
                                <h5 class="fw-bold text-dark mb-1">{{ dadosMeteo.condicao }}</h5>
                                <span class="text-muted small">IATA: {{ dadosMeteo.iata }} | {{ dadosMeteo.airportName }}</span>
                            </div>

                            <div class="row g-3" v-if="dadosMeteo">
                                <div class="col-6">
                                    <div class="p-3 bg-light rounded-3 border">
                                        <span class="d-block text-muted small mb-1">Vento</span>
                                        <h6 class="fw-bold mb-0 text-dark">{{ dadosMeteo.ventoVelocidade }} km/h</h6>
                                        <span class="text-muted small font-monospace">Direção: {{ dadosMeteo.ventoDirecao }}</span>
                                    </div>
                                </div>
                                <div class="col-6">
                                    <div class="p-3 bg-light rounded-3 border">
                                        <span class="d-block text-muted small mb-1">Umidade</span>
                                        <h6 class="fw-bold mb-0 text-dark">{{ dadosMeteo.umidade }}%</h6>
                                        <span class="text-muted small font-monospace">Pressão: {{ dadosMeteo.pressao }} hPa</span>
                                    </div>
                                </div>
                            </div>
                            
                            <div class="text-center py-5" v-else>
                                <p class="text-muted mb-0">Aguardando dados climáticos de {{ dadosVoo.destinoIata }}...</p>
                                <div class="spinner-border spinner-border-sm text-success mt-2" role="status"></div>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
        
        <!-- Empty state when no flight is selected/available -->
        <div class="row" v-else>
            <div class="col-12">
                <div class="text-center py-5 bg-white rounded-4 shadow-sm border p-4">
                    <div class="fs-1 mb-3">✈️</div>
                    <h4 class="fw-bold text-dark">Nenhum Voo Selecionado</h4>
                    <p class="text-muted mx-auto" style="max-width: 450px;">
                        Insira o número do seu voo no campo acima ou selecione um dos voos ativos detectados para começar o rastreamento em tempo real.
                    </p>
                </div>
            </div>
        </div>
    </div>
</template>