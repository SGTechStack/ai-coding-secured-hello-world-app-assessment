import type { Page } from '@playwright/test';

export type Outcome = { status?: number; code?: string };

/**
 * Sends one request through the SPA's own HTTP transport, from the page, so its registered Session-expired reaction
 * runs exactly as it would for a real call. The entry module is already loaded, so importing it again returns the same
 * instance; the transport is found by shape because the bundle mangles export names. This relies on the transport
 * being exported from the entry chunk (lazy route chunks import it from there).
 * ponytail: reads are driven through the Home page's Greeting (see returnToTab); the protected pages have no
 * mutating or admin call of their own yet, so those still go through here. Drive them from a page once one exists.
 */
export function callThroughApp(page: Page, method: 'GET' | 'POST', url: string): Promise<Outcome> {
  return page.evaluate(async ([method, url]) => {
    type Transport = { request: (config: object) => Promise<{ status: number }>; defaults?: { withCredentials?: boolean } };
    const entry = document.querySelector<HTMLScriptElement>('script[type="module"][src]')?.src;
    if (!entry) throw new Error('The SPA entry script is missing');
    const exports = Object.values(await import(entry) as Record<string, unknown>);
    // The bare axios default is exported too; only the app's instance sends credentials.
    const transport = exports.find((value): value is Transport =>
      typeof (value as Transport | null)?.request === 'function' && (value as Transport).defaults?.withCredentials === true);
    if (!transport) throw new Error('The SPA transport is not reachable from its entry module');
    try {
      return { status: (await transport.request({ method, url })).status };
    } catch (error) {
      const response = (error as { response?: { status: number; data?: { code?: string } } }).response;
      return { status: response?.status, code: response?.data?.code };
    }
  }, [method, url] as const);
}
