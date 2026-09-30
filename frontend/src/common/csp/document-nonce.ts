const NONCE_PLACEHOLDER = '__CSP_NONCE__';

/**
 * Checks the server replaced the build-time nonce placeholder, failing bootstrap otherwise. No installed library injects
 * styles today; one that does (e.g. Radix's react-remove-scroll) needs `setNonce(nonce)` from `get-nonce` here.
 */
export function configureDocumentNonce(): void {
  const marker = document.querySelector<HTMLMetaElement>('meta[property="csp-nonce"]');
  const nonce = marker?.nonce;
  if (!nonce || nonce === NONCE_PLACEHOLDER) {
    throw new Error('CSP nonce is missing or unreplaced');
  }
}
