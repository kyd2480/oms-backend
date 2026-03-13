import { useEffect, useState } from 'react';
import { AuthService } from './lib/authService';
import LoginPage from './pages/LoginPage';
import SignupPage from './pages/SignupPage';
import Dashboard from './pages/Dashboard';


export default function App() {
  const [user, setUser] = useState(null);
  const [authView, setAuthView] = useState('login'); // login | signup
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const currentUser = AuthService.getCurrentUser();
    if (currentUser) setUser(currentUser);
    setLoading(false);
  }, []);

  const handleLogout = () => {
    if (confirm('로그아웃 하시겠습니까?')) {
      AuthService.logout();
      setUser(null);
      setAuthView('login');
    }
  };

  if (loading) return <div className="loading">Loading...</div>;
  if (user) return <Dashboard user={user} onLogout={handleLogout} />;

  return authView === 'signup'
    ? <SignupPage onBackToLogin={() => setAuthView('login')} />
    : <LoginPage onLogin={setUser} onGoSignup={() => setAuthView('signup')} />;
}
