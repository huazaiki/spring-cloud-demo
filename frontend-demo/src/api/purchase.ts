import client from './client';

interface OrderItem {
  itemId: number;
  itemName: string;
  quantity: number;
  unitPrice: number;
  amount: number;
}

interface CreateOrderPayload {
  supplierId: number;
  items: OrderItem[];
}

export async function listOrders() {
  const res = await client.get('/api/v1/orders');
  return res.data;
}

export async function createOrder(payload: CreateOrderPayload) {
  const res = await client.post('/api/v1/orders', payload);
  return res.data;
}

export async function getOrder(id: number) {
  const res = await client.get('/api/v1/orders/' + id);
  return res.data;
}

export async function approveOrder(id: number) {
  const res = await client.put('/api/v1/orders/' + id + '/approve');
  return res.data;
}
export async function cancelOrder(id: number) {
  const res = await client.post(`/api/v1/orders/${id}/cancel`);
  return res.data;
}
export async function advanceOrderStatus(id: number, status: string) {
  const res = await client.put(`/api/v1/orders/${id}/status`, { status });
  return res.data;
}