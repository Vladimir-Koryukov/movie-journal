export function requiredElement(selector, root = document) {
  const element = root.querySelector(selector);

  if (element === null) {
    throw new Error(`Не найден обязательный элемент: ${selector}`);
  }

  return element;
}
