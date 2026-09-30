import { useAuth } from './AuthContext';
import { Icon } from './Icon';

interface HelloPageProps {
  greeting: string;
  onNavigateToAdmin: () => void;
}

export function HelloPage({ greeting, onNavigateToAdmin }: HelloPageProps) {
  const { logout, role, username } = useAuth();
  const isAdmin = role === 'ADMIN';

  return (
    <div className="card session">
      <h1 className="session__greeting">{greeting}</h1>

      {/* Session readout: the two facts the backend returned for this session. */}
      <dl className="readout">
        <div className="readout__row">
          <dt>Username</dt>
          <dd className="readout__value">{username}</dd>
        </div>
        <div className="readout__row">
          <dt>Role</dt>
          <dd>
            <span className={`badge ${isAdmin ? 'badge--admin' : ''}`}>{role}</span>
          </dd>
        </div>
      </dl>

      {isAdmin && (
        <section className="session__section" aria-labelledby="admin-tools-heading">
          <h2 id="admin-tools-heading" className="section-label">
            Administration
          </h2>
          <p id="admin-tools-desc" className="section-desc">
            Review accounts, enable or disable access, change roles, and delete users.
          </p>
          <button
            type="button"
            className="btn btn-secondary btn-block btn-nav"
            onClick={onNavigateToAdmin}
            aria-describedby="admin-tools-desc"
          >
            <Icon name="users" />
            <span className="btn-nav__label">Manage users</span>
            <Icon name="chevronRight" />
          </button>
        </section>
      )}

      <div className="session__foot">
        <button type="button" className="btn btn-secondary btn-sm" onClick={() => logout()}>
          <Icon name="logOut" />
          Log out
        </button>
      </div>
    </div>
  );
}
