import {
  ContactsOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MenuOutlined,
  PlusOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
  UserSwitchOutlined
} from '@ant-design/icons';
import {
  App as AntApp,
  Button,
  Form,
  Input,
  Layout,
  Menu,
  Modal,
  Popconfirm,
  Select,
  Space,
  Statistic,
  Table,
  Tag,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import { Customer, deleteCustomer, listCustomers, login, saveCustomer } from './api';

const { Header, Sider, Content } = Layout;

type ViewKey = 'dashboard' | 'customers' | 'contacts' | 'follows' | 'users' | 'roles' | 'menus';

const menuItems = [
  { key: 'dashboard', icon: <DashboardOutlined />, label: '工作台' },
  {
    key: 'crm',
    icon: <TeamOutlined />,
    label: '客户管理',
    children: [
      { key: 'customers', icon: <UserOutlined />, label: '客户列表' },
      { key: 'contacts', icon: <ContactsOutlined />, label: '联系人' },
      { key: 'follows', icon: <MenuOutlined />, label: '跟进记录' }
    ]
  },
  {
    key: 'system',
    icon: <SettingOutlined />,
    label: '系统管理',
    children: [
      { key: 'users', icon: <UserSwitchOutlined />, label: '用户管理' },
      { key: 'roles', icon: <SafetyCertificateOutlined />, label: '角色管理' },
      { key: 'menus', icon: <MenuOutlined />, label: '菜单管理' }
    ]
  }
];

export default function App() {
  const [token, setToken] = useState(localStorage.getItem('crm_token'));
  const [view, setView] = useState<ViewKey>('dashboard');

  if (!token) {
    return <LoginPage onLogin={setToken} />;
  }

  return (
    <AntApp>
      <Layout className="app-shell">
        <Sider width={228} theme="light" className="sidebar">
          <div className="brand">CRM</div>
          <Menu
            mode="inline"
            selectedKeys={[view]}
            defaultOpenKeys={['crm', 'system']}
            items={menuItems}
            onClick={(item) => setView(item.key as ViewKey)}
          />
        </Sider>
        <Layout>
          <Header className="topbar">
            <Typography.Text strong>客户管理系统</Typography.Text>
            <Button
              icon={<LogoutOutlined />}
              onClick={() => {
                localStorage.removeItem('crm_token');
                setToken(null);
              }}
            >
              退出
            </Button>
          </Header>
          <Content className="content">
            {view === 'dashboard' && <Dashboard />}
            {view === 'customers' && <CustomerList />}
            {view !== 'dashboard' && view !== 'customers' && <Placeholder title={labelOf(view)} />}
          </Content>
        </Layout>
      </Layout>
    </AntApp>
  );
}

function LoginPage({ onLogin }: { onLogin: (token: string) => void }) {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);

  return (
    <AntApp>
      <div className="login-page">
        <div className="login-panel">
          <Typography.Title level={2}>CRM 管理系统</Typography.Title>
          <Form
            layout="vertical"
            initialValues={{ username: 'admin', password: 'admin123' }}
            onFinish={async (values) => {
              setLoading(true);
              try {
                const result = await login(values.username, values.password);
                localStorage.setItem('crm_token', result.token);
                onLogin(result.token);
              } catch (error) {
                message.error((error as Error).message);
              } finally {
                setLoading(false);
              }
            }}
          >
            <Form.Item name="username" label="用户名" rules={[{ required: true }]}>
              <Input size="large" autoComplete="username" />
            </Form.Item>
            <Form.Item name="password" label="密码" rules={[{ required: true }]}>
              <Input.Password size="large" autoComplete="current-password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" size="large" loading={loading} block>
              登录
            </Button>
          </Form>
        </div>
      </div>
    </AntApp>
  );
}

function Dashboard() {
  return (
    <div className="page">
      <Typography.Title level={3}>工作台</Typography.Title>
      <div className="metrics">
        <Statistic title="客户总数" value={1} />
        <Statistic title="待跟进" value={1} />
        <Statistic title="成交客户" value={0} />
        <Statistic title="本月回款" value={0} precision={2} prefix="￥" />
      </div>
    </div>
  );
}

