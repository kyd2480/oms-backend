import { API_CONFIG } from './apiConfig';

export const AuthService = {
  async loginWithAPI(username, password) {
    const response = await fetch(`${API_CONFIG.BASE_URL}${API_CONFIG.ENDPOINTS.LOGIN}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password }),
    });

    const data = await response.json();
    if (data?.success) {
      localStorage.setItem('oms_user', JSON.stringify(data.user));
      localStorage.setItem('oms_token', data.token);
      return { success: true, user: data.user };
    }
    return { success: false, message: data?.message || '로그인 실패' };
  },

  // 개발 단계: 로컬 fallback 유지
  loginLocal(username, password) {
    const DEMO_ACCOUNTS = {
      admin: { password: 'admin123', name: '관리자', role: 'ADMIN', email: 'admin@oms.com' },
      manager: { password: 'manager123', name: '매니저', role: 'MANAGER', email: 'manager@oms.com' },
      user: { password: 'user123', name: '사용자', role: 'USER', email: 'user@oms.com' },
    };

    const account = DEMO_ACCOUNTS[username];
    if (!account || account.password !== password) {
      return { success: false, message: '아이디 또는 비밀번호가 올바르지 않습니다.' };
    }

    const user = {
      username,
      name: account.name,
      role: account.role,
      email: account.email,
      loginTime: new Date().toISOString(),
    };

    localStorage.setItem('oms_user', JSON.stringify(user));
    localStorage.setItem('oms_token', `local_token_${Date.now()}`);
    return { success: true, user };
  },

  async login(username, password) {
    try {
      const apiResult = await this.loginWithAPI(username, password);
      if (apiResult.success) return apiResult;
      // API 응답이 실패면 로컬 fallback
      return this.loginLocal(username, password);
    } catch {
      // 네트워크/서버 다운이면 로컬 fallback
      return this.loginLocal(username, password);
    }
  },

  logout() {
    localStorage.removeItem('oms_user');
    localStorage.removeItem('oms_token');
  },

  getCurrentUser() {
    const userStr = localStorage.getItem('oms_user');
    return userStr ? JSON.parse(userStr) : null;
  },

  getToken() {
    return localStorage.getItem('oms_token');
  },
};
