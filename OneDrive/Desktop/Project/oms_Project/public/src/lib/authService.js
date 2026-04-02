import { API_CONFIG } from './apiConfig';

const BASE = API_CONFIG.AUTH_URL;

export const AuthService = {

  /** 로그인 */
  async login(username, password) {
    try {
      const res  = await fetch(`${BASE}${API_CONFIG.ENDPOINTS.LOGIN}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();

      if (data?.success) {
        localStorage.setItem('oms_user',  JSON.stringify(data.user));
        localStorage.setItem('oms_token', data.token);
        return { success: true, user: data.user };
      }

      return { success: false, message: data?.message || '로그인 실패' };

    } catch (e) {
      console.error('로그인 오류:', e);
      return { success: false, message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' };
    }
  },

  /** 회원가입 */
  async signup(username, password, name, email) {
    try {
      const res  = await fetch(`${BASE}${API_CONFIG.ENDPOINTS.SIGNUP}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password, name, email }),
      });

      const data = await res.json();

      if (data?.success) {
        // 가입 즉시 로그인 처리
        localStorage.setItem('oms_user',  JSON.stringify(data.user));
        localStorage.setItem('oms_token', data.token);
        return { success: true, user: data.user };
      }

      return { success: false, message: data?.message || '회원가입 실패' };

    } catch (e) {
      console.error('회원가입 오류:', e);
      return { success: false, message: '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.' };
    }
  },

  /** 로그아웃 */
  logout() {
    localStorage.removeItem('oms_user');
    localStorage.removeItem('oms_token');
  },

  /** 현재 로그인 사용자 */
  getCurrentUser() {
    const str = localStorage.getItem('oms_user');
    return str ? JSON.parse(str) : null;
  },

  /** 저장된 토큰 */
  getToken() {
    return localStorage.getItem('oms_token');
  },
};
