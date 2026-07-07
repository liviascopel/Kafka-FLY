// Service to manage the Server-Sent Events (SSE) connection and mock simulations

const defaultUrl = import.meta.env.VITE_SSE_URL || 'http://localhost:8080/events';

let eventSource = null;
let simulationInterval = null;
let isSimulating = false;

let rankingWS = null;
let alertsWS = null;

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

// Handles Websocket bridge connection with backend Spring Boot on port 8081
const startWebSockets = () => {
    try {
        rankingWS = new WebSocket('ws://localhost:8081/ws/airline-ranking');
        rankingWS.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('Received WebSocket message [airline-ranking]:', data);
                window.dispatchEvent(new CustomEvent('airline-ranking-output', { detail: data }));
            } catch (err) {
                console.error('Error parsing ranking WebSocket data:', err);
            }
        };
        rankingWS.onerror = (err) => {
            console.warn('Ranking WebSocket connection failed (Spring Boot offline?):', err);
        };
    } catch (e) {
        console.warn('Could not initialize ranking WebSocket:', e);
    }

    try {
        alertsWS = new WebSocket('ws://localhost:8081/ws/alertas-climaticos');
        alertsWS.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                console.log('Received WebSocket message [climate-alert]:', data);
                window.dispatchEvent(new CustomEvent('climate-alert', { detail: data }));
            } catch (err) {
                console.error('Error parsing climate alert WebSocket data:', err);
            }
        };
        alertsWS.onerror = (err) => {
            console.warn('Climate alert WebSocket connection failed:', err);
        };
    } catch (e) {
        console.warn('Could not initialize climate alert WebSocket:', e);
    }
};

const stopWebSockets = () => {
    if (rankingWS) {
        rankingWS.close();
        rankingWS = null;
    }
    if (alertsWS) {
        alertsWS.close();
        alertsWS = null;
    }
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
    startWebSockets();

    console.log(`Connecting to SSE at: ${url}`);
    eventSource = new EventSource(url);

    // 1. Event: airline-ranking-output
    eventSource.addEventListener('airline-ranking-output', (event) => {
        try {
            const data = JSON.parse(event.data);
            window.dispatchEvent(new CustomEvent('airline-ranking-output', { detail: data }));
        } catch (err) {
            console.error('Error parsing airline-ranking-output:', err);
        }
    });

    // 2. Event: aviationstack-flights
    eventSource.addEventListener('aviationstack-flights', (event) => {
        try {
            const data = JSON.parse(event.data);
            const normalized = normalizeFlightData(data);
            window.dispatchEvent(new CustomEvent('complete-flights', { detail: normalized }));
        } catch (err) {
            console.error('Error parsing aviationstack-flights:', err);
        }
    });

    // 3. Event: complete-flights
    eventSource.addEventListener('complete-flights', (event) => {
        try {
            const data = JSON.parse(event.data);
            const normalized = normalizeFlightData(data);
            window.dispatchEvent(new CustomEvent('complete-flights', { detail: normalized }));
        } catch (err) {
            console.error('Error parsing complete-flights:', err);
        }
    });

    // 4. Event: meteo-raw
    eventSource.addEventListener('meteo-raw', (event) => {
        try {
            const data = JSON.parse(event.data);
            const normalized = normalizeMeteoData(data);
            window.dispatchEvent(new CustomEvent('meteo-raw', { detail: normalized }));
        } catch (err) {
            console.error('Error parsing meteo-raw:', err);
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
    stopWebSockets();
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
            window.dispatchEvent(new CustomEvent('complete-flights', { detail: normalized }));
        });

        // 2. Dispatch airline-ranking-output event (complying with AirlineMetrics model)
        window.dispatchEvent(new CustomEvent('airline-ranking-output', {
            detail: [
                { airlineName: 'Azul Linhas Aéreas', totalFlights: 145, delayedFlights: 5, cancelledFlights: 1, score: (95.5 + Math.sin(elapsed * 0.5) * 2) },
                { airlineName: 'LATAM Airlines', totalFlights: 180, delayedFlights: 15, cancelledFlights: 2, score: (89.2 + Math.cos(elapsed * 0.4) * 3) },
                { airlineName: 'GOL Linhas Aéreas', totalFlights: 155, delayedFlights: 20, cancelledFlights: 4, score: (82.1 + Math.sin(elapsed * 0.3) * 4) }
            ]
        }));

        // 3. Dispatch weather for main airports (GRU & VIX)
        const airPorts = ['VIX', 'GRU'];
        airPorts.forEach(iata => {
            const isVix = iata === 'VIX';
            const rawMeteo = {
                iataCode: iata,
                airportName: isVix ? 'Eurico de Aguiar Salles' : 'Guarulhos',
                countryName: 'Brasil',
                temperature: (isVix ? 24.5 : 18.0) + Math.sin(elapsed * 0.2) * 2,
                windspeed: 10 + Math.cos(elapsed * 0.3) * 5,
                weatherCode: isVix ? 1 : 3 // Clear vs Clouds
            };
            const normalizedMeteo = normalizeMeteoData(rawMeteo);
            window.dispatchEvent(new CustomEvent('meteo-raw', { detail: normalizedMeteo }));
        });

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

