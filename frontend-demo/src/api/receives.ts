import client from './client';

interface ReceiveItem { orderItemId: number; itemId: number; orderQty?: number; receivedQty: number; remark?: string; }
interface CreateReceivePayload { orderId: number; supplierId?: number; items: ReceiveItem[]; }

export async function createReceive(payload: CreateReceivePayload) {
  const res = await client.post('/api/v1/receives', payload);
  return res.data;
}
export async function listReceives() {
  const res = await client.get('/api/v1/receives');
  return res.data;
}
export async function stockIn(receiveId: number) {
  const res = await client.post(`/api/v1/receives/${receiveId}/stock-in`);
  return res.data;
}