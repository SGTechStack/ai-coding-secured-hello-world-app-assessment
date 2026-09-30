import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from '@tanstack/react-router';
import { queryClient } from './app/query-client';
import { router } from './app/router';
import { configureDocumentNonce } from './common/csp/document-nonce';
import './index.css';

configureDocumentNonce();
const root = document.getElementById('root');
if (!root) throw new Error('Application root is missing');
createRoot(root).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
