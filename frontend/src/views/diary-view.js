import { logoutUser } from '../api/auth.js';
import {
  deleteDiaryEntry,
  getDiaryEntries,
} from '../api/diary-entries.js';
import { initEntryForm } from '../diary-entry-form.js';
import { requiredElement } from '../dom.js';

const STATUS_LABELS = {
  PLANNED: 'В планах',
  WATCHED: 'Просмотрен',
};

export function initDiaryView() {
  const root = requiredElement('#diary-view');
  const currentLogin = requiredElement('#current-login', root);
  const logoutButton = requiredElement('#logout-button', root);

  const collectionScreen = requiredElement('#collection-screen', root);
  const editorScreen = requiredElement('#editor-screen', root);
  const detailScreen = requiredElement('#detail-screen', root);

  const addEntryButton = requiredElement('#add-entry-button', root);
  const editorBackButton = requiredElement('#editor-back-button', root);
  const detailBackButton = requiredElement('#detail-back-button', root);
  const movieDetail = requiredElement('#movie-detail', root);

  const filtersForm = requiredElement('#filters-form', root);
  const queryInput = requiredElement('#library-query', filtersForm);
  const resetFiltersButton = requiredElement(
    '#reset-filters-button',
    filtersForm
  );

  const entriesList = requiredElement('#entries', root);
  const feedback = requiredElement('#feedback', root);
  const actionFeedback = requiredElement('#action-feedback', root);
  const refreshButton = requiredElement('#refresh-button', root);

  let entries = [];
  let loadVersion = 0;
  let searchTimer = null;

  const entryForm = initEntryForm({
    onSaved: async () => {
      showScreen(collectionScreen);
      await loadEntries();
    },
    onCancel: () => showScreen(collectionScreen),
    onBusyChange: setEntryActionsDisabled,
  });

  logoutButton.addEventListener('click', handleLogout);
  addEntryButton.addEventListener('click', () => {
    if (entryForm.startCreate()) {
      showScreen(editorScreen);
    }
  });
  editorBackButton.addEventListener('click', () => {
    if (entryForm.requestClose()) {
      showScreen(collectionScreen);
    }
  });
  detailBackButton.addEventListener('click', () => {
    showScreen(collectionScreen);
  });
  refreshButton.addEventListener('click', loadEntries);
  filtersForm.addEventListener('submit', (event) => {
    event.preventDefault();
    loadEntries();
  });
  filtersForm.elements.namedItem('sort').addEventListener(
    'change',
    loadEntries
  );
  resetFiltersButton.addEventListener('click', () => {
    filtersForm.reset();
    loadEntries();
  });
  queryInput.addEventListener('input', () => {
    window.clearTimeout(searchTimer);
    searchTimer = window.setTimeout(loadEntries, 350);
  });

  return {
    async show(user) {
      currentLogin.textContent = user.login;
      root.hidden = false;
      showScreen(collectionScreen, false);
      await loadEntries();
    },
  };

  async function handleLogout() {
    logoutButton.disabled = true;

    try {
      await logoutUser();
      window.location.reload();
    } catch (error) {
      showActionMessage(error.message, true);
      logoutButton.disabled = false;
    }
  }

  async function handleDelete(entry) {
    if (entryForm.isBusy()) {
      return;
    }

    const confirmed = window.confirm(
      `Удалить фильм «${entry.title}» вместе с оценкой и заметкой?`
    );

    if (!confirmed) {
      return;
    }

    entryForm.setBusy(true);
    showActionMessage('Удаляем фильм…');

    try {
      await deleteDiaryEntry(entry.id);
    } catch (error) {
      showActionMessage(error.message, true);
      console.error('Ошибка удаления записи:', error);
      return;
    } finally {
      entryForm.setBusy(false);
    }

    entryForm.resetIfEditing(entry.id);
    showActionMessage(`Фильм «${entry.title}» удалён.`);
    showScreen(collectionScreen);
    await loadEntries(false);
  }

  async function loadEntries(clearAction = true) {
    const currentVersion = ++loadVersion;

    refreshButton.disabled = true;
    if (clearAction) {
      clearActionMessage();
    }
    showListMessage('Загружаем фильмы…');

    try {
      const loadedEntries = await getDiaryEntries(readFilters());

      if (currentVersion !== loadVersion) {
        return;
      }

      entries = loadedEntries;
      renderEntries();
      showListMessage(formatListMessage(entries.length));
    } catch (error) {
      if (currentVersion !== loadVersion) {
        return;
      }

      entries = [];
      entriesList.replaceChildren();
      showListMessage(error.message, true);
      console.error('Ошибка загрузки дневника:', error);
    } finally {
      if (currentVersion === loadVersion) {
        refreshButton.disabled = false;
      }
    }
  }

  function readFilters() {
    return {
      query: queryInput.value.trim(),
      status: filtersForm.elements.namedItem('status').value,
      sort: filtersForm.elements.namedItem('sort').value,
      yearFrom: readOptionalNumber(
        filtersForm.elements.namedItem('yearFrom')
      ),
      yearTo: readOptionalNumber(
        filtersForm.elements.namedItem('yearTo')
      ),
      ratingFrom: readOptionalNumber(
        filtersForm.elements.namedItem('ratingFrom')
      ),
      ratingTo: readOptionalNumber(
        filtersForm.elements.namedItem('ratingTo')
      ),
    };
  }

  function renderEntries() {
    entriesList.replaceChildren(...entries.map(createEntryElement));

    if (entries.length === 0) {
      entriesList.append(createEmptyState());
    }
  }

  function createEntryElement(entry) {
    const item = createElement('li', 'movie-card');
    const card = createElement('article', 'movie-card__button');
    card.tabIndex = 0;
    card.setAttribute('role', 'button');
    card.setAttribute('aria-label', `Открыть карточку фильма «${entry.title}»`);

    const visual = createPoster(entry, 'movie-card__poster');
    const body = createElement('div', 'movie-card__body');
    const badges = createElement('div', 'movie-card__badges');

    badges.append(createStatusBadge(entry));
    if (entry.catalogProvider) {
      badges.append(createSourceBadge(entry.catalogProvider));
    }

    const title = createElement('h3', '', entry.title);
    const metadata = createElement(
      'p',
      'movie-card__metadata',
      formatMetadata(entry)
    );
    const overview = createElement(
      'p',
      'movie-card__overview',
      entry.overview || 'Описание пока не добавлено.'
    );

    body.append(badges, title, metadata, overview);

    if (entry.genres?.length > 0) {
      const genres = createElement('div', 'tag-list tag-list--compact');
      genres.append(...entry.genres.slice(0, 3).map(createTag));
      body.append(genres);
    }

    const hint = createElement('span', 'movie-card__hint', 'Подробнее →');
    body.append(hint);
    card.append(visual, body);
    item.append(card);

    card.addEventListener('click', () => showDetail(entry));
    card.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' || event.key === ' ') {
        event.preventDefault();
        showDetail(entry);
      }
    });

    return item;
  }

  function createEmptyState() {
    const item = createElement('li', 'empty-state panel');
    const mark = createElement('span', 'empty-state__mark', '◇');
    const title = createElement(
      'strong',
      '',
      hasActiveFilters()
        ? 'По выбранным условиям ничего не найдено'
        : 'Коллекция пока пуста'
    );
    const description = createElement(
      'p',
      '',
      hasActiveFilters()
        ? 'Измените запрос или сбросьте фильтры.'
        : 'Добавьте первый фильм вручную или найдите его в OMDb.'
    );

    item.append(mark, title, description);
    return item;
  }

  function showDetail(entry) {
    movieDetail.replaceChildren(createDetailContent(entry));
    showScreen(detailScreen);
  }

  function createDetailContent(entry) {
    const fragment = document.createDocumentFragment();
    const hero = createElement('div', 'movie-detail__hero');
    const poster = createPoster(entry, 'movie-detail__poster');
    const summary = createElement('div', 'movie-detail__summary');
    const badges = createElement('div', 'movie-detail__badges');

    badges.append(createStatusBadge(entry));
    if (entry.catalogProvider) {
      badges.append(createSourceBadge(entry.catalogProvider));
    }

    const title = createElement('h2', '', entry.title);
    const metadata = createElement(
      'p',
      'movie-detail__metadata',
      formatMetadata(entry)
    );
    const overview = createElement(
      'p',
      'movie-detail__overview',
      entry.overview || 'Для этого фильма подробное описание пока не добавлено.'
    );

    summary.append(badges, title, metadata, overview);
    hero.append(poster, summary);
    fragment.append(hero);

    const facts = createElement('div', 'movie-detail__facts');
    facts.append(
      createFilterSection('Жанры', entry.genres, 'Жанры не указаны'),
      createFilterSection(
        'В ролях',
        entry.cast?.map((member) => member.name),
        'Актёрский состав не указан'
      )
    );
    fragment.append(facts);

    const notes = createElement('section', 'movie-detail__notes');
    notes.append(
      createElement('span', 'movie-detail__label', 'Личная заметка'),
      createElement('p', '', entry.notes || 'Заметок об этом фильме пока нет.')
    );
    fragment.append(notes);

    const actions = createElement('div', 'movie-detail__actions');
    const editButton = createActionButton('Редактировать');
    const deleteButton = createActionButton('Удалить', true);

    editButton.addEventListener('click', () => {
      entryForm.edit(entry);
      showScreen(editorScreen);
    });
    deleteButton.addEventListener('click', () => handleDelete(entry));

    actions.append(editButton, deleteButton);
    fragment.append(actions);
    return fragment;
  }

  function createFilterSection(title, values, emptyText) {
    const section = createElement('section', 'movie-detail__fact');
    section.append(createElement('span', 'movie-detail__label', title));

    if (!values || values.length === 0) {
      section.append(createElement('p', 'movie-detail__empty', emptyText));
      return section;
    }

    const list = createElement('div', 'tag-list');
    for (const value of values) {
      const button = createElement('button', 'tag tag--button', value);
      button.type = 'button';
      button.addEventListener('click', () => applyQuickSearch(value));
      list.append(button);
    }

    section.append(list);
    return section;
  }

  function applyQuickSearch(value) {
    queryInput.value = value;
    showScreen(collectionScreen);
    loadEntries();
  }

  function createPoster(entry, className) {
    if (entry.posterUrl) {
      const poster = document.createElement('img');
      poster.className = className;
      poster.src = entry.posterUrl;
      poster.alt = `Постер фильма «${entry.title}»`;
      poster.loading = 'lazy';
      return poster;
    }

    const fallback = createElement(
      'div',
      `${className} poster-fallback`,
      'MJ'
    );
    fallback.setAttribute('aria-label', 'Постер отсутствует');
    return fallback;
  }

  function createStatusBadge(entry) {
    return createElement(
      'span',
      `status-badge status-badge--${entry.status.toLowerCase()}`,
      STATUS_LABELS[entry.status] ?? entry.status
    );
  }

  function createSourceBadge(provider) {
    return createElement('span', 'source-badge', provider.toUpperCase());
  }

  function createActionButton(label, isDangerous = false) {
    const button = createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.className = isDangerous
      ? 'button button--danger'
      : 'button button--secondary';
    button.disabled = entryForm.isBusy();
    return button;
  }

  function setEntryActionsDisabled(disabled) {
    for (const element of root.querySelectorAll(
      '.movie-detail__actions button, .movie-card__button'
    )) {
      if (element instanceof HTMLButtonElement) {
        element.disabled = disabled;
      } else {
        element.setAttribute('aria-disabled', String(disabled));
      }
    }
  }

  function showScreen(screen, scroll = true) {
    collectionScreen.hidden = screen !== collectionScreen;
    editorScreen.hidden = screen !== editorScreen;
    detailScreen.hidden = screen !== detailScreen;

    if (scroll) {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  function hasActiveFilters() {
    return Object.values(readFilters()).some(
      (value) => value !== '' && value !== null
    );
  }

  function formatListMessage(count) {
    if (count === 0) {
      return hasActiveFilters()
        ? 'Совпадений не найдено'
        : 'Добавьте первый фильм в коллекцию';
    }

    return formatEntryCount(count);
  }

  function showListMessage(message, isError = false) {
    setMessage(feedback, message, isError);
  }

  function showActionMessage(message, isError = false) {
    setMessage(actionFeedback, message, isError);
  }

  function clearActionMessage() {
    setMessage(actionFeedback, '');
  }
}

function createElement(tagName, className = '', text = '') {
  const element = document.createElement(tagName);
  element.className = className;
  element.textContent = text;
  return element;
}

function createTag(value) {
  return createElement('span', 'tag', value);
}

function readOptionalNumber(input) {
  return input.value === '' ? null : input.valueAsNumber;
}

function formatMetadata(entry) {
  const details = [entry.releaseYear ?? 'Год не указан'];

  if (entry.rating !== null) {
    details.push(`★ ${entry.rating}/10`);
  } else {
    details.push('Без оценки');
  }

  return details.join(' · ');
}

function formatEntryCount(count) {
  const lastTwoDigits = count % 100;
  const lastDigit = count % 10;

  if (lastTwoDigits >= 11 && lastTwoDigits <= 14) {
    return `Найдено фильмов: ${count}`;
  }
  if (lastDigit === 1) {
    return `Найден ${count} фильм`;
  }
  if (lastDigit >= 2 && lastDigit <= 4) {
    return `Найдено ${count} фильма`;
  }

  return `Найдено ${count} фильмов`;
}

function setMessage(element, message, isError = false) {
  element.textContent = message;
  element.classList.toggle('status-message--error', isError);
}
