// auth-service URL: 환경변수 우선, 없으면 Railway URL
const AUTH_BASE = import.meta.env.VITE_AUTH_URL ||
  'https://intuitive-friendship-production-17a1.up.railway.app';

export const API_CONFIG = {
  AUTH_URL: AUTH_BASE,
  ENDPOINTS: {
    LOGIN: '/api/auth/login',
    SIGNUP: '/api/auth/signup',
    VALIDATE: '/api/auth/validate',
    ME: '/api/auth/me',
  }
};
