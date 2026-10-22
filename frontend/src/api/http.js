const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS']);

export async function apiFetch(url, options = {}) {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);

  if (!SAFE_METHODS.has(method)) {
    const csrf = await loadCsrfToken();
    headers.set(csrf.headerName, csrf.token);
  }

  return fetch(url, {
    ...options,
    method,
    headers,
    credentials: 'same-origin',
  });
}

export async function readProblemMessage(response, fallbackMessage) {
  const problem = await response.json().catch(() => null);

  const validationMessages = problem?.errors
    ?.map((error) => error.message)
    .filter(Boolean)
    .join(' ');

  return validationMessages || problem?.detail || fallbackMessage;
}

async function loadCsrfToken() {
  const response = await fetch('/api/auth/csrf', {
    method: 'GET',
    headers: {
      Accept: 'application/json',
    },
    credentials: 'same-origin',
    cache: 'no-store',
  });

  if (!response.ok) {
    throw new Error('Не удалось получить защитный токен.');
  }

  const csrf = await response.json();

  if (!csrf.headerName || !csrf.token) {
    throw new Error('Сервер вернул некорректный защитный токен.');
  }

  return csrf;
}