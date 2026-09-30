import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTheme } from './theme';

const mockMatchMedia = (matches: boolean) => {
  Object.defineProperty(window, 'matchMedia', {
    writable: true,
    value: vi.fn().mockReturnValue({ matches, addEventListener: vi.fn(), removeEventListener: vi.fn() }),
  });
};

function makeLocalStorage() {
  const store: Record<string, string> = {};
  return {
    getItem: (k: string) => store[k] ?? null,
    setItem: (k: string, v: string) => {
      store[k] = v;
    },
    removeItem: (k: string) => {
      delete store[k];
    },
    clear: () => {
      Object.keys(store).forEach((k) => delete store[k]);
    },
  };
}

describe('useTheme', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', makeLocalStorage());
    delete document.documentElement.dataset.mode;
    delete document.documentElement.dataset.zone;
  });

  it('initialises to stored theme from localStorage', () => {
    localStorage.setItem('demo:theme', 'dark');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
  });

  it('initialises to light when stored value is light', () => {
    localStorage.setItem('demo:theme', 'light');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('light');
  });

  it('defaults to dark when system prefers dark and nothing stored', () => {
    mockMatchMedia(true);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('dark');
  });

  it('defaults to light when system prefers light and nothing stored', () => {
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());

    expect(result.current.theme).toBe('light');
  });

  it('toggle switches from light to dark', () => {
    localStorage.setItem('demo:theme', 'light');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());

    expect(result.current.theme).toBe('dark');
  });

  it('toggle switches from dark to light', () => {
    localStorage.setItem('demo:theme', 'dark');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());

    expect(result.current.theme).toBe('light');
  });

  it('toggle sets data-mode to dark on documentElement', () => {
    localStorage.setItem('demo:theme', 'light');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());

    expect(document.documentElement.dataset.mode).toBe('dark');
  });

  it('toggle sets data-mode to light on documentElement', () => {
    localStorage.setItem('demo:theme', 'dark');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());

    expect(document.documentElement.dataset.mode).toBe('light');
  });

  it('persists new theme to localStorage on toggle', () => {
    localStorage.setItem('demo:theme', 'light');
    mockMatchMedia(false);

    const { result } = renderHook(() => useTheme());
    act(() => result.current.toggle());

    expect(localStorage.getItem('demo:theme')).toBe('dark');
  });
});
