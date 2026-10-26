import { useLayoutEffect } from 'react';
import type { PropsWithChildren } from 'react';

function applyDarkTheme() {
  document.documentElement.dataset.theme = 'dark';
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', '#172522');
}

export function ThemeProvider({ children }: PropsWithChildren) {
  useLayoutEffect(() => {
    applyDarkTheme();
  }, []);
  return children;
}
