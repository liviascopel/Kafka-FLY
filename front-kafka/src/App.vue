<script setup>
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <!-- <header> -->
    <div class="d-flex flex-column vh-100 overflow-hidden">
        <nav class="navbar navbar-expand-lg navbar-light bg-light border-bottom px-3">
            <div class="container-fluid">
                <!-- <router-link class="navbar-brand" to="/">Sistema de Rastreamento</router-link> -->
                <span style="font-size: 24px;" class="navbar-brand fw-bold col justify-content-start">Sistema de Rastreamento</span>
                <div class="navbar-nav col justify-content-evenly px-5">
                    <router-link class="nav-link" to="/">Mapa</router-link>
                    <router-link class="nav-link" to="/cliente">Visão Cliente</router-link>
                    <router-link class="nav-link" to="/gerente">Visão Gerente</router-link>
                </div>
                <div class="col"></div>
            </div>
        </nav>

        <main class="container flex-grow-1 mt-4 overflow-auto">
            <!-- As props sao repassadas para qualquer tela que estiver ativa no momento -->
            <!-- <router-view 
                :dadosVoo="estadoVoo" 
                :alertaAproximacao="alertaAtivo">
            </router-view> -->
            <RouterView />
        </main>
        
    </div>
  <!-- </header> -->
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
