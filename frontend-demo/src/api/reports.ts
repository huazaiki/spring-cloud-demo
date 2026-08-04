import client from './client';

export async function procurementDashboard() {
  const res = await client.get('/api/v1/reports/procurement-dashboard');
  return res.data;
}
export async function payableAging() {
  const res = await client.get('/api/v1/reports/payable-aging');
  return res.data;
}
export async function inventorySummary() {
  const res = await client.get('/api/v1/reports/inventory-summary');
  return res.data;
}