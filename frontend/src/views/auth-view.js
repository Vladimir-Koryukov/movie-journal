import {
  loginUser,
  registerUser,
} from '../api/auth.js';
import { requiredElement } from '../dom.js';

export function initAuthView() {
  const root = requiredElement('#auth-view');
  const feedback = requiredElement('#auth-feedback', root);

  const loginScreen = requiredElement('#login-view', root);
  const registerScreen = requiredElement('#register-view', root);
  const loginForm = requiredElement('#login-form', root);
  const registerForm = requiredElement('#register-form', root);

  const showRegisterButton = requiredElement(
    '#show-register-button',
    root
  );
  const showLoginButton = requiredElement('#show-login-button', root);

  showRegisterButton.addEventListener('click', () => {
    showScreen(registerScreen);
    focusLogin(registerForm);
  });

  showLoginButton.addEventListener('click', () => {
    showScreen(loginScreen);
    focusLogin(loginForm);
  });

  loginForm.addEventListener('submit', handleLogin);
  registerForm.addEventListener('submit', handleRegistration);

  return {
    show() {
      root.hidden = false;
      showScreen(loginScreen);
      focusLogin(loginForm);
    },
  };

  async function handleLogin(event) {
    event.preventDefault();

    const login = readValue(loginForm, 'login').trim();
    const password = readValue(loginForm, 'password');

    setBusy(true);
    showMessage('Выполняем вход…');

    try {
      await loginUser(login, password);
      window.location.reload();
    } catch (error) {
      showMessage(error.message, true);
      setBusy(false);
    }
  }

  async function handleRegistration(event) {
    event.preventDefault();

    const login = readValue(registerForm, 'login').trim();
    const password = readValue(registerForm, 'password');

    setBusy(true);
    showMessage('Создаём аккаунт…');

    try {
      const user = await registerUser(login, password);

      registerForm.reset();
      setBusy(false);
      showScreen(loginScreen);

      setValue(loginForm, 'login', user.login);
      requiredField(loginForm, 'password').focus();

      showMessage(
        `Аккаунт «${user.login}» создан. Теперь выполните вход.`
      );
    } catch (error) {
      showMessage(error.message, true);
      setBusy(false);
    }
  }

  function showScreen(targetScreen) {
    loginScreen.hidden = targetScreen !== loginScreen;
    registerScreen.hidden = targetScreen !== registerScreen;

    setValue(loginForm, 'password', '');
    setValue(registerForm, 'password', '');
    clearMessage();
  }

  function setBusy(busy) {
    for (const fieldset of root.querySelectorAll('fieldset')) {
      fieldset.disabled = busy;
    }
  }

  function showMessage(message, isError = false) {
    feedback.textContent = message;
    feedback.classList.toggle('status-message--error', isError);
  }

  function clearMessage() {
    feedback.textContent = '';
    feedback.classList.remove('status-message--error');
  }
}

function focusLogin(form) {
  requestAnimationFrame(() => requiredField(form, 'login').focus());
}

function readValue(form, name) {
  return requiredField(form, name).value;
}

function setValue(form, name, value) {
  requiredField(form, name).value = value;
}

function requiredField(form, name) {
  const field = form.elements.namedItem(name);

  if (!(field instanceof HTMLInputElement)) {
    throw new Error(`Не найдено поле формы: ${name}`);
  }

  return field;
}
