import { Outlet } from 'react-router-dom';
import HealthBadge from './HealthBadge';
import NavBar from './NavBar';

export default function Layout() {
  return (
    <>
      <header className="app-header">
        <h1>Secured Hello World</h1>
        <HealthBadge />
        <NavBar />
      </header>
      <main>
        <Outlet />
      </main>
    </>
  );
}
