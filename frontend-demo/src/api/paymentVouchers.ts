import client from './client';

interface PayVoucherPayload { supplierId: number; amount: number; method?: string; payableIds: number[]; }

export async function listPaymentVouchers() {
  const res = await client.get('/api/v1/payment-vouchers');
  return res.data;
}
export async function payVoucher(payload: PayVoucherPayload) {
  const res = await client.post('/api/v1/payment-vouchers', payload);
  return res.data;
}