import { Link } from 'react-router-dom';

export function NotFoundPage() {
  return (
    <section className="foundation-card" aria-labelledby="not-found-title">
      <p className="eyebrow">404</p>
      <h1 id="not-found-title">Page not found</h1>
      <p className="intro">This route is not part of the application foundation.</p>
      <Link to="/" className="text-link">Return to platform status</Link>
    </section>
  );
}
