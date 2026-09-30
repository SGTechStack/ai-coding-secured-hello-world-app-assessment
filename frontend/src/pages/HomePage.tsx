import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { authApi } from '../api/auth';
import { useAuth } from '../auth/useAuth';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { describeError } from '../lib/errors';

/** Story 5: the greeting comes from the protected endpoint, proving the session works. */
export function HomePage() {
  const { isAdmin } = useAuth();
  const hello = useQuery({ queryKey: ['hello'], queryFn: authApi.hello });

  return (
    <div className="stack">
      <Card>
        {hello.isPending ? <p className="muted">Loading your greeting…</p> : null}
        {hello.isError ? <Alert tone="error">{describeError(hello.error)}</Alert> : null}
        {hello.data ? <p className="greeting">{hello.data.message}</p> : null}
        <p className="muted">
          This message was returned by <code>GET /api/hello</code> using your server-side session.
        </p>
      </Card>
      {isAdmin ? (
        <Card>
          <h2>Administration</h2>
          <p>
            You are an administrator. <Link to="/admin/users">Manage user accounts</Link>.
          </p>
        </Card>
      ) : null}
    </div>
  );
}
