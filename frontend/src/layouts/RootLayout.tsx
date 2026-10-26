import { Link, Outlet } from 'react-router-dom';

export function RootLayout() {
  return (
    <div className="app-frame">
      <header className="topbar">
        <Link className="brand" to="/" aria-label="ERP Portal home">
          <span className="brand-mark" aria-hidden="true">E</span>
          <span>ERP Portal</span>
        </Link>
      </header>
      <main className="main-content">
        <Outlet />
      </main>
      <footer className="app-footer">Application foundation</footer>
    </div>
  );
}
