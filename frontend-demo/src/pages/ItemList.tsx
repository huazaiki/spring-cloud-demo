import { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Space, message, Typography } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { listItems, createItem, receiveItem, reserveItem } from '../api/inventory';

const { Title } = Typography;

interface Item { id: string; name: string; spec: string; unit: string; sku: string; }

export default function ItemList() {
  const [items, setItems] = useState<Item[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [receiveOpen, setReceiveOpen] = useState(false);
  const [receiveSubmitting, setReceiveSubmitting] = useState(false);
  const [receiveForm] = Form.useForm();
  const [reserveOpen, setReserveOpen] = useState(false);
  const [reserveSubmitting, setReserveSubmitting] = useState(false);
  const [reserveForm] = Form.useForm();

  const fetchItems = async () => {
    setLoading(true);
    try {
      const res = await listItems();
      setItems(res.data || []);
    } catch {
      message.error('获取物料列表失败');
    } finally { setLoading(false); }
  };

  useEffect(() => { fetchItems(); }, []);

  const handleCreate = async (values: Record<string, unknown>) => {
    setSubmitting(true);
    try {
      await createItem(values as { name: string; spec: string; unit: string; sku: string });
      message.success('物料创建成功');
      setModalOpen(false); form.resetFields();
      fetchItems();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '创建失败';
      message.error(msg);
    } finally { setSubmitting(false); }
  };

  const handleReceive = async (values: Record<string, unknown>) => {
    setReceiveSubmitting(true);
    try {
      await receiveItem({
        orderId: Number((values as { orderId: string }).orderId),
        itemId: Number((values as { itemId: string }).itemId),
        quantity: Number((values as { quantity: number }).quantity),
      });
      message.success('入库成功');
      setReceiveOpen(false); receiveForm.resetFields();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '入库失败';
      message.error(msg);
    } finally { setReceiveSubmitting(false); }
  };

  const handleReserve = async (values: Record<string, unknown>) => {
    setReserveSubmitting(true);
    try {
      await reserveItem({
        itemId: Number((values as { itemId: string }).itemId),
        quantity: Number((values as { quantity: number }).quantity),
      });
      message.success('库存预留成功');
      setReserveOpen(false); reserveForm.resetFields();
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '预留失败';
      message.error(msg);
    } finally { setReserveSubmitting(false); }
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80, ellipsis: true },
    { title: '名称', dataIndex: 'name' },
    { title: '规格', dataIndex: 'spec' },
    { title: '单位', dataIndex: 'unit', width: 80 },
    { title: 'SKU', dataIndex: 'sku' },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>物料与库存管理</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={fetchItems}>刷新</Button>
          <Button onClick={() => setReserveOpen(true)}>库存预留</Button>
          <Button onClick={() => setReceiveOpen(true)}>入库收货</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>新增物料</Button>
        </Space>
      </div>
      <Table rowKey="id" columns={columns} dataSource={items} loading={loading} />
      <Modal title="新增物料" open={modalOpen} onCancel={() => { setModalOpen(false); form.resetFields(); }} footer={null}>
        <Form form={form} layout="vertical" onFinish={handleCreate}>
          <Form.Item name="name" label="物料名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="spec" label="规格" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="unit" label="计量单位" rules={[{ required: true }]}><Input placeholder="例如：pcs、kg、m" /></Form.Item>
          <Form.Item name="sku" label="SKU" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={submitting} block>提交</Button></Form.Item>
        </Form>
      </Modal>
      <Modal title="入库收货" open={receiveOpen} onCancel={() => { setReceiveOpen(false); receiveForm.resetFields(); }} footer={null}>
        <Form form={receiveForm} layout="vertical" onFinish={handleReceive}>
          <Form.Item name="orderId" label="采购订单 ID" rules={[{ required: true }]}><Input placeholder="输入订单 ID" /></Form.Item>
          <Form.Item name="itemId" label="物料 ID" rules={[{ required: true }]}><Input placeholder="输入物料 ID" /></Form.Item>
          <Form.Item name="quantity" label="入库数量" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={1} /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={receiveSubmitting} block>确认入库</Button></Form.Item>
        </Form>
      </Modal>
      <Modal title="库存预留" open={reserveOpen} onCancel={() => { setReserveOpen(false); reserveForm.resetFields(); }} footer={null}>
        <Form form={reserveForm} layout="vertical" onFinish={handleReserve}>
          <Form.Item name="itemId" label="物料 ID" rules={[{ required: true }]}><Input placeholder="输入物料 ID" /></Form.Item>
          <Form.Item name="quantity" label="预留数量" rules={[{ required: true }]}><InputNumber style={{ width: '100%' }} min={1} /></Form.Item>
          <Form.Item><Button type="primary" htmlType="submit" loading={reserveSubmitting} block>确认预留</Button></Form.Item>
        </Form>
      </Modal>
    </div>
  );
}