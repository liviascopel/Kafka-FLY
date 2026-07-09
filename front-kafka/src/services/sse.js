// Service to manage the Server-Sent Events (SSE) connection and mock simulations
import { reactive } from 'vue';

const defaultUrl = import.meta.env.VITE_SSE_URL || 'http://localhost:8081/api/monitoramento/stream';

let eventSource = null;
let simulationInterval = null;
let isSimulating = false;

let rankingWS = null;
let alertsWS = null;

// Global Singleton Cache to preserve state across route transitions, wrapped in Vue reactive proxy
export const flightCache = reactive({
    voosAtivos: {},
    meteoPorAeroporto: {},
    rankingAirlines: [],
    logsEventos: [],
    alertasExposicao: {},
    alertasPickup: {}
});

// Airport coordinates dictionary for calculating distance and display names
export const AIRPORT_COORDS = {
  'VIX': { lat: -20.2588, lng: -40.2922, name: 'Eurico de Aguiar Salles (VIX)' },
  'GRU': { lat: -23.4356, lng: -46.4731, name: 'Guarulhos (GRU)' },
  'CGH': { lat: -23.6266, lng: -46.6564, name: 'Congonhas (CGH)' },
  'SDU': { lat: -22.9105, lng: -43.1631, name: 'Santos Dumont (SDU)' },
  'GIG': { lat: -22.8089, lng: -43.2506, name: 'Galeão (GIG)' },
  'BSB': { lat: -15.8697, lng: -47.9172, name: 'Brasília (BSB)' },
  'CNF': { lat: -19.6244, lng: -43.9719, name: 'Confins (CNF)' }
};

// Helper function to calculate Haversine distance in km
export function calcularDistancia(lat1, lon1, lat2, lon2) {
    const R = 6371; // Radius of the earth in km
    const dLat = (lat2 - lat1) * Math.PI / 180;
    const dLon = (lon2 - lon1) * Math.PI / 180;
    const a = 
        Math.sin(dLat/2) * Math.sin(dLat/2) +
        Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) * 
        Math.sin(dLon/2) * Math.sin(dLon/2); 
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
    return Math.round(R * c);
}

// Maps WMO code to human readable conditions
export function mapWeatherCode(code) {
    if (code === undefined || code === null) return 'Limpo';
    if (code === 0) return 'Céu Limpo';
    if (code >= 1 && code <= 3) return 'Parcialmente Nublado';
    if (code === 45 || code === 48) return 'Neblina Densa';
    if (code >= 51 && code <= 55) return 'Chuvisco';
    if (code >= 61 && code <= 65) return 'Chuva Forte';
    if (code >= 71 && code <= 77) return 'Neve';
    if (code >= 80 && code <= 82) return 'Pancadas de Chuva';
    if (code >= 95 && code <= 99) return 'Tempestade Elétrica';
    return 'Instável';
}

