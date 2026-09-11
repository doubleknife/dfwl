import { createApp } from 'vue';
import App from './App.vue';
import { permissionDirective } from './directives/permission.js';
import './style.css';

createApp(App)
  .directive('permission', permissionDirective)
  .mount('#app');
