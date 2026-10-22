import {
  apiFetch,
  readProblemMessage,
} from './http.js';

export async function getCurrentUser() {
  const response = await apiFetch('/api/auth/me', {
    headers: {
      Accept: 'application/json, application/problem+json',
    },
  });

  if (response.status === 401) {
    return null;
  }

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      'Не удалось проверить текущего пользователя.'
    ));
  }

  return response.json();
}

export async function registerUser(login, password) {
  const response = await apiFetch('/api/auth/register', {
    method: 'POST',
    headers: {
      Accept: 'application/json, application/problem+json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ login, password }),
  });

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      'Не удалось создать аккаунт.'
    ));
  }

  return response.json();
}

export async function loginUser(login, password) {
  const body = new URLSearchParams({
    login,
    password,
  });

  const response = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: {
      Accept: 'application/problem+json',
      'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
    },
    body,
  });

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      'Не удалось выполнить вход.'
    ));
  }
}

export async function logoutUser() {
  const response = await apiFetch('/api/auth/logout', {
    method: 'POST',
    headers: {
      Accept: 'application/problem+json',
    },
  });

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      'Не удалось завершить сеанс.'
    ));
  }
}