import { useState } from 'react';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';
import { AuthService } from '../lib/authService';

export default function SignupPage({ onBackToLogin }) {
  const [form, setForm] = useState({
    username: '',
    password: '',
    password2: '',
    name: '',
    email: '',
  });

  const [error, setError] = useState('');
  const [ok, setOk] = useState('');
  const [loading, setLoading] = useState(false);

  const set = (k, v) => {
    setForm((prev) => ({ ...prev, [k]: v }));
  };

  const validate = () => {
    if (!form.username || !form.password || !form.name || !form.email) {
      return '필수 항목(아이디/비밀번호/이름/이메일)을 입력하세요.';
    }

    if (form.username.trim().length < 3) {
      return '아이디는 3자 이상이어야 합니다.';
    }

    if (form.password.length < 6) {
      return '비밀번호는 6자 이상이어야 합니다.';
    }

    if (form.password !== form.password2) {
      return '비밀번호가 일치하지 않습니다.';
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(form.email)) {
      return '올바른 이메일 형식이 아닙니다.';
    }

    return '';
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setOk('');

    const message = validate();
    if (message) {
      setError(message);
      return;
    }

    setLoading(true);

    try {
      const result = await AuthService.signup(
        form.username.trim(),
        form.password,
        form.name.trim(),
        form.email.trim()
      );

      if (result.success) {
        setOk('회원가입이 완료되었습니다.');

        setTimeout(() => {
          onBackToLogin?.();
        }, 500);
      } else {
        setError(result.message || '회원가입에 실패했습니다.');
      }
    } catch (err) {
      console.error('회원가입 처리 오류:', err);
      setError('회원가입 처리 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-bg">
      <div className="auth-card" style={{ maxWidth: 560 }}>
        <div className="auth-header">
          <div className="auth-logo">OMS</div>
          <h1 className="auth-title">회원가입</h1>
          <p className="auth-subtitle">기본 정보를 입력하세요</p>
        </div>

        {error && <div className="notice err">{error}</div>}
        {ok && <div className="notice ok">{ok}</div>}

        <form className="auth-form" onSubmit={handleSubmit}>
          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">아이디</label>
              <Input
                value={form.username}
                onChange={(v) => set('username', v)}
                placeholder="아이디"
                width={260}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label className="form-label">이름</label>
              <Input
                value={form.name}
                onChange={(v) => set('name', v)}
                placeholder="이름"
                width={260}
                disabled={loading}
              />
            </div>
          </div>

          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">비밀번호</label>
              <Input
                type="password"
                value={form.password}
                onChange={(v) => set('password', v)}
                placeholder="비밀번호"
                width={260}
                disabled={loading}
              />
            </div>

            <div className="form-group">
              <label className="form-label">비밀번호 확인</label>
              <Input
                type="password"
                value={form.password2}
                onChange={(v) => set('password2', v)}
                placeholder="비밀번호 확인"
                width={260}
                disabled={loading}
              />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">이메일</label>
            <Input
              value={form.email}
              onChange={(v) => set('email', v)}
              placeholder="example@domain.com"
              width={540}
              disabled={loading}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
            <Button type="submit" variant="primary" disabled={loading}>
              {loading ? '가입 처리 중...' : '가입하기'}
            </Button>

            <Button
              type="button"
              variant="ghost"
              onClick={onBackToLogin}
              disabled={loading}
            >
              로그인으로
            </Button>
          </div>
        </form>
      </div>
    </div>
  );
}