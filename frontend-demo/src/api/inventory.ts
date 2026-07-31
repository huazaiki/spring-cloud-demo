import client from './client';

interface CreateItemPayload {
  name: string;
  spec: string;
  unit: string;
  sku: string;
}

interface ReceivePayload {
  orderId: number;
  itemId: number;
  quantity: number;
}

interface ReservePayload {
  itemId: number;
  quantity: number;
}

export async function listItems() {
  const res = await client.get('/api/v1/inventory/items');
  return res.data;
}

export async function createItem(payload: CreateItemPayload) {
  const res = await client.post('/api/v1/inventory/items', payload);
  return res.data;
}

export async function receiveItem(payload: ReceivePayload) {
  const res = await client.post('/api/v1/inventory/receive', payload);
  return res.data;
}

export async function reserveItem(payload: ReservePayload) {
  const res = await client.post('/api/v1/inventory/reserve', payload);
  return res.data;
}