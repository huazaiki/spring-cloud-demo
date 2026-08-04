import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout, Menu, Button, theme } from 'antd';
import {
  DashboardOutlined, TeamOutlined, InboxOutlined, FileTextOutlined, DollarOutlined,
  LogoutOutlined, AuditOutlined, ImportOutlined, SafetyCertificateOutlined, SettingOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';

const { Header, Sider, Content } = Layout;

const menuDefs = [
  { key: '/', icon: <DashboardOutlined />, label: '首页', perm: 'menu:dashboard' },
  { key: '/suppliers', icon: <TeamOutlined />, label: '供应商管理', perm: 'menu:suppliers' },
  { key: '/items', icon: <InboxOutlined />, label: '物料与库存', perm: 'menu:items' },
  { key: '/requisitions', icon: <FileTextOutlined />, label: '请购管理', perm: 'menu:requisitions' },
  { key: '/orders', icon: <FileTextOutlined />, label: '采购订单', perm: 'menu:orders' },
  { key: '/receives', icon: <ImportOutlined />, label: '收货质检入库', perm: 'menu:receives' },
  { key: '/finance', icon: <DollarOutlined />, label: '发票付款', perm: 'menu:finance' },
  { key: '/approval-tasks', icon: <AuditOutlined />, label: '待办中心', perm: 'menu:approval-tasks' },
  { key: '/system', icon: <SettingOutlined />, label: '系统管理', perm: 'menu:system' },
];

const roleLabels: Record<string, string> = {
  ADMIN: '管理员', PURCHASER: '采购员', PURCHASE_MANAGER: '采购经理', DEPT_MANAGER: '部门经理',
  WAREHOUSE: '仓管员', FINANCE: '财务', FINANCE_MANAGER: '财务经理', FINANCE_DIRECTOR: '财务总监',
};

export default function MainLayout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { username, roles, permissions, hasPermission, logout } = useAuth();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  const selectedKey = '/' + (location.pathname.split('/')[1] || '');
  const menus = menuDefs.filter((m) => hasPermission(m.perm) || permissions.includes('menu:system'));

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider breakpoint="lg" collapsedWidth="0">
        <div style={{
          height: 48, margin: 16, display: 'flex', alignItems: 'center', justifyContent: 'center',
          color: '#fff', fontSize: 16, fontWeight: 700, whiteSpace: 'nowrap', overflow: 'hidden',
        }}>
          采购管理系统
        </div>
        <Menu
          theme="dark" mode="inline" selectedKeys={[selectedKey]}
          items={menus.map((m) => ({ key: m.key, icon: m.icon, label: m.label }))}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header style={{ padding: '0 24px', background: colorBgContainer, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: 14, color: '#666' }}>
            {username}（{(roles || []).map((r) => roleLabels[r] || r).join('/') || '—'}）
            {hasPermission('approval:task:view') ? (
              <Button type="link" size="small" icon={<SafetyCertificateOutlined />} onClick={() => navigate('/approval-tasks')}>待办</Button>
            ) : null}
          </span>
          <Button type="text" icon={<LogoutOutlined />} onClick={logout} danger>退出登录</Button>
        </Header>
        <Content style={{ margin: 24 }}>
          <div style={{ padding: 24, minHeight: 360, background: colorBgContainer, borderRadius: borderRadiusLG }}>
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
}