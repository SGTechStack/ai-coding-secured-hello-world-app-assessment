import { beforeEach, describe, expect, it } from 'vitest';

import { configureDocumentNonce } from './document-nonce';

describe('document nonce bootstrap', () => {
  beforeEach(() => {
    document.head.innerHTML = '';
  });

  it('fails safely when the nonce marker is absent', () => {
    expect(() => configureDocumentNonce()).toThrow('CSP nonce is missing or unreplaced');
  });

  it('fails safely when the build placeholder was not replaced', () => {
    document.head.innerHTML = '<meta property="csp-nonce" nonce="__CSP_NONCE__">';
    expect(() => configureDocumentNonce()).toThrow('CSP nonce is missing or unreplaced');
  });

  it('accepts a server-issued nonce', () => {
    document.head.innerHTML = '<meta property="csp-nonce" nonce="server-nonce">';
    expect(() => configureDocumentNonce()).not.toThrow();
  });
});
