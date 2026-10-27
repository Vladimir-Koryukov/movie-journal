import {
  createDiaryEntry,
  importCatalogDiaryEntry,
  updateDiaryEntry,
} from './api/diary-entries.js';
import { initCatalogSearch } from './catalog-search.js';
import { requiredElement } from './dom.js';

export function initEntryForm({ onSaved, onBusyChange, onCancel }) {
  const form = requiredElement('#entry-form');
  const fields = requiredElement('fieldset', form);
  const editorHeading = requiredElement('#editor-heading');
  const submitButton = requiredElement('button[type="submit"]', form);
  const cancelButton = requiredElement('#cancel-edit-button', form);
  const feedback = requiredElement('#form-feedback', form);

  const titleInput = form.elements.namedItem('title');
  const yearInput = form.elements.namedItem('releaseYear');
  const statusInput = form.elements.namedItem('status');
  const ratingInput = form.elements.namedItem('rating');
  const notesInput = form.elements.namedItem('notes');
  const genresInput = form.elements.namedItem('genres');

  let editingId = null;
  let hasUnsavedChanges = false;

  const catalogSearch = initCatalogSearch({
    onSelect(movie) {
      titleInput.value = movie.title;
      yearInput.value = movie.releaseYear ?? '';
      titleInput.readOnly = true;
      yearInput.readOnly = true;
      titleInput.setCustomValidity('');
      markAsChanged();
    },
    onClear() {
      titleInput.readOnly = false;
      yearInput.readOnly = false;
      titleInput.value = '';
      yearInput.value = '';
      markAsChanged();
      titleInput.focus();
    },
  });

  function setBusy(busy) {
    fields.disabled = busy;
    onBusyChange(busy);
  }

  function syncRatingField() {
    const isPlanned = statusInput.value === 'PLANNED';

    ratingInput.disabled = isPlanned;

    if (isPlanned) {
      ratingInput.value = '';
    }
  }

  function clearFeedback() {
    feedback.textContent = '';
    feedback.classList.remove('status-message--error');
  }

  function showFeedback(message, isError = false) {
    feedback.textContent = message;
    feedback.classList.toggle('status-message--error', isError);
  }

  function resetForm() {
    form.reset();
    titleInput.setCustomValidity('');

    editingId = null;
    hasUnsavedChanges = false;

    titleInput.readOnly = false;
    yearInput.readOnly = false;
    catalogSearch.reset();
    catalogSearch.setEditing(false);

    editorHeading.textContent = 'Добавить фильм';
    submitButton.textContent = 'Сохранить фильм';

    syncRatingField();
    clearFeedback();
  }

  function canDiscardChanges() {
    return !hasUnsavedChanges
      || window.confirm('Отменить несохранённые изменения в форме?');
  }

  function markAsChanged() {
    hasUnsavedChanges = true;
  }

  form.addEventListener('input', markAsChanged);
  form.addEventListener('change', markAsChanged);

  statusInput.addEventListener('change', syncRatingField);

  titleInput.addEventListener('input', () => {
    titleInput.setCustomValidity('');
  });

  cancelButton.addEventListener('click', () => {
    if (fields.disabled || !canDiscardChanges()) {
      return;
    }

    resetForm();
    onCancel();
  });

  form.addEventListener('submit', async (event) => {
    event.preventDefault();

    if (fields.disabled) {
      return;
    }

    const title = titleInput.value.trim();

    if (!title) {
      titleInput.setCustomValidity('Введите название фильма.');
      titleInput.reportValidity();
      return;
    }

    const entry = {
      title,
      releaseYear: readOptionalNumber(yearInput),
      status: statusInput.value,
      rating: statusInput.value === 'WATCHED'
        ? readOptionalNumber(ratingInput)
        : null,
      notes: notesInput.value.trim() || null,
      genres: readGenres(genresInput.value),
    };

    const entryId = editingId;
    const isEditing = entryId !== null;

    setBusy(true);

    showFeedback('Сохраняем фильм…');

    try {
      if (isEditing) {
        await updateDiaryEntry(entryId, entry);
      } else if (catalogSearch.getSelectedId() !== null) {
        await importCatalogDiaryEntry({
          catalogId: catalogSearch.getSelectedId(),
          status: entry.status,
          rating: entry.rating,
          notes: entry.notes,
        });
      } else {
        await createDiaryEntry(entry);
      }
    } catch (error) {
      showFeedback(error.message, true);
      console.error('Ошибка сохранения записи:', error);
      return;
    } finally {
      setBusy(false);
    }

    resetForm();

    showFeedback(
      isEditing ? 'Изменения сохранены.' : 'Фильм добавлен.'
    );

    await onSaved();
  });

  function edit(entry) {
    if (fields.disabled || !canDiscardChanges()) {
      return;
    }

    editingId = entry.id;
    catalogSearch.setEditing(true);
    titleInput.readOnly = false;
    yearInput.readOnly = false;

    titleInput.value = entry.title;
    yearInput.value = entry.releaseYear ?? '';
    statusInput.value = entry.status;
    ratingInput.value = entry.rating ?? '';
    notesInput.value = entry.notes ?? '';
    genresInput.value = entry.genres?.join(', ') ?? '';

    titleInput.setCustomValidity('');
    syncRatingField();
    clearFeedback();

    hasUnsavedChanges = false;

    editorHeading.textContent = 'Редактировать фильм';
    submitButton.textContent = 'Сохранить изменения';
    cancelButton.hidden = false;

    titleInput.focus();
  }

  syncRatingField();

  return {
    edit,
    startCreate() {
      if (fields.disabled || !canDiscardChanges()) {
        return false;
      }

      resetForm();
      return true;
    },
    requestClose() {
      if (fields.disabled || !canDiscardChanges()) {
        return false;
      }

      resetForm();
      return true;
    },
    setBusy,
    isBusy: () => fields.disabled,
    resetIfEditing(id) {
      if (editingId === id) {
        resetForm();
      }
    },
  };
}

function readGenres(value) {
  return [...new Map(value
    .split(',')
    .map((genre) => genre.trim())
    .filter(Boolean)
    .map((genre) => [genre.toLocaleLowerCase('ru-RU'), genre]))
    .values()]
    .slice(0, 10);
}

function readOptionalNumber(input) {
  return input.value === '' ? null : input.valueAsNumber;
}
