import client from './client';

interface RequisitionItem { itemId: number; itemName: string; quantity: number; amount: number; }
interface CreateRequisitionPayload { supplierId?: number; expectedDate?: string; purpose?: string; items: RequisitionItem[]; }

export async function listRequisitions() {
  const res = await client.get('/api/v1/requisitions');
  return res.data;
}
export async function getRequisition(id: number) {
  const res = await client.get(`/api/v1/requisitions/${id}`);
  return res.data;
}
export async function createRequisition(payload: CreateRequisitionPayload) {
  const res = await client.post('/api/v1/requisitions', payload);
  return res.data;
}
export async function submitRequisition(id: number) {
  const res = await client.post(`/api/v1/requisitions/${id}/submit`);
  return res.data;
}
export async function convertRequisition(id: number) {
  const res = await client.post(`/api/v1/requisitions/${id}/convert`);
  return res.data;
}
export async function cancelRequisition(id: number) {
  const res = await client.post(`/api/v1/requisitions/${id}/cancel`);
  return res.data;
}