function CustomerList() {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [form] = Form.useForm<Customer>();

  const load = async (page = current) => {
    setLoading(true);
    try {
      const result = await listCustomers({ current: page, size: 10, keyword });
      setCustomers(result.records);
      setTotal(result.total);
      setCurrent(result.current);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1);
  }, []);

  const columns: ColumnsType<Customer> = useMemo(
    () => [
      { title: '客户名称', dataIndex: 'customerName', fixed: 'left', width: 220 },
      { title: '行业', dataIndex: 'industry', width: 140 },
      { title: '来源', dataIndex: 'source', width: 120 },
      {
        title: '级别',
        dataIndex: 'level',
        width: 90,
        render: (level) => (level ? <Tag color="blue">{level}</Tag> : '-')
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        render: (status) => <Tag color={status === 'DEAL' ? 'green' : 'gold'}>{status || 'POTENTIAL'}</Tag>
      },
      { title: '电话', dataIndex: 'phone', width: 150 },
      { title: '城市', dataIndex: 'city', width: 120 },
      {
        title: '操作',
        key: 'action',
        width: 150,
        fixed: 'right',
        render: (_, record) => (
          <Space>
            <Button
              type="link"
              onClick={() => {
                setEditing(record);
                form.setFieldsValue(record);
                setModalOpen(true);
              }}
            >
              编辑
            </Button>
            <Popconfirm
              title="确认删除该客户？"
              onConfirm={async () => {
                await deleteCustomer(record.id!);
                message.success('已删除');
                load();
              }}
            >
              <Button type="link" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        )
      }
    ],
    [form, message]
  );

  return (
    <div className="page">
      <div className="page-header">
        <Typography.Title level={3}>客户列表</Typography.Title>
        <Space>
          <Input
            placeholder="搜索客户名称"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => load(1)}
          />
          <Button icon={<SearchOutlined />} onClick={() => load(1)}>
            查询
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null);
              form.resetFields();
              setModalOpen(true);
            }}
          >
            新增客户
          </Button>
        </Space>
      </div>
      <Table
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={customers}
        scroll={{ x: 1200 }}
        pagination={{ total, current, pageSize: 10, onChange: (page) => load(page) }}
      />
      <Modal
        title={editing ? '编辑客户' : '新增客户'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            await saveCustomer({ ...editing, ...values });
            message.success('保存成功');
            setModalOpen(false);
            load();
          }}
        >
          <Form.Item name="customerName" label="客户名称" rules={[{ required: true, message: '请输入客户名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="industry" label="行业">
            <Input />
          </Form.Item>
          <Form.Item name="source" label="来源">
            <Input />
          </Form.Item>
          <Form.Item name="level" label="级别">
            <Select
              options={[
                { value: 'A', label: 'A 级' },
                { value: 'B', label: 'B 级' },
                { value: 'C', label: 'C 级' }
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态" initialValue="POTENTIAL">
            <Select
              options={[
                { value: 'POTENTIAL', label: '潜在客户' },
                { value: 'DEAL', label: '成交客户' },
                { value: 'LOST', label: '流失客户' }
              ]}
            />
          </Form.Item>
          <Form.Item name="phone" label="电话">
            <Input />
          </Form.Item>
          <Form.Item name="city" label="城市">
            <Input />
          </Form.Item>
          <Form.Item name="address" label="地址">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function Placeholder({ title }: { title: string }) {
  return (
    <div className="page">
      <Typography.Title level={3}>{title}</Typography.Title>
      <Typography.Text type="secondary">后端基础接口和数据表已预留，可按业务优先级继续补齐页面。</Typography.Text>
    </div>
  );
}

function labelOf(view: ViewKey) {
  const labels: Record<ViewKey, string> = {
    dashboard: '工作台',
    customers: '客户列表',
    contacts: '联系人',
    follows: '跟进记录',
    users: '用户管理',
    roles: '角色管理',
    menus: '菜单管理'
  };
  return labels[view];
}
