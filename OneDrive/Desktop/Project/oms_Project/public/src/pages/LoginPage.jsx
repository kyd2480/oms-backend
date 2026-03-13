import { useState } from 'react';
import { AuthService } from '../lib/authService';

export default function LoginPage({ onLogin, onGoSignup }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    const result = await AuthService.login(username, password);
    if (result.success) onLogin(result.user);
    else setError(result.message);

    setLoading(false);
  };

  return (
    <div className="auth-bg">
      <div className="login-card">
        <div className="login-header">
          <div className="login-logo">📦</div>
          <h1 className="login-title">OMS 로그인</h1>
          <p className="login-subtitle">주문 관리 시스템에 로그인하세요</p>
        </div>

        {error && <div className="error-box">{error}</div>}

        <form className="login-form" onSubmit={handleSubmit}>
          <div className="form-group">
            <label>아이디</label>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="아이디"
              required
            />
          </div>

          <div className="form-group">
            <label>비밀번호</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="비밀번호"
              required
            />
          </div>

          <button className="login-btn" disabled={loading}>
            {loading ? '로그인 중...' : '로그인'}
          </button>

          <button
            type="button"
            className="signup-btn"
            onClick={onGoSignup}
          >
            회원가입
          </button>
        </form>

        <div className="demo-accounts">
          <div>admin / admin123</div>
          <div>manager / manager123</div>
          <div>user / user123</div>
        </div>
      </div>
    </div>
  );
}
