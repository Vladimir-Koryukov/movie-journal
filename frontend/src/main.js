import './style.css';
import {
  getDiaryEntries,
  deleteDiaryEntry,
} from './api/diary-entries.js';
import { initEntryForm } from './diary-entry-form.js';

const entriesList = document.querySelector('#entries');
const feedback = document.querySelector('#feedback');
const actionFeedback = document.querySelector('#action-feedback');
const refreshButton = document.querySelector('#refresh-button');
const statusFilter = document.querySelector('#status-filter');

let loadVersion = 0;

const statusLabels = {
  PLANNED: 'Планирую посмотреть',
  WATCHED: 'Просмотрен',
};

function createEntryElement(entry) {
  const item = document.createElement('li');

  const title = document.createElement('h2');
  title.textContent = entry.title;

  const details = document.createElement('p');
  const year = entry.releaseYear ?? 'Год не указан';
  const status = statusLabels[entry.status] ?? entry.status;
  const rating = entry.rating === null
    ? 'Без оценки'
    : `Оценка: ${entry.rating}/10`;

  details.textContent = `${year} · ${status} · ${rating}`;

  item.append(title, details);

  if (entry.notes) {
    const notes = document.createElement('p');
    notes.className = 'entry-notes';
    notes.textContent = entry.notes;
    item.append(notes);
  }

    const actions = document.createElement('div');
  actions.className = 'entry-actions';

  const editButton = document.createElement('button');
  editButton.type = 'button';
  editButton.textContent = 'Редактировать';
  editButton.disabled = entryForm.isBusy();

  editButton.addEventListener('click', () => {
    entryForm.edit(entry);
  });

  const deleteButton = document.createElement('button');
  deleteButton.type = 'button';
  deleteButton.textContent = 'Удалить';
  deleteButton.className = 'button-danger';
  deleteButton.disabled = entryForm.isBusy();

  deleteButton.addEventListener('click', () => {
    handleDeleteEntry(entry);
  });

  actions.append(editButton, deleteButton);
  item.append(actions);

  return item;
}

async function handleDeleteEntry(entry) {
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

  actionFeedback.classList.remove('error');
  actionFeedback.textContent = 'Удаляем фильм…';

  try {
    await deleteDiaryEntry(entry.id);
  } catch (error) {
    actionFeedback.textContent = error.message;
    actionFeedback.classList.add('error');
    console.error('Ошибка удаления записи:', error);
    return;
  } finally {
    entryForm.setBusy(false);
  }

  entryForm.resetIfEditing(entry.id);
  actionFeedback.textContent = `Фильм «${entry.title}» удалён.`;

  await loadEntries();
}

async function loadEntries() {
  const currentVersion = ++loadVersion;
  const status = statusFilter.value;

  refreshButton.disabled = true;
  feedback.classList.remove('error');
  feedback.textContent = 'Загружаем фильмы…';
  entriesList.replaceChildren();

  try {
    const entries = await getDiaryEntries(status);

    if (currentVersion !== loadVersion) {
      return;
    }

    for (const entry of entries) {
      entriesList.append(createEntryElement(entry));
    }

    if (entries.length === 0) {
      feedback.textContent = status
        ? 'Фильмов с выбранным статусом пока нет.'
        : 'Пока нет фильмов.';
    } else {
      feedback.textContent = `Показано записей: ${entries.length}`;
    }
  } catch (error) {
    if (currentVersion !== loadVersion) {
      return;
    }

    feedback.textContent = 'Не удалось загрузить фильмы. Попробуйте ещё раз.';
    feedback.classList.add('error');
    console.error('Ошибка загрузки дневника:', error);
  } finally {
    if (currentVersion === loadVersion) {
      refreshButton.disabled = false;
    }
  }
}

refreshButton.addEventListener('click', loadEntries);
statusFilter.addEventListener('change', loadEntries);

const entryForm = initEntryForm({
  onSaved: loadEntries,
  onBusyChange(isBusy) {
    for (const button of entriesList.querySelectorAll('button')) {
      button.disabled = isBusy;
    }
  },
});

loadEntries();