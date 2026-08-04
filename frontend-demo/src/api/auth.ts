import client from './client';

interface RegisterPayload {
  username: string;
  password: string;
  role: string;
}

interface LoginPayload {
  username: string;
  password: string;
}

export async function register(payload: RegisterPayload) {
  const res = await client.post('/api/v1/auth/register', payload);
  return res.data;
}

export async function login(payload: LoginPayload) {
  const res = await client.post('/api/v1/auth/login', payload);
  return res.data;
}

export interface MeInfo { userId: number; username: string; deptId: number | null; deptName?: string; roles: string[]; permissions: string[]; }

export async function me(): Promise<MeInfo> {
  const res = await client.get('/api/v1/auth/me');
  return res.data.data;
}