// Normalizes Flight data to standard format
export const normalizeFlightData = (data) => {
    if (!data) return null;
    
    const flightInfo = data.flight || {};
    const departureInfo = data.departure || {};
    const arrivalInfo = data.arrival || {};
    const airlineInfo = data.airline || {};
    const liveInfo = data.live || {};
    
    // Extract flight code with robust fallbacks
    const voo = data.voo || flightInfo.iata || flightInfo.icao || (flightInfo.number ? `${airlineInfo.name || ''} ${flightInfo.number}`.trim() : null) || 'DESCONHECIDO';
    const flightNumber = flightInfo.number || data.flightNumber || (data.voo ? data.voo.replace(/^[A-Za-z0-9]+-/, '') : 'N/A');
    const status = data.flight_status || data.status || (liveInfo.is_ground ? 'landed' : 'active');
    const origem = data.origem || departureInfo.airport || departureInfo.iata || 'GRU';
    const destino = data.destino || arrivalInfo.airport || arrivalInfo.iata || 'VIX';
    const origemIata = (departureInfo.iata || (data.origem && data.origem.includes('GRU') ? 'GRU' : 'GRU')).toUpperCase().trim();
    const destinoIata = (arrivalInfo.iata || (data.destino && data.destino.includes('VIX') ? 'VIX' : 'VIX')).toUpperCase().trim();
    
    // Telemetry fields
    const latitude = liveInfo.latitude !== undefined ? liveInfo.latitude : (data.latitude !== undefined ? data.latitude : data.lat);
    const longitude = liveInfo.longitude !== undefined ? liveInfo.longitude : (data.longitude !== undefined ? data.longitude : data.lng);
    const altitude = liveInfo.altitude !== undefined ? liveInfo.altitude : (data.altitude !== undefined ? data.altitude : 0);
    const direction = liveInfo.direction !== undefined ? liveInfo.direction : (data.direction !== undefined ? data.direction : 0);
    const velocidade = liveInfo.speed_horizontal !== undefined ? liveInfo.speed_horizontal : (data.velocidade !== undefined ? data.velocidade : 0);
    
    // Distance estimation
    let distanciaRestante = data.distanciaRestante;
    if (distanciaRestante === undefined && latitude !== undefined && longitude !== undefined) {
        const destCoords = AIRPORT_COORDS[destinoIata] || AIRPORT_COORDS['VIX'];
        distanciaRestante = calcularDistancia(latitude, longitude, destCoords.lat, destCoords.lng);
    }
    
    // ETA estimation
    let tempoEstimado = data.tempoEstimado;
    if (tempoEstimado === undefined && distanciaRestante !== undefined && velocidade > 0) {
        tempoEstimado = distanciaRestante === 0 ? 'Chegou' : `${Math.ceil(distanciaRestante / (velocidade / 60))} min`;
    } else if (tempoEstimado === undefined) {
        tempoEstimado = 'Indisponível';
    }
    
    return {
        voo,
        flightNumber,
        status,
        origem,
        destino,
        origemIata,
        destinoIata,
        latitude: parseFloat(latitude),
        longitude: parseFloat(longitude),
        altitude: Math.round(altitude),
        direction: Math.round(direction),
        velocidade: Math.round(velocidade),
        distanciaRestante: Math.round(distanciaRestante),
        tempoEstimado,
        airline: airlineInfo.name || data.airline || 'Kafka-FLY',
        raw: data
    };
};

// Normalizes Weather data to standard format
export const normalizeMeteoData = (data) => {
    if (!data) return null;
    
    const iata = (data.iataCode || data.iata || 'VIX').toUpperCase().trim();
    const airportName = data.airportName || data.airport || 'Vitória';
    const temperatura = data.temperature !== undefined ? data.temperature : (data.temperatura !== undefined ? data.temperatura : 25);
    const ventoVelocidade = data.windspeed !== undefined ? data.windspeed : (data.ventoVelocidade !== undefined ? data.ventoVelocidade : 10);
    const condicao = data.condicao || mapWeatherCode(data.weatherCode) || 'Limpo';
    const ventoDirecao = data.ventoDirecao || 'NE';
    const umidade = data.umidade !== undefined ? data.umidade : 75;
    const pressao = data.pressao || '1012';
    
    return {
        iata,
        airportName,
        temperatura: parseFloat(temperatura).toFixed(1),
        ventoVelocidade: parseFloat(ventoVelocidade).toFixed(1),
        condicao,
        ventoDirecao,
        umidade: parseInt(umidade),
        pressao,
        raw: data
    };
};

// Handles Websocket bridge connection - Disabled as frontend is configured to communicate via StreamingController SSE only
const startWebSockets = () => {
    // WebSockets disabled to listen solely to StreamingController SSE events (those 3 topics)
};

const stopWebSockets = () => {
    // No-op as WebSockets are disabled
};

/**
 * Initializes the EventSource connection to the backend SSE emitter.
 */
