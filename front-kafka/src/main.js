import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'

/* Importacao exclusiva da folha de estilos e do motor de interatividade do Bootstrap puro */
import 'bootstrap/dist/css/bootstrap.min.css'
import 'bootstrap/dist/js/bootstrap.bundle.min.js'

const app = createApp(App)

app.use(router)

app.mount('#app')