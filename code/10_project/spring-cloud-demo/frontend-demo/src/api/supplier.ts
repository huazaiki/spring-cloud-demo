import client from './client';

interface SupplierPayload {
  name: string;
  creditCode: string;
  contactName: string;
  contactPhone: string;
}

export async function createSupplier(payload: SupplierPayload) {
  const res = await client.post('/api/v1/suppliers', payload);
  return res.data;
}

export async function listSuppliers(params: { page?: number; size?: number; name?: string }) {
  const res = await client.get('/api/v1/suppliers', { params });
  return res.data;
}

export async function getSupplier(id: number) {
  const res = await client.get(`/api/v1/suppliers/${id}`);
  return res.data;
}

export async function updateSupplierStatus(id: number, status: string) {
  const res = await client.put(`/api/v1/suppliers/${id}/status`, { status });
  return res.data;
}
