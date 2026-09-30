import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import { RegisterForm } from './RegisterForm';
import { LoginForm } from './LoginForm';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { ResetPasswordForm } from './ResetPasswordForm';
import { ChangePasswordForm } from './ChangePasswordForm';
import { HelloPage } from './HelloPage';
import { AdminUsersPage } from './AdminUsersPage';

type View = 'login' | 'register' | 'forgot-password' | 'reset-password' | 'hello' | 'admin';

function initialView(): View {
  const path = window.location.pathname;
  if (path === '/reset-password') {
    return 'reset-password';
  }
  return 'login';
}

function AppContent() {
  const { status, username, refresh } = useAuth();
  const [view, setView] = useState<View>(initialView());

  useEffect(() => {
    if (status === 'authenticated' && (view === 'login' || view === 'register')) {
      setView('hello');
    }
  }, [status, view]);

  if (status === 'loading') {
    return (
      <div className="page">
        <p className="footnote">Loading…</p>
      </div>
    );
  }

  if (status === 'password-change-required') {
    // Takes priority over any pending `view` (e.g. a stale reset-password
    // link) — the backend rejects every other authenticated request until
    // this is resolved (ForcePasswordChangeFilter).
    return (
      <div className="page">
        <ChangePasswordForm />
      </div>
    );
  }

  if (view === 'reset-password') {
    return (
      <div className="page">
        <ResetPasswordForm onNavigateToLogin={() => setView('login')} />
      </div>
    );
  }

  if (status === 'anonymous') {
    if (view === 'register') {
      return (
        <div className="page">
          <RegisterForm
            onRegistered={() => setView('login')}
            onNavigateToLogin={() => setView('login')}
          />
        </div>
      );
    }
    if (view === 'forgot-password') {
      return (
        <div className="page">
          <ForgotPasswordForm onNavigateToLogin={() => setView('login')} />
        </div>
      );
    }
    return (
      <div className="page">
        <LoginForm
          onLoggedIn={() => refresh().then(() => setView('hello'))}
          onNavigateToRegister={() => setView('register')}
          onNavigateToForgotPassword={() => setView('forgot-password')}
        />
      </div>
    );
  }

  if (view === 'admin') {
    return (
      <div className="page page--table">
        <AdminUsersPage onNavigateBack={() => setView('hello')} />
      </div>
    );
  }

  return (
    <div className="page">
      <HelloPage greeting={`Hello, ${username}`} onNavigateToAdmin={() => setView('admin')} />
    </div>
  );
}

export default function App() {
  return (
    <AuthProvider>
      {/* Single landmark for every view, so screen-reader users can jump to the content. */}
      <main>
        <AppContent />
      </main>
    </AuthProvider>
  );
}
