import client from './client';

interface CreatePayablePayload {
  orderId: number;
  supplierId: number;
  amount: number;
}

export async function listPayables() {
  const res = await client.get('/api/v1/payments');
  return res.data;
}

export async function createPayable(payload: CreatePayablePayload) {
  const res = await client.post('/api/v1/payments', payload);
  return res.data;
}

export async function approvePayable(id: number) {
  const res = await client.put('/api/v1/payments/' + id + '/approve');
  return res.data;
}