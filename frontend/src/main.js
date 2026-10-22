import './style.css';

import { getCurrentUser } from './api/auth.js';
import { requiredElement } from './dom.js';
import { initAuthView } from './views/auth-view.js';
import { initDiaryView } from './views/diary-view.js';

const startupFeedback = requiredElement('#startup-feedback');
const authView = initAuthView();
const diaryView = initDiaryView();

initialize();

async function initialize() {
  try {
    const user = await getCurrentUser();

    startupFeedback.hidden = true;

    if (user === null) {
      authView.show();
      return;
    }

    await diaryView.show(user);
  } catch (error) {
    startupFeedback.textContent =
      'Не удалось связаться с сервером. Обновите страницу.';
    startupFeedback.classList.add('status-message--error');

    console.error('Ошибка инициализации приложения:', error);
  }
}