export const startSSE = (url = defaultUrl) => {
    if (eventSource) {
        console.log('SSE connection already exists, closing it first...');
        eventSource.close();
    }

    stopSimulation();

    console.log(`Connecting to SSE at: ${url}`);
    eventSource = new EventSource(url);

    // 1. Event: airline-ranking-output
    eventSource.addEventListener('airline-ranking-output', (event) => {
        try {
            const data = JSON.parse(event.data);
            
            // Normalize and update global cache for ranking
            let updatedList = [...flightCache.rankingAirlines];
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
                    if (index !== -1) updatedList[index] = normalized;
                    else updatedList.push(normalized);
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
                    if (index !== -1) updatedList[index] = normalized;
                    else updatedList.push(normalized);
                }
            }
            updatedList.sort((a, b) => b.score - a.score);
            const ranked = updatedList.map((item, index) => ({ ...item, rank: index + 1 }));
            flightCache.rankingAirlines.splice(0, flightCache.rankingAirlines.length, ...ranked);

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'airline-ranking-output',
                payload: JSON.stringify(data)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('airline-ranking-output', { detail: flightCache.rankingAirlines }));
        } catch (err) {
            console.error('Error parsing airline-ranking-output:', err);
        }
    });

    // 2. Event: complete-flights
    eventSource.addEventListener('complete-flights', (event) => {
        try {
            const data = JSON.parse(event.data);
            const normalized = normalizeFlightData(data);
            
            // Update global cache
            flightCache.voosAtivos[normalized.voo] = normalized;
            
            // Initialize weather if destination not exists in cache
            const destino = normalized.destinoIata;
            if (destino && !flightCache.meteoPorAeroporto[destino]) {
                flightCache.meteoPorAeroporto[destino] = {
                    iata: destino,
                    airportName: normalized.destino || 'Aeroporto',
                    temperatura: '25.0',
                    condicao: 'Céu Limpo',
                    ventoVelocidade: '12.0',
                    ventoDirecao: 'NE',
                    umidade: 70,
                    pressao: '1013'
                };
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'complete-flights',
                payload: JSON.stringify(normalized)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('complete-flights', { detail: normalized }));
        } catch (err) {
            console.error('Error parsing complete-flights:', err);
        }
    });

    // 3. Event: climate-alert
    eventSource.addEventListener('climate-alert', (event) => {
        try {
            const data = JSON.parse(event.data);
            
            // Update global weather cache on severe weather
            const iata = data.airportIata;
            if (iata) {
                flightCache.meteoPorAeroporto[iata] = {
                    iata: iata,
                    airportName: data.airportName || 'Aeroporto',
                    temperatura: '18.0',
                    condicao: data.condition || 'Condição Severa',
                    ventoVelocidade: '35.0',
                    ventoDirecao: 'S',
                    umidade: 95,
                    pressao: '998'
                };
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'climate-alert',
                payload: JSON.stringify(data)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('climate-alert', { detail: data }));
        } catch (err) {
            console.error('Error parsing climate-alert:', err);
        }
    });

    // 4. Event: meteo-raw
    eventSource.addEventListener('meteo-raw', (event) => {
        try {
            const data = JSON.parse(event.data);
            const normalized = normalizeMeteoData(data);
            if (normalized && normalized.iata) {
                flightCache.meteoPorAeroporto[normalized.iata] = normalized;
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'meteo-raw',
                payload: JSON.stringify(normalized)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('meteo-raw', { detail: normalized }));
        } catch (err) {
            console.error('Error parsing meteo-raw:', err);
        }
    });

    // 5. Event: climate-exposure-alert
    eventSource.addEventListener('climate-exposure-alert', (event) => {
        try {
            const data = JSON.parse(event.data);
            const key = (data.flightIcao || '').toUpperCase().trim();
            if (key) {
                flightCache.alertasExposicao[key] = data;
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'climate-exposure-alert',
                payload: JSON.stringify(data)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('climate-exposure-alert', { detail: data }));
        } catch (err) {
            console.error('Error parsing climate-exposure-alert:', err);
        }
    });

    // 6. Event: pickup-alerts
    eventSource.addEventListener('pickup-alerts', (event) => {
        try {
            const data = JSON.parse(event.data);
            const key = (data.flightIcao || '').toUpperCase().trim();
            if (key) {
                flightCache.alertasPickup[key] = data;
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'pickup-alerts',
                payload: JSON.stringify(data)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('pickup-alerts', { detail: data }));
        } catch (err) {
            console.error('Error parsing pickup-alerts:', err);
        }
    });

    eventSource.onopen = () => {
        console.log('SSE Connection established successfully.');
        window.dispatchEvent(new CustomEvent('sse-status', { detail: { connected: true, simulating: false } }));
    };

    eventSource.onerror = (err) => {
        console.error('SSE Connection error:', err);
        window.dispatchEvent(new CustomEvent('sse-status', { detail: { connected: false, error: err, simulating: false } }));
    };

    return eventSource;
};

/**
 * Closes the SSE connection.
 */
export const stopSSE = () => {
    if (eventSource) {
        console.log('Closing SSE connection.');
        eventSource.close();
        eventSource = null;
    }
    window.dispatchEvent(new CustomEvent('sse-status', { detail: { connected: false, simulating: isSimulating } }));
};

/**
 * Starts a simulation that generates mock data and dispatches standard CustomEvents.
 */
