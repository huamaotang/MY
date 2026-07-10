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
  Tree,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import {
  Customer,
  Role,
  SysMenu,
  User,
  deleteCustomer,
  deleteMenu,
  deleteRole,
  deleteUser,
  listCustomers,
  listMenus,
  listRoles,
  listUsers,
  login,
  saveCustomer,
  saveMenu,
  saveRole,
  saveUser
} from './api';

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
    return (
      <AntApp>
        <LoginPage onLogin={setToken} />
      </AntApp>
    );
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
            {view === 'users' && <UserAdmin />}
            {view === 'roles' && <RoleAdmin />}
            {view === 'menus' && <MenuAdmin />}
            {view !== 'dashboard' && view !== 'customers' && view !== 'users' && view !== 'roles' && view !== 'menus' && (
              <Placeholder title={labelOf(view)} />
            )}
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

function UserAdmin() {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [users, setUsers] = useState<User[]>([]);
  const [roles, setRoles] = useState<Role[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);
  const [form] = Form.useForm<User>();

  const load = async () => {
    setLoading(true);
    try {
      const [userResult, roleResult] = await Promise.all([listUsers(keyword), listRoles()]);
      setUsers(userResult);
      setRoles(roleResult);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const columns: ColumnsType<User> = [
    { title: '用户名', dataIndex: 'username', width: 150 },
    { title: '姓名', dataIndex: 'realName', width: 140 },
    { title: '手机号', dataIndex: 'mobile', width: 150 },
    { title: '邮箱', dataIndex: 'email', width: 200 },
    {
      title: '角色',
      dataIndex: 'roleNames',
      width: 220,
      render: (roleNames?: string[]) => (roleNames || []).map((name) => <Tag key={name}>{name}</Tag>)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => <Tag color={status === 0 ? 'red' : 'green'}>{status === 0 ? '禁用' : '启用'}</Tag>
    },
    {
      title: '操作',
      width: 150,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            onClick={() => {
              setEditing(record);
              form.setFieldsValue({ ...record, password: '' });
              setModalOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm
            title="确认删除该用户？"
            onConfirm={async () => {
              await deleteUser(record.id!);
              message.success('已删除');
              load();
            }}
          >
            <Button type="link" danger disabled={record.id === 1}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div className="page">
      <div className="page-header">
        <Typography.Title level={3}>用户管理</Typography.Title>
        <Space>
          <Input
            placeholder="搜索用户名或姓名"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={load}
          />
          <Button icon={<SearchOutlined />} onClick={load}>
            查询
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setEditing(null);
              form.resetFields();
              form.setFieldsValue({ status: 1 });
              setModalOpen(true);
            }}
          >
            新增用户
          </Button>
        </Space>
      </div>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={users} scroll={{ x: 1100 }} />
      <Modal title={editing ? '编辑用户' : '新增用户'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} destroyOnClose>
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            await saveUser({ ...editing, ...values });
            message.success('保存成功');
            setModalOpen(false);
            load();
          }}
        >
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="password" label={editing ? '新密码' : '密码'} extra={editing ? '留空则不修改密码' : '留空默认 123456'}>
            <Input.Password autoComplete="new-password" />
          </Form.Item>
          <Form.Item name="realName" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="mobile" label="手机号">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input />
          </Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select mode="multiple" options={roles.map((role) => ({ value: role.id!, label: role.roleName }))} />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '禁用' }
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function RoleAdmin() {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [roles, setRoles] = useState<Role[]>([]);
  const [menus, setMenus] = useState<SysMenu[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Role | null>(null);
  const [form] = Form.useForm<Role>();

  const load = async () => {
    setLoading(true);
    try {
      const [roleResult, menuResult] = await Promise.all([listRoles(), listMenus()]);
      setRoles(roleResult);
      setMenus(menuResult);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const columns: ColumnsType<Role> = [
    { title: '角色名称', dataIndex: 'roleName', width: 180 },
    { title: '角色编码', dataIndex: 'roleCode', width: 180 },
    {
      title: '数据范围',
      dataIndex: 'dataScope',
      width: 120,
      render: (scope) => ({ ALL: '全部', DEPT: '部门', SELF: '本人' }[scope as string] || scope)
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (status) => <Tag color={status === 0 ? 'red' : 'green'}>{status === 0 ? '禁用' : '启用'}</Tag>
    },
    {
      title: '操作',
      width: 150,
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
            title="确认删除该角色？"
            onConfirm={async () => {
              await deleteRole(record.id!);
              message.success('已删除');
              load();
            }}
          >
            <Button type="link" danger disabled={record.id === 1}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div className="page">
      <div className="page-header">
        <Typography.Title level={3}>角色管理</Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditing(null);
            form.resetFields();
            form.setFieldsValue({ status: 1, dataScope: 'ALL', menuIds: [] });
            setModalOpen(true);
          }}
        >
          新增角色
        </Button>
      </div>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={roles} />
      <Modal title={editing ? '编辑角色' : '新增角色'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} width={720} destroyOnClose>
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            await saveRole({ ...editing, ...values });
            message.success('保存成功');
            setModalOpen(false);
            load();
          }}
        >
          <Form.Item name="roleName" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="roleCode" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input disabled={editing?.id === 1} />
          </Form.Item>
          <Form.Item name="dataScope" label="数据范围">
            <Select
              options={[
                { value: 'ALL', label: '全部数据' },
                { value: 'DEPT', label: '部门数据' },
                { value: 'SELF', label: '本人数据' }
              ]}
            />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select
              options={[
                { value: 1, label: '启用' },
                { value: 0, label: '禁用' }
              ]}
            />
          </Form.Item>
          <Form.Item
            name="menuIds"
            label="菜单权限"
            valuePropName="checkedKeys"
            trigger="onCheck"
            getValueFromEvent={(checkedKeys) => (Array.isArray(checkedKeys) ? checkedKeys : checkedKeys.checked)}
          >
            <Tree checkable treeData={toTreeData(menus)} defaultExpandAll />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

function MenuAdmin() {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [menus, setMenus] = useState<SysMenu[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<SysMenu | null>(null);
  const [form] = Form.useForm<SysMenu>();

  const load = async () => {
    setLoading(true);
    try {
      setMenus(await listMenus());
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const columns: ColumnsType<SysMenu> = [
    { title: '名称', dataIndex: 'menuName', width: 180 },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 100,
      render: (type) => <Tag>{menuTypeLabel(type)}</Tag>
    },
    { title: '路由', dataIndex: 'path', width: 180 },
    { title: '组件', dataIndex: 'component', width: 160 },
    { title: '权限编码', dataIndex: 'permissionCode', width: 190 },
    { title: '排序', dataIndex: 'sortOrder', width: 80 },
    {
      title: '显示',
      dataIndex: 'visible',
      width: 90,
      render: (visible) => <Tag color={visible === 0 ? 'default' : 'green'}>{visible === 0 ? '隐藏' : '显示'}</Tag>
    },
    {
      title: '操作',
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
            title="确认删除该菜单？"
            onConfirm={async () => {
              await deleteMenu(record.id!);
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
  ];

  return (
    <div className="page">
      <div className="page-header">
        <Typography.Title level={3}>菜单管理</Typography.Title>
        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={() => {
            setEditing(null);
            form.resetFields();
            form.setFieldsValue({ parentId: 0, menuType: 'MENU', visible: 1, sortOrder: 0 });
            setModalOpen(true);
          }}
        >
          新增菜单
        </Button>
      </div>
      <Table rowKey="id" loading={loading} columns={columns} dataSource={buildMenuRows(menus)} pagination={false} scroll={{ x: 1200 }} />
      <Modal title={editing ? '编辑菜单' : '新增菜单'} open={modalOpen} onCancel={() => setModalOpen(false)} onOk={() => form.submit()} destroyOnClose>
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            await saveMenu({ ...editing, ...values });
            message.success('保存成功');
            setModalOpen(false);
            load();
          }}
        >
          <Form.Item name="parentId" label="上级菜单">
            <Select
              showSearch
              optionFilterProp="label"
              options={[{ value: 0, label: '根目录' }, ...menus.filter((menu) => menu.id !== editing?.id).map((menu) => ({ value: menu.id!, label: menu.menuName }))]}
            />
          </Form.Item>
          <Form.Item name="menuName" label="菜单名称" rules={[{ required: true, message: '请输入菜单名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="menuType" label="类型">
            <Select
              options={[
                { value: 'CATALOG', label: '目录' },
                { value: 'MENU', label: '菜单' },
                { value: 'BUTTON', label: '按钮' }
              ]}
            />
          </Form.Item>
          <Form.Item name="path" label="路由">
            <Input />
          </Form.Item>
          <Form.Item name="component" label="组件">
            <Input />
          </Form.Item>
          <Form.Item name="permissionCode" label="权限编码">
            <Input />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Input />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序">
            <Input type="number" />
          </Form.Item>
          <Form.Item name="visible" label="显示状态">
            <Select
              options={[
                { value: 1, label: '显示' },
                { value: 0, label: '隐藏' }
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}

type MenuTreeNode = {
  title: string;
  key: number;
  children?: MenuTreeNode[];
};

function toTreeData(menus: SysMenu[]) {
  return buildMenuRows(menus).map((menu) => toTreeNode(menu));
}

function toTreeNode(menu: SysMenu): MenuTreeNode {
  return {
    title: `${menu.menuName} ${menu.permissionCode ? `(${menu.permissionCode})` : ''}`,
    key: menu.id!,
    children: menu.children?.map((child) => toTreeNode(child))
  };
}

function buildMenuRows(menus: SysMenu[]) {
  const map = new Map<number, SysMenu & { children?: SysMenu[] }>();
  menus.forEach((menu) => map.set(menu.id!, { ...menu, children: [] }));
  const roots: (SysMenu & { children?: SysMenu[] })[] = [];
  map.forEach((menu) => {
    if (menu.parentId && map.has(menu.parentId)) {
      map.get(menu.parentId)!.children!.push(menu);
    } else {
      roots.push(menu);
    }
  });
  return roots;
}

function menuTypeLabel(type: string) {
  return { CATALOG: '目录', MENU: '菜单', BUTTON: '按钮' }[type] || type;
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
