import { cleanup, render } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { ThemeProvider } from './ThemeProvider';

describe('ThemeProvider', () => {
  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.head.innerHTML = '<meta name="theme-color" content="#ffffff" />';
  });

  afterEach(() => cleanup());

  it('always applies dark mode even when light mode was previously saved', () => {
    window.localStorage.setItem('erp-portal-theme', 'light');

    render(<ThemeProvider><div>Workspace</div></ThemeProvider>);

    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(document.querySelector('meta[name="theme-color"]')).toHaveAttribute('content', '#172522');
    expect(window.localStorage.getItem('erp-portal-theme')).toBe('light');
  });
});