export const startSimulation = () => {
    if (eventSource) {
        stopSSE();
    }
    if (simulationInterval) {
        clearInterval(simulationInterval);
    }

    console.log('Starting local SSE simulation with multi-flight tracking...');
    isSimulating = true;
    window.dispatchEvent(new CustomEvent('sse-status', { detail: { connected: true, simulating: true } }));

    // Multi-flight simulated registry
    const simulatedFlights = [
        {
            number: '4050',
            iata: 'AD4050',
            icao: 'AZU4050',
            airline: 'Azul Linhas Aéreas',
            airlineIata: 'AD',
            airlineIcao: 'AZU',
            startLat: -23.4356, // GRU
            startLng: -46.4731,
            targetLat: -20.2588, // VIX
            targetLng: -40.2922,
            origem: 'Guarulhos (GRU)',
            origemIata: 'GRU',
            destino: 'Vitória (VIX)',
            destinoIata: 'VIX',
            progress: 0.0,
            speed: 820,
            altitude: 11000,
            direction: 45,
            status: 'active'
        },
        {
            number: '3421',
            iata: 'LA3421',
            icao: 'TAM3421',
            airline: 'LATAM Airlines',
            airlineIata: 'LA',
            airlineIcao: 'TAM',
            startLat: -23.6266, // CGH
            startLng: -46.6564,
            targetLat: -20.2588, // VIX
            targetLng: -40.2922,
            origem: 'Congonhas (CGH)',
            origemIata: 'CGH',
            destino: 'Vitória (VIX)',
            destinoIata: 'VIX',
            progress: 0.35,
            speed: 780,
            altitude: 10500,
            direction: 50,
            status: 'active'
        },
        {
            number: '1234',
            iata: 'G31234',
            icao: 'GLO1234',
            airline: 'GOL Linhas Aéreas',
            airlineIata: 'G3',
            airlineIcao: 'GLO',
            startLat: -22.9105, // SDU
            startLng: -43.1631,
            targetLat: -20.2588, // VIX
            targetLng: -40.2922,
            origem: 'Santos Dumont (SDU)',
            origemIata: 'SDU',
            destino: 'Vitória (VIX)',
            destinoIata: 'VIX',
            progress: 0.7,
            speed: 800,
            altitude: 9500,
            direction: 35,
            status: 'active'
        }
    ];

    let elapsed = 0;

    simulationInterval = setInterval(() => {
        elapsed += 1;
        
        // 1. Update and emit flights
        simulatedFlights.forEach(f => {
            if (f.status === 'active') {
                f.progress += 0.03 + Math.random() * 0.02;
                if (f.progress >= 1.0) {
                    f.progress = 1.0;
                    f.status = 'landed';
                    f.landedTicks = 0;
                }
            } else {
                f.landedTicks = (f.landedTicks || 0) + 1;
                if (f.landedTicks >= 4) { // Stay landed for 4 ticks
                    f.progress = 0.0;
                    f.status = 'active';
                    f.landedTicks = 0;
                }
            }

            const lat = f.startLat + (f.targetLat - f.startLat) * f.progress;
            const lng = f.startLng + (f.targetLng - f.startLng) * f.progress;
            const currentSpeed = f.status === 'landed' ? 0 : f.speed + Math.floor((Math.random() - 0.5) * 10);
            const currentAltitude = f.status === 'landed' ? 0 : f.altitude + Math.floor((Math.random() - 0.5) * 200);

            const rawData = {
                flight_date: new Date().toLocaleDateString(),
                flight_status: f.status,
                departure: { airport: f.origem, iata: f.origemIata },
                arrival: { airport: f.destino, iata: f.destinoIata },
                airline: { name: f.airline, iata: f.airlineIata, icao: f.airlineIcao },
                flight: { number: f.number, iata: f.iata, icao: f.icao },
                live: {
                    latitude: lat,
                    longitude: lng,
                    altitude: currentAltitude,
                    speed_horizontal: currentSpeed,
                    direction: f.direction,
                    is_ground: f.status === 'landed',
                    updated: new Date().toISOString()
                }
            };

            const normalized = normalizeFlightData(rawData);
            
            // Update global cache
            flightCache.voosAtivos[normalized.voo] = normalized;
            
            const destino = normalized.destinoIata;
            if (destino && !flightCache.meteoPorAeroporto[destino]) {
                flightCache.meteoPorAeroporto[destino] = {
                    iata: destino,
                    airportName: normalized.destino || 'Aeroporto',
                    temperatura: '25.0',
                    condicao: 'Céu Limpo',
                    ventoVelocidade: '12.0',
                    ventoDirecao: 'NE',
                    umidade: 70,
                    pressao: '1013'
                };
            }

            // Log event
            const timestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp,
                topico: 'complete-flights',
                payload: JSON.stringify(normalized)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('complete-flights', { detail: normalized }));
        });

        // 2. Dispatch airline-ranking-output event (complying with AirlineMetrics model)
        const rankings = [
            { airlineName: 'Azul Linhas Aéreas', totalFlights: 145, delayedFlights: 5, cancelledFlights: 1, score: (95.5 + Math.sin(elapsed * 0.5) * 2) },
            { airlineName: 'LATAM Airlines', totalFlights: 180, delayedFlights: 15, cancelledFlights: 2, score: (89.2 + Math.cos(elapsed * 0.4) * 3) },
            { airlineName: 'GOL Linhas Aéreas', totalFlights: 155, delayedFlights: 20, cancelledFlights: 4, score: (82.1 + Math.sin(elapsed * 0.3) * 4) }
        ];
        rankings.sort((a, b) => b.score - a.score);
        const ranked = rankings.map((item, index) => ({ ...item, rank: index + 1 }));
        flightCache.rankingAirlines.splice(0, flightCache.rankingAirlines.length, ...ranked);

        // Log event
        const rankTimestamp = new Date().toLocaleTimeString();
        flightCache.logsEventos.unshift({
            id: Date.now() + Math.random(),
            timestamp: rankTimestamp,
            topico: 'airline-ranking-output',
            payload: JSON.stringify(flightCache.rankingAirlines)
        });
        if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

        window.dispatchEvent(new CustomEvent('airline-ranking-output', {
            detail: flightCache.rankingAirlines
        }));

        // 3. Dispatch simulated climate alerts occasionally (every 3 ticks / 9 seconds)
        if (elapsed % 3 === 0) {
            const alertTypes = ["NEBLINA DENSA", "CHUVA FORTE", "TEMPESTADE ELÉTRICA"];
            const type = alertTypes[Math.floor(Math.random() * alertTypes.length)];
            const isVix = Math.random() > 0.5;
            const iata = isVix ? 'VIX' : 'GRU';
            const airportName = isVix ? 'Eurico de Aguiar Salles (VIX)' : 'Guarulhos (GRU)';
            
            // Find a simulated flight with this destination to make it look realistic
            const flight = simulatedFlights.find(f => f.destinoIata === iata) || simulatedFlights[0];
            
            const rawFlightData = {
                flight_date: new Date().toLocaleDateString(),
                flight_status: flight.status,
                departure: { airport: flight.origem, iata: flight.origemIata },
                arrival: { airport: flight.destino, iata: flight.destinoIata },
                airline: { name: flight.airline, iata: flight.airlineIata, icao: flight.airlineIcao },
                flight: { number: flight.number, iata: flight.iata, icao: flight.icao },
                live: {
                    latitude: flight.startLat + (flight.targetLat - flight.startLat) * flight.progress,
                    longitude: flight.startLng + (flight.targetLng - flight.startLng) * flight.progress,
                    altitude: flight.status === 'landed' ? 0 : flight.altitude,
                    speed_horizontal: flight.status === 'landed' ? 0 : flight.speed,
                    direction: flight.direction,
                    is_ground: flight.status === 'landed'
                }
            };

            const alertData = {
                flightIcao: flight.icao,
                condition: type,
                airportIata: iata,
                airportName: airportName,
                type: "DESTINO",
                flightDetails: rawFlightData
            };

            // Update weather
            flightCache.meteoPorAeroporto[iata] = {
                iata: iata,
                airportName: airportName,
                temperatura: '18.0',
                condicao: type,
                ventoVelocidade: '35.0',
                ventoDirecao: 'S',
                umidade: 95,
                pressao: '998'
            };

            // Log event
            const alertTimestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp: alertTimestamp,
                topico: 'climate-alert',
                payload: JSON.stringify(alertData)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('climate-alert', {
                detail: alertData
            }));
        }

        // 4. Dispatch simulated meteo-raw events periodically (every 2 ticks / 6 seconds)
        if (elapsed % 2 === 0) {
            const isVix = Math.random() > 0.5;
            const iata = isVix ? 'VIX' : 'GRU';
            const airportName = isVix ? 'Eurico de Aguiar Salles (VIX)' : 'Guarulhos (GRU)';
            
            const rawMeteo = {
                iataCode: iata,
                airportName: airportName,
                countryName: "Brasil",
                temperature: 20 + Math.random() * 10,
                windspeed: 5 + Math.random() * 20,
                weatherCode: Math.floor(Math.random() * 5)
            };
            
            const normalized = normalizeMeteoData(rawMeteo);
            flightCache.meteoPorAeroporto[iata] = normalized;

            // Log event
            const meteoTimestamp = new Date().toLocaleTimeString();
            flightCache.logsEventos.unshift({
                id: Date.now() + Math.random(),
                timestamp: meteoTimestamp,
                topico: 'meteo-raw',
                payload: JSON.stringify(normalized)
            });
            if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

            window.dispatchEvent(new CustomEvent('meteo-raw', {
                detail: normalized
            }));
        }

        // 5. Dispatch simulated climate-exposure-alert events periodically (every 4 ticks / 12 seconds)
        if (elapsed % 4 === 0) {
            const flightKeys = Object.keys(flightCache.voosAtivos);
            if (flightKeys.length > 0) {
                const randomKey = flightKeys[Math.floor(Math.random() * flightKeys.length)];
                const flight = flightCache.voosAtivos[randomKey];
                const isVix = Math.random() > 0.5;
                const iata = isVix ? 'VIX' : 'GRU';
                const airportName = isVix ? 'Eurico de Aguiar Salles (VIX)' : 'Guarulhos (GRU)';
                
                // Construct a simulated Allen relation alert
                const alertData = {
                    flightIcao: flight.raw?.flight?.icao || flight.voo,
                    allenRelation: "OVERLAPS",
                    risky: true,
                    airportIata: iata,
                    airportName: airportName,
                    type: isVix ? "DESTINO" : "ORIGEM",
                    flightWindowStart: new Date(Date.now() - 600000).toISOString(),
                    flightWindowEnd: new Date(Date.now() + 1200000).toISOString(),
                    stormWindowStart: new Date(Date.now() - 300000).toISOString(),
                    stormWindowEnd: new Date(Date.now() + 900000).toISOString(),
                    flightDetails: flight.raw
                };

                const key = (alertData.flightIcao || '').toUpperCase().trim();
                if (key) {
                    flightCache.alertasExposicao[key] = alertData;
                }

                // Log event
                const exposureTimestamp = new Date().toLocaleTimeString();
                flightCache.logsEventos.unshift({
                    id: Date.now() + Math.random(),
                    timestamp: exposureTimestamp,
                    topico: 'climate-exposure-alert',
                    payload: JSON.stringify(alertData)
                });
                if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

                window.dispatchEvent(new CustomEvent('climate-exposure-alert', {
                    detail: alertData
                }));
            }
        }

        // 6. Dispatch simulated pickup-alerts events periodically (every 5 ticks / 15 seconds)
        if (elapsed % 5 === 0) {
            const flightKeys = Object.keys(flightCache.voosAtivos);
            if (flightKeys.length > 0) {
                const randomKey = flightKeys[Math.floor(Math.random() * flightKeys.length)];
                const flight = flightCache.voosAtivos[randomKey];
                
                const alertData = {
                    userId: 999,
                    userName: "Motorista Mock",
                    flightIcao: flight.raw?.flight?.icao || flight.voo,
                    arrivalAirportIata: flight.destinoIata,
                    travelTimeMinutes: 35,
                    etaMinutes: 65,
                    message: `O voo solicitado, pousará em 65 minutos, no aeroporto ${flight.destinoIata}. O tempo de seu endereço até o aeroporto de destino é 35 minutos. Vá para o aeroporto em 30 minutos.`
                };

                const key = (alertData.flightIcao || '').toUpperCase().trim();
                if (key) {
                    flightCache.alertasPickup[key] = alertData;
                }

                // Log event
                const pickupTimestamp = new Date().toLocaleTimeString();
                flightCache.logsEventos.unshift({
                    id: Date.now() + Math.random(),
                    timestamp: pickupTimestamp,
                    topico: 'pickup-alerts',
                    payload: JSON.stringify(alertData)
                });
                if (flightCache.logsEventos.length > 15) flightCache.logsEventos.pop();

                window.dispatchEvent(new CustomEvent('pickup-alerts', {
                    detail: alertData
                }));
            }
        }

    }, 3000);
};

/**
 * Stops the mock data simulation.
 */
export const stopSimulation = () => {
    if (simulationInterval) {
        clearInterval(simulationInterval);
        simulationInterval = null;
    }
    isSimulating = false;
    window.dispatchEvent(new CustomEvent('sse-status', { detail: { connected: false, simulating: false } }));
};

/**
 * Returns the current simulation status.
 */
export const getSimulationStatus = () => isSimulating;

