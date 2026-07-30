import { useState } from 'react';
import { Form, Input, Button, Card, message, Typography } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { login as loginApi } from '../api/auth';
import { useAuth } from '../context/AuthContext';

const { Title } = Typography;

export default function Login() {
  const [loading, setLoading] = useState(false);
  const auth = useAuth();

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await loginApi(values);
      const token = res.data?.token;
      if (token) {
        auth.login(token, values.username, '');
        message.success('登录成功');
      } else {
        message.error('登录失败：未返回 token');
      }
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '登录失败';
      message.error(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center',
      background: '#f5f5f5',
    }}>
      <Card style={{ width: 400 }}>
        <Title level={3} style={{ textAlign: 'center', marginBottom: 32 }}>
          采购管理系统
        </Title>
        <Form onFinish={onFinish} size="large">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
          <div style={{ textAlign: 'center' }}>
            <Link to="/register">还没有账号？立即注册</Link>
          </div>
        </Form>
      </Card>
    </div>
  );
}