import { useAuth } from './AuthContext';

interface HelloPageProps {
  greeting: string;
  onNavigateToAdmin: () => void;
}

export function HelloPage({ greeting, onNavigateToAdmin }: HelloPageProps) {
  const { logout } = useAuth();

  return (
    <div>
      <h1>{greeting}</h1>
      <button type="button" onClick={() => logout()}>
        Log out
      </button>
      <p>
        <button type="button" onClick={onNavigateToAdmin}>
          Manage users
        </button>
      </p>
    </div>
  );
}
