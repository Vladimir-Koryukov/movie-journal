import {
  createDiaryEntry,
  updateDiaryEntry,
} from './api/diary-entries.js';

export function initEntryForm({ onSaved, onBusyChange }) {
  const form = document.querySelector('#entry-form');
  const fields = form.querySelector('fieldset');
  const legend = form.querySelector('legend');
  const submitButton = form.querySelector('button[type="submit"]');
  const cancelButton = document.querySelector('#cancel-edit-button');
  const feedback = document.querySelector('#form-feedback');

  const titleInput = form.elements.namedItem('title');
  const yearInput = form.elements.namedItem('releaseYear');
  const statusInput = form.elements.namedItem('status');
  const ratingInput = form.elements.namedItem('rating');
  const notesInput = form.elements.namedItem('notes');

  let editingId = null;
  let hasUnsavedChanges = false;

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
    feedback.classList.remove('error');
  }

  function resetForm() {
    form.reset();
    titleInput.setCustomValidity('');

    editingId = null;
    hasUnsavedChanges = false;

    legend.textContent = 'Добавить фильм';
    submitButton.textContent = 'Добавить фильм';
    cancelButton.hidden = true;

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
    titleInput.focus();
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
    };

    const entryId = editingId;
    const isEditing = entryId !== null;

    setBusy(true);

    clearFeedback();
    feedback.textContent = 'Сохраняем фильм…';

    try {
      if (isEditing) {
        await updateDiaryEntry(entryId, entry);
      } else {
        await createDiaryEntry(entry);
      }
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add('error');
      console.error('Ошибка сохранения записи:', error);
      return;
    } finally {
      setBusy(false);
    }

    resetForm();

    feedback.textContent = isEditing
      ? 'Изменения сохранены.'
      : 'Фильм добавлен.';

    await onSaved();
  });

  function edit(entry) {
    if (fields.disabled || !canDiscardChanges()) {
      return;
    }

    editingId = entry.id;

    titleInput.value = entry.title;
    yearInput.value = entry.releaseYear ?? '';
    statusInput.value = entry.status;
    ratingInput.value = entry.rating ?? '';
    notesInput.value = entry.notes ?? '';

    titleInput.setCustomValidity('');
    syncRatingField();
    clearFeedback();

    hasUnsavedChanges = false;

    legend.textContent = 'Редактировать фильм';
    submitButton.textContent = 'Сохранить изменения';
    cancelButton.hidden = false;

    titleInput.focus();
  }

  syncRatingField();

  return {
    edit,
    setBusy,
    isBusy: () => fields.disabled,
    resetIfEditing(id) {
      if (editingId === id) {
        resetForm();
      }
    },
  };
}

function readOptionalNumber(input) {
  return input.value === '' ? null : input.valueAsNumber;
}