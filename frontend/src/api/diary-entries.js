export async function getDiaryEntries(status = '') {
  let url = '/api/diary-entries';

  if (status) {
    const params = new URLSearchParams({ status });
    url += `?${params.toString()}`;
  }

  const response = await fetch(url, {
    headers: {
      Accept: 'application/json',
    },
  });

  if (!response.ok) {
    throw new Error(`Ошибка загрузки дневника: HTTP ${response.status}`);
  }

  return response.json();
}

export function createDiaryEntry(entry) {
  return sendDiaryEntryRequest('/api/diary-entries', 'POST', entry);
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
    response = await fetch(url, options);
  } catch (error) {
    throw new Error(uncertainMessage, { cause: error });
  }

  if (response.ok) {
    return;
  }

  if (response.status >= 500) {
    throw new Error(uncertainMessage);
  }

  if (response.status === 404) {
    throw new Error('Запись не найдена. Обновите список.');
  }

  const problem = await response.json().catch(() => null);
  const messages = problem?.errors
    ?.map((error) => error.message)
    .join(' ');

  const fallbackMessage = isDelete
    ? 'Не удалось удалить фильм.'
    : 'Не удалось сохранить фильм.';

  throw new Error(messages || problem?.detail || fallbackMessage);
}