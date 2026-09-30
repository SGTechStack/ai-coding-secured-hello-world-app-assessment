import { Link } from 'react-router';
import { Card } from '../components/ui/Card';

export function NotFoundPage() {
  return (
    <Card>
      <h1>Page not found</h1>
      <p>
        <Link to="/">Go home</Link>
      </p>
    </Card>
  );
}
