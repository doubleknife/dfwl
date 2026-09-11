import { canAny } from '../stores/auth.js';

export const permissionDirective = {
  mounted(el, binding) {
    if (!canAny(binding.value)) {
      el.remove();
    }
  },
  updated(el, binding) {
    if (!canAny(binding.value)) {
      el.remove();
    }
  }
};
