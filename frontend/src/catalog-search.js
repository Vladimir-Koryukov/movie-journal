import { searchCatalogMovies } from './api/catalog.js';
import { requiredElement } from './dom.js';

export function initCatalogSearch({ onSelect, onClear }) {
  const root = requiredElement('#catalog-picker');
  const queryInput = requiredElement('#catalog-query', root);
  const searchButton = requiredElement('#catalog-search-button', root);
  const feedback = requiredElement('#catalog-feedback', root);
  const results = requiredElement('#catalog-results', root);
  const selectedPanel = requiredElement('#catalog-selection', root);
  const selectedTitle = requiredElement('#catalog-selection-title', root);
  const clearButton = requiredElement('#catalog-clear-button', root);

  let selectedMovie = null;
  let requestVersion = 0;

  searchButton.addEventListener('click', search);
  clearButton.addEventListener('click', () => clearSelection(true));

  queryInput.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      event.preventDefault();
      search();
    }
  });

  async function search() {
    const query = queryInput.value.trim();

    if (query.length < 2) {
      showFeedback('Введите хотя бы 2 символа.', true);
      queryInput.focus();
      return;
    }

    const currentVersion = ++requestVersion;
    searchButton.disabled = true;
    results.replaceChildren();
    showFeedback('Ищем фильмы в OMDb…');

    try {
      const movies = await searchCatalogMovies(query);

      if (currentVersion !== requestVersion) {
        return;
      }

      results.append(...movies.map(createResult));
      showFeedback(
        movies.length === 0
          ? 'OMDb доступен, но фильмы по этому запросу не найдены. '
            + 'Попробуйте международное название или уточните запрос.'
          : `Найдено вариантов: ${movies.length}`
      );
    } catch (error) {
      if (currentVersion !== requestVersion) {
        return;
      }

      showFeedback(error.message, true);
      console.error('Ошибка поиска фильма:', error);
    } finally {
      if (currentVersion === requestVersion) {
        searchButton.disabled = false;
      }
    }
  }

  function createResult(movie) {
    const item = document.createElement('li');
    const button = document.createElement('button');
    const title = document.createElement('strong');
    const details = document.createElement('span');

    button.type = 'button';
    button.className = 'catalog-result';
    title.textContent = movie.title;
    details.textContent = formatDetails(movie);

    button.append(title, details);
    button.addEventListener('click', () => selectMovie(movie));
    item.append(button);

    return item;
  }

  function selectMovie(movie) {
    selectedMovie = movie;
    selectedTitle.textContent = formatSelectedTitle(movie);
    selectedPanel.hidden = false;
    results.replaceChildren();
    showFeedback('Фильм выбран. Заполните статус и заметку.');
    onSelect(movie);
  }

  function clearSelection(notify) {
    const hadSelection = selectedMovie !== null;
    selectedMovie = null;
    selectedPanel.hidden = true;

    if (notify && hadSelection) {
      showFeedback('Можно выбрать другой фильм или добавить его вручную.');
      onClear();
    }
  }

  function reset() {
    requestVersion += 1;
    queryInput.value = '';
    results.replaceChildren();
    showFeedback('');
    clearSelection(false);
  }

  function setEditing(editing) {
    root.hidden = editing;
    if (editing) {
      reset();
    }
  }

  function showFeedback(message, isError = false) {
    feedback.textContent = message;
    feedback.classList.toggle('status-message--error', isError);
  }

  return {
    getSelectedId: () => selectedMovie?.id ?? null,
    reset,
    setEditing,
  };
}

function formatDetails(movie) {
  const details = [];

  if (movie.releaseYear !== null) {
    details.push(movie.releaseYear);
  }
  return details.length === 0 ? 'Год не указан' : details.join(' · ');
}

function formatSelectedTitle(movie) {
  return movie.releaseYear === null
    ? movie.title
    : `${movie.title} (${movie.releaseYear})`;
}
