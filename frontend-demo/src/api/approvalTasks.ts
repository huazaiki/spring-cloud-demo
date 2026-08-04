import client from './client';

export async function myTasks() {
  const res = await client.get('/api/v1/approval-tasks/mine');
  return res.data;
}
export async function taskRecords(bizType: string, bizId: number) {
  const res = await client.get('/api/v1/approval-tasks', { params: { bizType, bizId } });
  return res.data;
}
export async function approveTask(id: number | string, opinion?: string) {
  const res = await client.post(`/api/v1/approval-tasks/${id}/approve`, { opinion });
  return res.data;
}
export async function rejectTask(id: number | string, opinion: string) {
  const res = await client.post(`/api/v1/approval-tasks/${id}/reject`, { opinion });
  return res.data;
}
export async function transferTask(id: number | string, targetUserId: number | string, opinion?: string) {
  const res = await client.post(`/api/v1/approval-tasks/${id}/transfer`, { targetUserId, opinion });
  return res.data;
}