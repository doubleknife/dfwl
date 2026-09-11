import { ref } from 'vue';

export function useRequest() {
  const loading = ref(false);
  const error = ref('');

  async function run(task) {
    loading.value = true;
    error.value = '';
    try {
      return await task();
    } catch (caught) {
      error.value = caught.message || '请求失败';
      throw caught;
    } finally {
      loading.value = false;
    }
  }

  return { loading, error, run };
}

export async function confirmAndRun(message, task) {
  if (!window.confirm(message)) return undefined;
  return task();
}
