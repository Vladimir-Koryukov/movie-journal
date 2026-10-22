import { logoutUser } from '../api/auth.js';
import {
  deleteDiaryEntry,
  getDiaryEntries,
} from '../api/diary-entries.js';
import { initEntryForm } from '../diary-entry-form.js';
import { requiredElement } from '../dom.js';

const STATUS_LABELS = {
  PLANNED: 'Запланирован',
  WATCHED: 'Просмотрен',
};

export function initDiaryView() {
  const root = requiredElement('#diary-view');
  const currentLogin = requiredElement('#current-login', root);
  const logoutButton = requiredElement('#logout-button', root);
  const entriesList = requiredElement('#entries', root);
  const feedback = requiredElement('#feedback', root);
  const actionFeedback = requiredElement('#action-feedback', root);
  const refreshButton = requiredElement('#refresh-button', root);
  const statusFilter = requiredElement('#status-filter', root);

  let loadVersion = 0;

  const entryForm = initEntryForm({
    onSaved: loadEntries,
    onBusyChange: setEntryActionsDisabled,
  });

  logoutButton.addEventListener('click', handleLogout);
  refreshButton.addEventListener('click', loadEntries);
  statusFilter.addEventListener('change', loadEntries);

  return {
    async show(user) {
      currentLogin.textContent = user.login;
      root.hidden = false;
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

    await loadEntries();
  }

  async function loadEntries() {
    const currentVersion = ++loadVersion;

    refreshButton.disabled = true;
    clearActionMessage();
    showListMessage('Загружаем фильмы…');
    entriesList.replaceChildren();

    try {
      const entries = await getDiaryEntries(statusFilter.value);

      if (currentVersion !== loadVersion) {
        return;
      }

      entriesList.append(...entries.map(createEntryElement));

      const message = entries.length === 0
        ? statusFilter.value
          ? 'Фильмов с выбранным статусом пока нет.'
          : 'Пока нет фильмов. Добавьте первый фильм слева.'
        : formatEntryCount(entries.length);

      showListMessage(message);
    } catch (error) {
      if (currentVersion !== loadVersion) {
        return;
      }

      showListMessage(
        'Не удалось загрузить фильмы. Попробуйте ещё раз.',
        true
      );
      console.error('Ошибка загрузки дневника:', error);
    } finally {
      if (currentVersion === loadVersion) {
        refreshButton.disabled = false;
      }
    }
  }

  function createEntryElement(entry) {
    const item = document.createElement('li');
    item.className = 'movie-card';

    const content = document.createElement('div');
    content.className = 'movie-card__content';

    if (entry.posterUrl) {
      const poster = document.createElement('img');
      poster.className = 'movie-card__poster';
      poster.src = entry.posterUrl;
      poster.alt = `Постер фильма «${entry.title}»`;
      poster.loading = 'lazy';
      item.classList.add('movie-card--with-poster');
      item.append(poster);
    }

    const header = document.createElement('div');
    header.className = 'movie-card__header';

    const title = document.createElement('h3');
    title.textContent = entry.title;

    const status = document.createElement('span');
    status.className = `status-badge status-badge--${entry.status.toLowerCase()}`;
    status.textContent = STATUS_LABELS[entry.status] ?? entry.status;

    const badges = document.createElement('div');
    badges.className = 'movie-card__badges';

    if (entry.catalogProvider) {
      const source = document.createElement('span');
      source.className = 'source-badge';
      source.textContent = entry.catalogProvider.toUpperCase();
      badges.append(source);
    }

    badges.append(status);
    header.append(title, badges);

    const metadata = document.createElement('p');
    metadata.className = 'movie-card__metadata';
    metadata.textContent = formatMetadata(entry);

    content.append(header, metadata);

    if (entry.overview) {
      const overview = document.createElement('p');
      overview.className = 'movie-card__overview';
      overview.textContent = entry.overview;
      content.append(overview);
    }

    if (entry.genres?.length > 0) {
      const genres = document.createElement('p');
      genres.className = 'movie-card__genres';
      genres.textContent = `Жанры: ${entry.genres.join(', ')}`;
      content.append(genres);
    }

    if (entry.cast?.length > 0) {
      const cast = document.createElement('p');
      cast.className = 'movie-card__cast';
      cast.textContent = `В ролях: ${entry.cast
        .map(formatCastMember)
        .join(', ')}`;
      content.append(cast);
    }

    if (entry.notes) {
      const notes = document.createElement('p');
      notes.className = 'movie-card__notes';
      notes.textContent = entry.notes;
      content.append(notes);
    }

    const actions = document.createElement('div');
    actions.className = 'movie-card__actions';

    const editButton = createActionButton('Редактировать');
    editButton.addEventListener('click', () => entryForm.edit(entry));

    const deleteButton = createActionButton('Удалить', true);
    deleteButton.addEventListener('click', () => handleDelete(entry));

    actions.append(editButton, deleteButton);
    content.append(actions);
    item.append(content);

    return item;
  }

  function createActionButton(label, isDangerous = false) {
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = label;
    button.className = isDangerous
      ? 'button button--danger'
      : 'button button--secondary';
    button.disabled = entryForm.isBusy();
    return button;
  }

  function setEntryActionsDisabled(disabled) {
    for (const button of entriesList.querySelectorAll('button')) {
      button.disabled = disabled;
    }
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

function formatCastMember(member) {
  return member.name;
}

function formatMetadata(entry) {
  const year = entry.releaseYear ?? 'Год не указан';
  const rating = entry.rating === null
    ? 'Без оценки'
    : `Оценка ${entry.rating}/10`;

  return `${year} · ${rating}`;
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
