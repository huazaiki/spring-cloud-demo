import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, theme } from 'antd';
import {
  DashboardOutlined,
  TeamOutlined,
  InboxOutlined,
  FileTextOutlined,
  DollarOutlined,
  LogoutOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';

const { Header, Sider, Content } = Layout;

const menuItems = [
  { key: '/', icon: <DashboardOutlined />, label: '首页' },
  { key: '/suppliers', icon: <TeamOutlined />, label: '供应商管理' },
  { key: '/items', icon: <InboxOutlined />, label: '物料与库存' },
  { key: '/orders', icon: <FileTextOutlined />, label: '采购订单' },
  { key: '/payments', icon: <DollarOutlined />, label: '应付账款' },
];

const roleLabels: Record<string, string> = {
  PURCHASER: '采购员',
  WAREHOUSE: '仓管员',
  FINANCE: '财务',
  ADMIN: '管理员',
};

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { username, role, logout } = useAuth();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  const selectedKey = '/' + (location.pathname.split('/')[1] || '');

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth="0">
        <div style={{
          height: 48,
          margin: 16,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#fff',
          fontSize: 16,
          fontWeight: 700,
          whiteSpace: 'nowrap',
          overflow: 'hidden',
        }}>
          采购管理系统
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[selectedKey]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{
          padding: '0 24px',
          background: colorBgContainer,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <span style={{ fontSize: 14, color: '#666' }}>
            {username}（{roleLabels[role || ''] || role}）
          </span>
          <Button
            type="text"
            icon={<LogoutOutlined />}
            onClick={logout}
            danger
          >
            退出登录
          </Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <div style={{
            padding: 24,
            minHeight: 360,
            background: colorBgContainer,
            borderRadius: borderRadiusLG,
          }}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}