import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider, createBrowserRouter } from 'react-router'
import { wireUnauthorizedRedirect } from './app/router.ts'
import { routes } from './app/routes.tsx'
import '@fontsource-variable/inter'
import './index.css'

const router = createBrowserRouter(routes)
wireUnauthorizedRedirect(router)

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <RouterProvider router={router} />
  </StrictMode>,
)
