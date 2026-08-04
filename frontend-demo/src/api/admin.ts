import client from './client';

export async function listUsers() { const res = await client.get('/api/v1/users'); return res.data; }
export async function createUser(payload: { username: string; password: string; deptId?: number }) {
  const res = await client.post('/api/v1/users', payload); return res.data;
}
export async function assignRoles(userId: number, roleIds: number[]) {
  const res = await client.post(`/api/v1/users/${userId}/roles`, { roleIds }); return res.data;
}
export async function listDepts() { const res = await client.get('/api/v1/depts'); return res.data; }
export async function createDept(payload: { deptCode: string; deptName: string; parentId?: number }) {
  const res = await client.post('/api/v1/depts', payload); return res.data;
}
export async function listRoles() { const res = await client.get('/api/v1/roles'); return res.data; }
export async function createRole(payload: { roleCode: string; roleName: string; description?: string }) {
  const res = await client.post('/api/v1/roles', payload); return res.data;
}
export async function assignRolePermissions(roleId: number, permissionIds: number[]) {
  const res = await client.put(`/api/v1/roles/${roleId}/permissions`, { permissionIds }); return res.data;
}
export async function listPermissions() { const res = await client.get('/api/v1/permissions'); return res.data; }