import { reactive } from 'vue';

export const apiErrorDialog = reactive({ open: false, title: '', message: '' });

export function apiErrorContent(error, { path = '', method = 'GET', action = '' } = {}) {
  const status = Number(error?.status || 0);
  const network = error instanceof TypeError || /failed to fetch|networkerror|network request failed/i.test(error?.message || '');
  const title = path.startsWith('/auth/login') ? '登录失败'
    : status === 403 ? '权限不足'
      : status === 401 ? '登录已失效'
        : path.startsWith('/auth/password') ? '修改密码失败'
          : /\/users\/[^/]+\/reset-password/.test(path) ? '重置密码失败'
            : action === 'download' ? '下载失败'
              : action === 'upload' ? '上传失败'
                : method === 'GET' ? '加载失败'
                  : method === 'DELETE' ? '删除失败'
                    : /\/(approve|return-step|return-applicant|publish|confirm|resubmit|withdraw)(?:\?|$)/.test(path)
                      || path === '/approvals' ? '提交失败' : '保存失败';
  const raw = String(error?.message || '').replace(/[（(]?\s*(?:request\s*id|requestId|请求编号)\s*[:：]\s*[^）)\s]+[）)]?/ig, '').trim();
  const fallback = status === 403 ? '当前账号无权执行此操作'
    : status === 401 ? '请重新登录后再试'
      : status === 409 ? '当前数据已发生变化，请刷新后重试'
        : status >= 500 ? '服务暂时不可用，请稍后重试'
          : status === 400 || status === 422 ? '提交信息有误，请检查后重试'
            : '请稍后重试';
  const message = network ? '网络连接失败，请检查网络或服务状态后重试'
    : /[\u3400-\u9fff]/.test(raw) ? raw : fallback;
  return { title, message };
}

export function showApiError(error, context) {
  Object.assign(apiErrorDialog, apiErrorContent(error, context));
  apiErrorDialog.open = true;
}

export function showErrorDialog(title, message) {
  Object.assign(apiErrorDialog, { title, message, open: true });
}

export function closeApiError() { apiErrorDialog.open = false; }
