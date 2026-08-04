import client from './client';

interface InvoiceItem { orderItemId?: number; itemId: number; quantity: number; unitPrice: number; amount: number; }
interface CreateInvoicePayload { supplierId: number; orderId?: number; invoiceNo: string; invoiceDate: string; totalAmount: number; taxAmount?: number; items?: InvoiceItem[]; }

export async function listInvoices() {
  const res = await client.get('/api/v1/invoices');
  return res.data;
}
export async function createInvoice(payload: CreateInvoicePayload) {
  const res = await client.post('/api/v1/invoices', payload);
  return res.data;
}
export async function matchInvoice(id: number, receiveId?: number) {
  const res = await client.post(`/api/v1/invoices/${id}/match`, { receiveId });
  return res.data;
}