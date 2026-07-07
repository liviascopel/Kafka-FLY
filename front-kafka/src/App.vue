<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { startSSE, stopSSE, startSimulation, stopSimulation, getSimulationStatus } from '@/services/sse'

const isConnected = ref(false)
const isSimulating = ref(false)
const serverUrl = ref(import.meta.env.VITE_SSE_URL || 'http://localhost:8080/events')

const handleStatusChange = (event) => {
  isConnected.value = event.detail.connected
  isSimulating.value = event.detail.simulating
}

const toggleSimulation = () => {
  if (isSimulating.value) {
    stopSimulation()
    // Reconnect to real SSE
    startSSE(serverUrl.value)
  } else {
    startSimulation()
  }
}

const reconnectSSE = () => {
  startSSE(serverUrl.value)
}

onMounted(() => {
  // Listen to connection status events
  window.addEventListener('sse-status', handleStatusChange)
  
  // Start standard SSE connection by default
  startSSE(serverUrl.value)
  
  // As a fallback helper, if connection fails, user can enable simulation.
  // We can also auto-start simulation for convenience if they want, but let's default to real SSE connection.
})

onUnmounted(() => {
  window.removeEventListener('sse-status', handleStatusChange)
  stopSSE()
  stopSimulation()
})
</script>

<template>
  <div class="d-flex flex-column vh-100 overflow-hidden app-container">
    <nav class="navbar navbar-expand-lg border-bottom px-4 py-3 custom-navbar shadow-sm">
      <div class="container-fluid d-flex align-items-center justify-content-between">
        
        <!-- Logo / Title -->
        <div class="d-flex align-items-center">
          <span class="logo-icon me-2">✈️</span>
          <span class="navbar-brand fw-bold mb-0 text-gradient">Kafka-FLY</span>
          <span class="ms-3 text-muted d-none d-md-inline" style="font-size: 0.85rem; border-left: 1px solid #dee2e6; padding-left: 15px;">
            Rastreamento de Voos em Tempo Real
          </span>
        </div>

        <!-- Navigation Links -->
        <div class="navbar-nav d-flex flex-row gap-2 px-2 my-2 my-lg-0 justify-content-center">
          <router-link class="nav-link px-3 py-2 rounded-pill transition-all" to="/" active-class="active-nav">
            🗺️ Mapa
          </router-link>
          <router-link class="nav-link px-3 py-2 rounded-pill transition-all" to="/cliente" active-class="active-nav">
            👤 Passageiro
          </router-link>
          <router-link class="nav-link px-3 py-2 rounded-pill transition-all" to="/gerente" active-class="active-nav">
            💼 Gerente
          </router-link>
        </div>

        <!-- SSE connection and Simulator Dashboard -->
        <div class="d-flex align-items-center gap-3">
          <!-- Connection Status Indicator -->
          <div class="d-flex align-items-center gap-2 px-3 py-2 rounded-pill bg-light border">
            <span 
              class="status-dot" 
              :class="{ 
                'bg-success animate-pulse': isConnected && !isSimulating, 
                'bg-warning animate-pulse': isSimulating, 
                'bg-danger': !isConnected && !isSimulating 
              }"
            ></span>
            <span class="fw-semibold text-dark" style="font-size: 0.85rem;">
              {{ isSimulating ? 'Simulador Ativo' : isConnected ? 'Servidor Conectado' : 'Servidor Offline' }}
            </span>
          </div>

          <!-- Quick Controls -->
          <button 
            @click="toggleSimulation" 
            class="btn btn-sm rounded-pill px-3 transition-all"
            :class="isSimulating ? 'btn-outline-danger' : 'btn-outline-warning'"
            style="font-size: 0.8rem;"
          >
            {{ isSimulating ? 'Parar Simulação' : 'Simular Dados' }}
          </button>
          
          <button 
            v-if="!isConnected && !isSimulating"
            @click="reconnectSSE" 
            class="btn btn-sm btn-primary rounded-pill px-3 transition-all"
            style="font-size: 0.8rem;"
          >
            Reconectar SSE
          </button>
        </div>

      </div>
    </nav>

    <!-- Main Content Area -->
    <main class="flex-grow-1 overflow-auto bg-main-layout">
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
/* header {
  line-height: 1.5;
  max-height: 100vh;
}

.logo {
  display: block;
  margin: 0 auto 2rem;
}

nav {
  width: 100%;
  font-size: 12px;
  text-align: center;
  margin-top: 2rem;
}

nav a.router-link-exact-active {
  color: var(--color-text);
}

nav a.router-link-exact-active:hover {
  background-color: transparent;
}

nav a {
  display: inline-block;
  padding: 0 1rem;
  border-left: 1px solid var(--color-border);
}

nav a:first-of-type {
  border: 0;
}

@media (min-width: 1024px) {
  header {
    display: flex;
    place-items: center;
    padding-right: calc(var(--section-gap) / 2);
  }

  .logo {
    margin: 0 2rem 0 0;
  }

  header .wrapper {
    display: flex;
    place-items: flex-start;
    flex-wrap: wrap;
  }

  nav {
    text-align: left;
    margin-left: -1rem;
    font-size: 1rem;

    padding: 1rem 0;
    margin-top: 1rem;
  }
} */
</style>
