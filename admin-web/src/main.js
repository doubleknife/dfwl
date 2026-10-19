import { createApp } from 'vue';
import App from './App.vue';
import { permissionDirective } from './directives/permission.js';
import './style.css';
import './design/ui-final.css';

createApp(App)
  .directive('permission', permissionDirective)
  .mount('#app');
