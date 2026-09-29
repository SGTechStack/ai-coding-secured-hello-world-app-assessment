import { Outlet } from 'react-router'

/** The page frame every route renders into. */
export function Layout() {
  return (
    <main className="mx-auto max-w-xl p-8">
      <h1 className="text-2xl font-semibold">Secured Hello World</h1>
      <Outlet />
    </main>
  )
}
