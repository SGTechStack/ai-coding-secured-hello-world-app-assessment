import { useEffect, useState } from 'react';
import { AuthProvider, useAuth } from './AuthContext';
import { RegisterForm } from './RegisterForm';
import { LoginForm } from './LoginForm';
import { ForgotPasswordForm } from './ForgotPasswordForm';
import { ResetPasswordForm } from './ResetPasswordForm';
import { HelloPage } from './HelloPage';
import { AdminUsersPage } from './AdminUsersPage';
import { api } from './api';

type View = 'login' | 'register' | 'forgot-password' | 'reset-password' | 'hello' | 'admin';

function initialView(): View {
  const path = window.location.pathname;
  if (path === '/reset-password') {
    return 'reset-password';
  }
  return 'login';
}

function AppContent() {
  const { status, refresh } = useAuth();
  const [view, setView] = useState<View>(initialView());
  const [greeting, setGreeting] = useState('');

  useEffect(() => {
    if (status === 'authenticated' && (view === 'login' || view === 'register')) {
      setView('hello');
    }
  }, [status, view]);

  useEffect(() => {
    if (view === 'hello') {
      api.hello().then(setGreeting).catch(() => {});
    }
  }, [view]);

  if (status === 'loading') {
    return <p>Loading…</p>;
  }

  if (view === 'reset-password') {
    return <ResetPasswordForm onNavigateToLogin={() => setView('login')} />;
  }

  if (status === 'anonymous') {
    if (view === 'register') {
      return (
        <RegisterForm
          onRegistered={() => setView('login')}
          onNavigateToLogin={() => setView('login')}
        />
      );
    }
    if (view === 'forgot-password') {
      return <ForgotPasswordForm onNavigateToLogin={() => setView('login')} />;
    }
    return (
      <LoginForm
        onLoggedIn={() => refresh().then(() => setView('hello'))}
        onNavigateToRegister={() => setView('register')}
        onNavigateToForgotPassword={() => setView('forgot-password')}
      />
    );
  }

  if (view === 'admin') {
    return <AdminUsersPage onNavigateBack={() => setView('hello')} />;
  }

  return <HelloPage greeting={greeting} onNavigateToAdmin={() => setView('admin')} />;
}

export default function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}
