import {
  apiFetch,
  readProblemMessage,
} from './http.js';

export async function getDiaryEntries(filters = {}) {
  const params = new URLSearchParams();

  for (const [name, value] of Object.entries(filters)) {
    if (value !== '' && value !== null && value !== undefined) {
      params.set(name, value);
    }
  }

  const query = params.toString();
  const url = query
    ? `/api/diary-entries?${query}`
    : '/api/diary-entries';

  const response = await apiFetch(url, {
    headers: {
      Accept: 'application/json, application/problem+json',
    },
  });

  handleExpiredSession(response);

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      `Ошибка загрузки дневника: HTTP ${response.status}`
    ));
  }

  return response.json();
}

export function createDiaryEntry(entry) {
  return sendDiaryEntryRequest(
    '/api/diary-entries',
    'POST',
    entry
  );
}

export function importCatalogDiaryEntry(entry) {
  return sendDiaryEntryRequest(
    '/api/diary-entries/from-catalog',
    'POST',
    entry
  );
}

export function updateDiaryEntry(id, entry) {
  return sendDiaryEntryRequest(
    `/api/diary-entries/${encodeURIComponent(id)}`,
    'PUT',
    entry
  );
}

export function deleteDiaryEntry(id) {
  return sendDiaryEntryRequest(
    `/api/diary-entries/${encodeURIComponent(id)}`,
    'DELETE'
  );
}

async function sendDiaryEntryRequest(url, method, entry) {
  const isDelete = method === 'DELETE';

  const uncertainMessage = isDelete
    ? 'Не удалось подтвердить удаление. Обновите список перед повторной попыткой.'
    : 'Не удалось подтвердить сохранение. Обновите список перед повторной попыткой.';

  const options = {
    method,
    headers: {
      Accept: 'application/json, application/problem+json',
    },
  };

  if (entry !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(entry);
  }

  let response;

  try {
    response = await apiFetch(url, options);
  } catch (error) {
    throw new Error(uncertainMessage, { cause: error });
  }

  handleExpiredSession(response);

  if (response.ok) {
    return isDelete ? undefined : response.json();
  }

  if (response.status === 404) {
    throw new Error('Запись не найдена. Обновите список.');
  }

  const fallbackMessage = isDelete
    ? 'Не удалось удалить фильм.'
    : 'Не удалось сохранить фильм.';

  throw new Error(await readProblemMessage(
    response,
    response.status >= 500 ? uncertainMessage : fallbackMessage
  ));
}

function handleExpiredSession(response) {
  if (response.status !== 401) {
    return;
  }

  window.location.reload();

  throw new Error(
    'Сеанс завершён. Выполните вход повторно.'
  );
}
