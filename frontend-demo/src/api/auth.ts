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
