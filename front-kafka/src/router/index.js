// import { createRouter, createWebHistory } from 'vue-router'
// import HomeView from '../views/HomeView.vue'

// const router = createRouter({
//   history: createWebHistory(import.meta.env.BASE_URL),
//   routes: [
//     {
//       path: '/',
//       name: 'home',
//       component: HomeView,
//     },
//     {
//       path: '/about',
//       name: 'about',
//       // route level code-splitting
//       // this generates a separate chunk (About.[hash].js) for this route
//       // which is lazy-loaded when the route is visited.
//       component: () => import('../views/AboutView.vue'),
//     },
//   ],
// })

// export default router
import { createRouter, createWebHistory } from 'vue-router';
import TelaMapa from '../views/TelaMapa.vue';
import TelaCliente from '../views/TelaCliente.vue';
import TelaGerente from '../views/TelaGerente.vue';

const routes = [
    { path: '/', component: TelaMapa },
    { path: '/cliente', component: TelaCliente },
    { path: '/gerente', component: TelaGerente }
];

const router = createRouter({
    history: createWebHistory(),
    routes
});

export default router;
