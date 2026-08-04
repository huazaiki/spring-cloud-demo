import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, message, Typography, Tabs, Select, Tag } from 'antd';
import { PlusOutlined, ReloadOutlined, TeamOutlined, SafetyOutlined } from '@ant-design/icons';
import {
  listUsers, createUser, assignRoles,
  listDepts, createDept,
  listRoles, createRole, assignRolePermissions, listPermissions,
} from '../api/admin';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

interface User { id: string; username: string; deptId: number | null; status: string; }
interface Dept { id: string; deptCode: string; deptName: string; parentId: number; }
interface Role { id: string; roleCode: string; roleName: string; description?: string; }
interface Permission { id: string; permCode: string; permName: string; permType: string; }

export default function System() {
  const { hasPermission } = useAuth();
  const [users, setUsers] = useState<User[]>([]);
  const [depts, setDepts] = useState<Dept[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [perms, setPerms] = useState<Permission[]>([]);
  const [loading, setLoading] = useState(false);

  // modals
  const [userOpen, setUserOpen] = useState(false);
  const [deptOpen, setDeptOpen] = useState(false);
  const [roleOpen, setRoleOpen] = useState(false);
  const [permOpen, setPermOpen] = useState<Role | null>(null);
  const [roleForUser, setRoleForUser] = useState<User | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [uForm] = Form.useForm();
  const [dForm] = Form.useForm();
  const [rForm] = Form.useForm();
  const [pForm] = Form.useForm();

  const fetchAll = async () => {
    setLoading(true);
    try {
      const [u, d, r, p] = await Promise.all([listUsers(), listDepts(), listRoles(), listPermissions()]);
      setUsers(u.data || []); setDepts(d.data || []); setRoles(r.data || []); setPerms(p.data || []);
    } catch { message.error('获取系统数据失败'); } finally { setLoading(false); }
  };
  useEffect(() => { fetchAll(); }, []);

  const errMsg = (err: unknown, fallback: string) =>
    (err as { response?: { data?: { message?: string } } })?.response?.data?.message || fallback;

  const handleCreateUser = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createUser({ username: values.username as string, password: values.password as string, deptId: values.deptId ? Number(values.deptId) : undefined });
      message.success('用户已创建'); setUserOpen(false); uForm.resetFields(); fetchAll();
    } catch (err: unknown) { message.error(errMsg(err, '创建失败')); } finally { setSubmitting(false); }
  };

  const handleAssignRoles = async (values: Record<string, unknown>) => {
    if (!roleForUser) return;
    setSubmitting(true);
    try {
      await assignRoles(Number(roleForUser.id), (values.roleIds as number[] || []).map(Number));
      message.success('角色已分配'); setRoleForUser(null); pForm.resetFields();
    } catch (err: unknown) { message.error(errMsg(err, '分配失败')); } finally { setSubmitting(false); }
  };

  const handleCreateDept = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createDept({ deptCode: values.deptCode as string, deptName: values.deptName as string, parentId: values.parentId ? Number(values.parentId) : 0 });
      message.success('部门已创建'); setDeptOpen(false); dForm.resetFields(); fetchAll();
    } catch (err: unknown) { message.error(errMsg(err, '创建失败')); } finally { setSubmitting(false); }
  };

  const handleCreateRole = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createRole({ roleCode: values.roleCode as string, roleName: values.roleName as string, description: values.description as string });
      message.success('角色已创建'); setRoleOpen(false); rForm.resetFields(); fetchAll();
    } catch (err: unknown) { message.error(errMsg(err, '创建失败')); } finally { setSubmitting(false); }
  };

  const handleAssignPerms = async (values: Record<string, unknown>) => {
    if (!permOpen) return;
    setSubmitting(true);
    try {
      await assignRolePermissions(Number(permOpen.id), (values.permissionIds as number[] || []).map(Number));
      message.success('权限已配置'); setPermOpen(null);
    } catch (err: unknown) { message.error(errMsg(err, '配置失败')); } finally { setSubmitting(false); }
  };

  const userColumns = [
    { title: 'ID', dataIndex: 'id', width: 120 },
    { title: '用户名', dataIndex: 'username' },
    { title: '部门', dataIndex: 'deptId', width: 120, render: (v: number | null) => v ?? '—' },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag> },
    { title: '操作', width: 130, render: (_: unknown, r: User) => (
      hasPermission('user:update') ? <Button size="small" icon={<TeamOutlined />} onClick={() => { setRoleForUser(r); }}>分配角色</Button> : null
    )},
  ];
  const deptColumns = [
    { title: '部门编码', dataIndex: 'deptCode' },
    { title: '部门名称', dataIndex: 'deptName' },
    { title: '上级部门', dataIndex: 'parentId', width: 120, render: (v: number) => v || '—' },
  ];
  const roleColumns = [
    { title: '角色编码', dataIndex: 'roleCode' },
    { title: '角色名称', dataIndex: 'roleName' },
    { title: '描述', dataIndex: 'description' },
    { title: '操作', width: 130, render: (_: unknown, r: Role) => (
      hasPermission('role:manage') ? <Button size="small" icon={<SafetyOutlined />} onClick={() => setPermOpen(r)}>配置权限</Button> : null
    )},
  ];

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>系统管理</Title>
      <Tabs items={[
        { key: 'users', label: '用户', children: (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
              {hasPermission('user:create') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setUserOpen(true)}>新建用户</Button>}
            </div>
            <Table rowKey="id" columns={userColumns} dataSource={users} loading={loading} />
          </div>
        )},
        { key: 'depts', label: '部门', children: (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
              {hasPermission('dept:manage') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setDeptOpen(true)}>新建部门</Button>}
            </div>
            <Table rowKey="id" columns={deptColumns} dataSource={depts} loading={loading} />
          </div>
        )},
        { key: 'roles', label: '角色', children: (
          <div>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
              <Button icon={<ReloadOutlined />} onClick={fetchAll}>刷新</Button>
              {hasPermission('role:manage') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setRoleOpen(true)}>新建角色</Button>}
            </div>
            <Table rowKey="id" columns={roleColumns} dataSource={roles} loading={loading} />
          </div>
        )},
      ]} />

      <Modal title="新建用户" open={userOpen} onCancel={() => { setUserOpen(false); uForm.resetFields(); }} footer={null}>
        <Form form={uForm} layout="vertical" onFinish={handleCreateUser}>
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="password" label="密码" rules={[{ required: true, min: 6 }]}><Input.Password /></Form.Item>
          <Form.Item name="deptId" label="部门">
            <Select allowClear options={depts.map((d) => ({ label: d.deptName + ' (' + d.deptCode + ')', value: d.id }))} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>创建</Button>
        </Form>
      </Modal>
      <Modal title="分配角色" open={!!roleForUser} onCancel={() => setRoleForUser(null)} footer={null}>
        <p>用户：{roleForUser?.username}</p>
        <Form form={pForm} layout="vertical" onFinish={handleAssignRoles}>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" options={roles.map((r) => ({ label: r.roleName + ' (' + r.roleCode + ')', value: r.id }))} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>保存</Button>
        </Form>
      </Modal>
      <Modal title="新建部门" open={deptOpen} onCancel={() => { setDeptOpen(false); dForm.resetFields(); }} footer={null}>
        <Form form={dForm} layout="vertical" onFinish={handleCreateDept}>
          <Form.Item name="deptCode" label="部门编码" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="deptName" label="部门名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="parentId" label="上级部门">
            <Select allowClear options={depts.map((d) => ({ label: d.deptName, value: d.id }))} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>创建</Button>
        </Form>
      </Modal>
      <Modal title="新建角色" open={roleOpen} onCancel={() => { setRoleOpen(false); rForm.resetFields(); }} footer={null}>
        <Form form={rForm} layout="vertical" onFinish={handleCreateRole}>
          <Form.Item name="roleCode" label="角色编码" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="description" label="描述"><Input /></Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>创建</Button>
        </Form>
      </Modal>
      <Modal title={`配置权限：${permOpen?.roleName || ''}`} open={!!permOpen} width={560} onCancel={() => setPermOpen(null)} footer={null}>
        <Form form={rForm} layout="vertical" onFinish={handleAssignPerms}>
          <Form.Item name="permissionIds" label="权限点">
            <Select mode="multiple" style={{ width: '100%' }} optionFilterProp="label"
              options={perms.map((p) => ({ label: `${p.permName} (${p.permCode})`, value: p.id }))} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={submitting} block>保存</Button>
        </Form>
      </Modal>
    </div>
  );
}