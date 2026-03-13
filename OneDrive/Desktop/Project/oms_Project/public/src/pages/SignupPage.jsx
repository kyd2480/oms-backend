import { useState } from 'react';
import Button from '../components/ui/Button';
import Input from '../components/ui/Input';

export default function SignupPage({ onBackToLogin }) {
  const [form, setForm] = useState({ username: '', password: '', password2: '', name: '', email: '' });
  const [error, setError] = useState('');
  const [ok, setOk] = useState('');

  const set = (k, v) => setForm((p) => ({ ...p, [k]: v }));

  const handleSubmit = (e) => {
    e.preventDefault();
    setError('');
    setOk('');

    if (!form.username || !form.password || !form.name) {
      setError('필수 항목(아이디/비밀번호/이름)을 입력하세요.');
      return;
    }
    if (form.password !== form.password2) {
      setError('비밀번호가 일치하지 않습니다.');
      return;
    }

    // 현재 단계: API 미연결 -> UI 템플릿만 제공
    setOk('회원가입 UI 템플릿 완료. (API 연결 시 실제 가입 처리)');
    setTimeout(() => onBackToLogin(), 600);
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
              <Input value={form.username} onChange={(v) => set('username', v)} placeholder="아이디" width={260} />
            </div>
            <div className="form-group">
              <label className="form-label">이름</label>
              <Input value={form.name} onChange={(v) => set('name', v)} placeholder="이름" width={260} />
            </div>
          </div>

          <div className="grid-2">
            <div className="form-group">
              <label className="form-label">비밀번호</label>
              <Input type="password" value={form.password} onChange={(v) => set('password', v)} placeholder="비밀번호" width={260} />
            </div>
            <div className="form-group">
              <label className="form-label">비밀번호 확인</label>
              <Input type="password" value={form.password2} onChange={(v) => set('password2', v)} placeholder="비밀번호 확인" width={260} />
            </div>
          </div>

          <div className="form-group">
            <label className="form-label">이메일</label>
            <Input value={form.email} onChange={(v) => set('email', v)} placeholder="example@domain.com" width={540} />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
            <Button type="submit" variant="primary">가입하기</Button>
            <Button variant="ghost" onClick={onBackToLogin}>로그인으로</Button>
          </div>
        </form>
      </div>
    </div>
  );
}
