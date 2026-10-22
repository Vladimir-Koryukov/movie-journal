import {
  apiFetch,
  readProblemMessage,
} from './http.js';

export async function searchCatalogMovies(query) {
  const params = new URLSearchParams({ query });
  const response = await apiFetch(
    `/api/catalog/movies?${params.toString()}`,
    {
      headers: {
        Accept: 'application/json, application/problem+json',
      },
    }
  );

  if (response.status === 401) {
    window.location.reload();
    throw new Error('Сеанс завершён. Выполните вход повторно.');
  }

  if (!response.ok) {
    throw new Error(await readProblemMessage(
      response,
      'Не удалось выполнить поиск в OMDb.'
    ));
  }

  return response.json();
}
