export function isMac() {
  if (typeof navigator === 'undefined') return false;
  return /Mac|iPhone|iPad|iPod/.test(navigator.userAgent);
}

export function modKey() {
  return isMac() ? '⌘' : 'Ctrl';
}
