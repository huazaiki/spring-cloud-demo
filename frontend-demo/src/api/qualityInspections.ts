import client from './client';

interface CreateQcPayload { receiveItemId: number | string; inspectType?: string; inspectQty: number; qualifiedQty: number; }

export async function createQualityInspection(payload: CreateQcPayload) {
  const res = await client.post('/api/v1/quality-inspections', payload);
  return res.data;
}