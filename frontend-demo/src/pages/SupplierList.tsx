import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, message, Typography, Tag, Select } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { listSuppliers, createSupplier, updateSupplierStatus } from '../api/supplier';

const { Title } = Typography;

interface Supplier {
  id: string; name: string; creditCode: string; contactName: string; contactPhone: string;
  status: string; createTime: string; updateTime: string;
}

const statusColor: Record<string, string> = { ACTIVE: 'green', INACTIVE: 'orange', DISQUALIFIED: 'red' };
const statusLabel: Record<string, string> = { ACTIVE: '正常', INACTIVE: '停用', DISQUALIFIED: '已淘汰' };

export default function SupplierList() {
  const [data, setData] = useState<Supplier[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [statusModal, setStatusModal] = useState<{ id: string; current: string } | null>(null);
  const [statusSubmitting, setStatusSubmitting] = useState(false);
  const [statusForm] = Form.useForm();

  const fetch = async () => {
    setLoading(true);
    try {
      const res = await listSuppliers({ page: 1, size: 100 });
      setData(res.data?.records || res.data || []);
    } catch {
      message.error('获取供应商列表失败');
    } finally { setLoading(false); }
  };

  useEffect(() => { fetch(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createSupplier(values as unknown as { name: string; creditCode: string; contactName: string; contactPhone: string });
      message.success('供应商创建成功');
      setModalOpen(false); form.resetFields(); fetch();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally { setSubmitting(false); }
  };

  const handleStatus = async (values: { status: string }) => {
    if (!statusModal) return;
    setStatusSubmitting(true);
    try {
      await updateSupplierStatus(Number(statusModal.id), values.status);
      message.success('状态修改成功');
      setStatusModal(null); fetch();
    } catch {
      message.error('状态修改失败');
    } finally { setStatusSubmitting(false); }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, ellipsis: true },
    { title: '名称', dataIndex: 'name' },
    { title: '统一社会信用代码', dataIndex: 'creditCode' },
    { title: '联系人', dataIndex: 'contactName' },
    { title: '联系电话', dataIndex: 'contactPhone' },
    { title: '状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={statusColor[s]}>{statusLabel[s] || s}</Tag> },
    { title: '操作', width: 120, render: (_: unknown, record: Supplier) => (
      <Button size="small" onClick={() => { setStatusModal({ id: record.id, current: record.status }); statusForm.setFieldsValue({ status: record.status }); }}>修改状态</Button>
    )},
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>供应商管理</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetch}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新增供应商</Button>
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={data} loading={loading} />
      <Modal title="新增供应商" open={modalOpen} onCancel={() => { setModalOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="name" label="名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="creditCode" label="统一社会信用代码" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="contactName" label="联系人" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="contactPhone" label="联系电话" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={submitting} block>提交</Button></Form.Item>
        </Form>
      </Modal>
      <Modal title="修改供应商状态" open={!!statusModal} onCancel={() => setStatusModal(null)} footer={null}>
        <Form form={statusForm} layout="vertical" onFinish={handleStatus}>
          <Form.Item name="status" label="状态" rules={[{ required: true }]}>
            <Select options={[
              { label: '正常 (ACTIVE)', value: 'ACTIVE' },
              { label: '停用 (INACTIVE)', value: 'INACTIVE' },
              { label: '已淘汰 (DISQUALIFIED)', value: 'DISQUALIFIED' },
            ]} />
          </Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={statusSubmitting} block>确认</Button></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}