import {
  ContactsOutlined,
  DashboardOutlined,
  FundOutlined,
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
  Descriptions,
  Drawer,
  Form,
  Input,
  Layout,
  Menu,
  Modal,
  Popconfirm,
  Select,
  Segmented,
  Space,
  Statistic,
  Table,
  Tabs,
  Tag,
  Tree,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useState } from 'react';
import {
  Customer,
  Fund,
  FundDetail,
  FundFeature,
  FundHolding,
  FundNav,
  Role,
  SysMenu,
  User,
  deleteCustomer,
  deleteFund,
  deleteMenu,
  deleteRole,
  deleteUser,
  getFundDetail,
  listFundFeatures,
  listFundHoldings,
  listFundNavs,
  listFunds,
  listCustomers,
  listMenus,
  listRoles,
  listUsers,
  login,
  saveCustomer,
  saveFund,
  saveMenu,
  saveRole,
  saveUser
} from './api';

const { Header, Sider, Content } = Layout;

type ViewKey = 'dashboard' | 'customers' | 'contacts' | 'follows' | 'funds' | 'users' | 'roles' | 'menus';

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const CHART_NAV_SIZE = 1000;

type TrendPeriod = '1M' | '3M' | '6M' | '1Y' | '3Y' | 'ALL';

const TREND_PERIOD_OPTIONS: { label: string; value: TrendPeriod }[] = [
  { label: '近1月', value: '1M' },
  { label: '近3月', value: '3M' },
  { label: '近6月', value: '6M' },
  { label: '近1年', value: '1Y' },
  { label: '近3年', value: '3Y' },
  { label: '成立以来', value: 'ALL' }
];

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
    key: 'products',
    icon: <FundOutlined />,
    label: '产品管理',
    children: [{ key: 'funds', icon: <FundOutlined />, label: '基金管理' }]
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
            defaultOpenKeys={['crm', 'products', 'system']}
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
            {view === 'funds' && <FundList />}
            {view === 'users' && <UserAdmin />}
            {view === 'roles' && <RoleAdmin />}
            {view === 'menus' && <MenuAdmin />}
            {view !== 'dashboard' && view !== 'customers' && view !== 'funds' && view !== 'users' && view !== 'roles' && view !== 'menus' && (
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
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [form] = Form.useForm<Customer>();

  const load = async (page = current, size = pageSize) => {
    setLoading(true);
    try {
      const result = await listCustomers({ current: page, size, keyword });
      setCustomers(result.records);
      setTotal(result.total);
      setCurrent(result.current);
      setPageSize(result.size);
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
        pagination={{
          total,
          current,
          pageSize,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS,
          onChange: (page, size) => load(page, size)
        }}
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

function FundList() {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [fundType, setFundType] = useState<string | undefined>();
  const [funds, setFunds] = useState<Fund[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Fund | null>(null);
  const [detailFundCode, setDetailFundCode] = useState<string | null>(null);
  const [form] = Form.useForm<Fund>();

  const load = async (page = current, size = pageSize) => {
    setLoading(true);
    try {
      const result = await listFunds({ current: page, size, keyword, fundType });
      setFunds(result.records);
      setTotal(result.total);
      setCurrent(result.current);
      setPageSize(result.size);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load(1);
  }, []);

  const columns: ColumnsType<Fund> = [
    { title: '基金代码', dataIndex: 'fundCode', fixed: 'left', width: 120 },
    { title: '基金名称', dataIndex: 'fundName', fixed: 'left', width: 240 },
    { title: '类型', dataIndex: 'fundType', width: 120, render: (value) => value || '-' },
    { title: '基金经理', dataIndex: 'fundManager', width: 160, render: (value) => value || '-' },
    { title: '管理人', dataIndex: 'managementCompany', width: 220, render: (value) => value || '-' },
    { title: '净资产规模', dataIndex: 'netAssetScale', width: 140, render: (value) => value || '-' },
    { title: '成立日期', dataIndex: 'inceptionDate', width: 130, render: (value) => value || '-' },
    {
      title: '操作',
      key: 'action',
      width: 210,
      fixed: 'right',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => setDetailFundCode(record.fundCode)}>
            详情
          </Button>
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
            title="确认删除该基金？"
            onConfirm={async () => {
              await deleteFund(record.fundCode);
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
        <Typography.Title level={3}>基金管理</Typography.Title>
        <Space wrap>
          <Input
            placeholder="搜索代码、名称或经理"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => load(1)}
          />
          <Select
            allowClear
            placeholder="基金类型"
            value={fundType}
            style={{ width: 140 }}
            onChange={setFundType}
            options={[
              { value: '股票型', label: '股票型' },
              { value: '混合型', label: '混合型' },
              { value: '债券型', label: '债券型' },
              { value: '指数型', label: '指数型' },
              { value: '货币型', label: '货币型' }
            ]}
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
            新增基金
          </Button>
        </Space>
      </div>
      <Table
        rowKey="fundCode"
        loading={loading}
        columns={columns}
        dataSource={funds}
        scroll={{ x: 1350 }}
        pagination={{
          total,
          current,
          pageSize,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS,
          onChange: (page, size) => load(page, size)
        }}
      />
      <Modal
        title={editing ? '编辑基金' : '新增基金'}
        open={modalOpen}
        onCancel={() => setModalOpen(false)}
        onOk={() => form.submit()}
        destroyOnClose
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={async (values) => {
            await saveFund({ ...editing, ...values });
            message.success('保存成功');
            setModalOpen(false);
            load();
          }}
        >
          <Form.Item name="fundCode" label="基金代码" rules={[{ required: true, message: '请输入基金代码' }]}>
            <Input disabled={!!editing} />
          </Form.Item>
          <Form.Item name="fundName" label="基金名称" rules={[{ required: true, message: '请输入基金名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="fundType" label="基金类型">
            <Input />
          </Form.Item>
          <Form.Item name="fundManager" label="基金经理">
            <Input />
          </Form.Item>
          <Form.Item name="managementCompany" label="管理人">
            <Input />
          </Form.Item>
          <Form.Item name="inceptionDate" label="成立日期">
            <Input placeholder="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="netAssetScale" label="净资产规模">
            <Input />
          </Form.Item>
          <Form.Item name="scaleDate" label="规模截止日期">
            <Input placeholder="YYYY-MM-DD" />
          </Form.Item>
        </Form>
      </Modal>
      <FundDetailDrawer fundCode={detailFundCode} open={!!detailFundCode} onClose={() => setDetailFundCode(null)} />
    </div>
  );
}

function FundDetailDrawer({ fundCode, open, onClose }: { fundCode: string | null; open: boolean; onClose: () => void }) {
  const { message } = AntApp.useApp();
  const [loading, setLoading] = useState(false);
  const [detail, setDetail] = useState<FundDetail | null>(null);
  const [chartNavs, setChartNavs] = useState<FundNav[]>([]);
  const [trendPeriod, setTrendPeriod] = useState<TrendPeriod>('1Y');
  const [navs, setNavs] = useState<FundNav[]>([]);
  const [navTotal, setNavTotal] = useState(0);
  const [navCurrent, setNavCurrent] = useState(1);
  const [navPageSize, setNavPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [holdings, setHoldings] = useState<FundHolding[]>([]);
  const [holdingTotal, setHoldingTotal] = useState(0);
  const [holdingCurrent, setHoldingCurrent] = useState(1);
  const [holdingPageSize, setHoldingPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [features, setFeatures] = useState<FundFeature[]>([]);

  const loadDetail = async () => {
    if (!fundCode) {
      return;
    }
    setLoading(true);
    try {
      const result = await getFundDetail(fundCode);
      setDetail(result);
      setFeatures(result.features || []);
      await Promise.all([loadChartNavs(), loadNavs(1), loadHoldings(1)]);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadNavs = async (page = navCurrent, size = navPageSize) => {
    if (!fundCode) {
      return;
    }
    const result = await listFundNavs(fundCode, { current: page, size });
    setNavs(result.records);
    setNavTotal(result.total);
    setNavCurrent(result.current);
    setNavPageSize(result.size);
  };

  const loadChartNavs = async () => {
    if (!fundCode) {
      return;
    }
    const result = await listFundNavs(fundCode, { current: 1, size: CHART_NAV_SIZE });
    setChartNavs(result.records);
  };

  const loadHoldings = async (page = holdingCurrent, size = holdingPageSize) => {
    if (!fundCode) {
      return;
    }
    const result = await listFundHoldings(fundCode, { current: page, size });
    setHoldings(result.records);
    setHoldingTotal(result.total);
    setHoldingCurrent(result.current);
    setHoldingPageSize(result.size);
  };

  const refreshFeatures = async () => {
    if (!fundCode) {
      return;
    }
    setFeatures(await listFundFeatures(fundCode));
  };

  useEffect(() => {
    if (open && fundCode) {
      loadDetail();
    } else {
      setDetail(null);
      setChartNavs([]);
      setNavs([]);
      setHoldings([]);
      setFeatures([]);
    }
  }, [open, fundCode]);

  const navColumns: ColumnsType<FundNav> = [
    { title: '净值日期', dataIndex: 'navDate', width: 120 },
    { title: '单位净值', dataIndex: 'unitNav', width: 120, render: formatValue },
    { title: '累计净值', dataIndex: 'accumulatedNav', width: 120, render: formatValue },
    { title: '日增长率', dataIndex: 'dailyGrowthRate', width: 120, render: (value) => (value == null ? '-' : `${value}%`) }
  ];

  const holdingColumns: ColumnsType<FundHolding> = [
    { title: '报告日期', dataIndex: 'reportDate', width: 110 },
    { title: '排名', dataIndex: 'rankNo', width: 80 },
    { title: '股票代码', dataIndex: 'stockCode', width: 110 },
    { title: '股票名称', dataIndex: 'stockName', width: 140 },
    { title: '占净值比例', dataIndex: 'netValueRatio', width: 120, render: (value) => (value == null ? '-' : `${value}%`) },
    { title: '持股数(万股)', dataIndex: 'holdingShares10k', width: 130, render: formatValue },
    { title: '持仓市值(万元)', dataIndex: 'holdingMarketValue10k', width: 150, render: formatValue }
  ];

  const featureColumns: ColumnsType<FundFeature> = [
    { title: '截止日期', dataIndex: 'cutoffDate', width: 120 },
    { title: '统计周期', dataIndex: 'periodLabel', width: 100 },
    { title: '标准差', dataIndex: 'standardDeviation', width: 120, render: formatValue },
    { title: '夏普比率', dataIndex: 'sharpeRatio', width: 120, render: formatValue }
  ];

  const trendRows = useMemo(() => buildTrendRows(chartNavs, trendPeriod), [chartNavs, trendPeriod]);

  return (
    <Drawer title="基金详情" open={open} onClose={onClose} width={920} destroyOnClose>
      <Tabs
        items={[
          {
            key: 'base',
            label: '基础信息',
            children: (
              <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="基金代码">{detail?.fund.fundCode || '-'}</Descriptions.Item>
                <Descriptions.Item label="基金名称">{detail?.fund.fundName || '-'}</Descriptions.Item>
                <Descriptions.Item label="基金类型">{detail?.fund.fundType || '-'}</Descriptions.Item>
                <Descriptions.Item label="基金经理">{detail?.fund.fundManager || '-'}</Descriptions.Item>
                <Descriptions.Item label="管理人">{detail?.fund.managementCompany || '-'}</Descriptions.Item>
                <Descriptions.Item label="成立日期">{detail?.fund.inceptionDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="净资产规模">{detail?.fund.netAssetScale || '-'}</Descriptions.Item>
                <Descriptions.Item label="规模截止日期">{detail?.fund.scaleDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="最新净值">{formatValue(detail?.latestNav?.unitNav)}</Descriptions.Item>
                <Descriptions.Item label="净值日期">{detail?.latestNav?.navDate || '-'}</Descriptions.Item>
              </Descriptions>
            )
          },
          {
            key: 'trends',
            label: '走势',
            children: (
              <div className="trend-section">
                <div className="trend-toolbar">
                  <Typography.Title level={5}>净值与收益走势</Typography.Title>
                  <Segmented
                    size="small"
                    value={trendPeriod}
                    options={TREND_PERIOD_OPTIONS}
                    onChange={(value) => setTrendPeriod(value as TrendPeriod)}
                  />
                </div>
                <div className="trend-chart-grid">
                  <TrendChart
                    title="净值走势图"
                    rows={trendRows}
                    series={[
                      { key: 'unitNav', label: '单位净值', color: '#1677ff' },
                      { key: 'accumulatedNav', label: '累计净值', color: '#52c41a' }
                    ]}
                  />
                  <TrendChart
                    title="收益走势图"
                    rows={trendRows}
                    series={[{ key: 'returnRate', label: '累计收益率', color: '#fa8c16', unit: '%' }]}
                  />
                </div>
              </div>
            )
          },
          {
            key: 'holdings',
            label: '持仓',
            children: (
              <Table
                rowKey={(record) => `${record.reportDate}-${record.stockCode}`}
                columns={holdingColumns}
                dataSource={holdings}
                loading={loading}
                scroll={{ x: 900 }}
                pagination={{
                  total: holdingTotal,
                  current: holdingCurrent,
                  pageSize: holdingPageSize,
                  showSizeChanger: true,
                  pageSizeOptions: PAGE_SIZE_OPTIONS,
                  onChange: loadHoldings
                }}
              />
            )
          },
          {
            key: 'navs',
            label: '净值',
            children: (
              <Table
                rowKey={(record) => `${record.navDate}-${record.fundCode}`}
                columns={navColumns}
                dataSource={navs}
                loading={loading}
                pagination={{
                  total: navTotal,
                  current: navCurrent,
                  pageSize: navPageSize,
                  showSizeChanger: true,
                  pageSizeOptions: PAGE_SIZE_OPTIONS,
                  onChange: loadNavs
                }}
              />
            )
          },
          {
            key: 'features',
            label: '特色数据',
            children: (
              <Table
                rowKey={(record) => `${record.cutoffDate}-${record.periodLabel}`}
                columns={featureColumns}
                dataSource={features}
                loading={loading}
                pagination={false}
                onChange={refreshFeatures}
              />
            )
          }
        ]}
      />
    </Drawer>
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

type TrendRow = {
  date: string;
  unitNav?: number;
  accumulatedNav?: number;
  returnRate?: number;
};

type TrendSeries = {
  key: keyof TrendRow;
  label: string;
  color: string;
  unit?: string;
};

function TrendChart({ title, rows, series }: { title: string; rows: TrendRow[]; series: TrendSeries[] }) {
  const width = 760;
  const height = 260;
  const padding = { top: 24, right: 28, bottom: 34, left: 54 };
  const plotWidth = width - padding.left - padding.right;
  const plotHeight = height - padding.top - padding.bottom;
  const values = rows.flatMap((row) => series.map((item) => toNumber(row[item.key])).filter((value): value is number => value != null));

  if (rows.length < 2 || values.length < 2) {
    return (
      <div className="trend-chart-panel">
        <div className="trend-chart-header">
          <Typography.Text strong>{title}</Typography.Text>
        </div>
        <div className="trend-empty">暂无走势数据</div>
      </div>
    );
  }

  const minValue = Math.min(...values);
  const maxValue = Math.max(...values);
  const valuePadding = Math.max((maxValue - minValue) * 0.08, 0.01);
  const yMin = minValue - valuePadding;
  const yMax = maxValue + valuePadding;
  const xFor = (index: number) => padding.left + (plotWidth * index) / Math.max(rows.length - 1, 1);
  const yFor = (value: number) => padding.top + plotHeight - ((value - yMin) / (yMax - yMin || 1)) * plotHeight;
  const yTicks = [yMax, (yMax + yMin) / 2, yMin];
  const xTicks = [0, Math.floor((rows.length - 1) / 2), rows.length - 1];

  return (
    <div className="trend-chart-panel">
      <div className="trend-chart-header">
        <Typography.Text strong>{title}</Typography.Text>
        <div className="trend-legend">
          {series.map((item) => (
            <span key={item.key} className="trend-legend-item">
              <span className="trend-legend-dot" style={{ background: item.color }} />
              {item.label}
            </span>
          ))}
        </div>
      </div>
      <svg className="trend-chart-svg" viewBox={`0 0 ${width} ${height}`} role="img" aria-label={title}>
        {yTicks.map((tick) => (
          <g key={tick}>
            <line x1={padding.left} y1={yFor(tick)} x2={width - padding.right} y2={yFor(tick)} stroke="#edf0f5" />
            <text x={padding.left - 10} y={yFor(tick) + 4} textAnchor="end" className="trend-axis-text">
              {formatChartNumber(tick, series[0]?.unit)}
            </text>
          </g>
        ))}
        {xTicks.map((index) => (
          <text key={index} x={xFor(index)} y={height - 10} textAnchor={index === 0 ? 'start' : index === rows.length - 1 ? 'end' : 'middle'} className="trend-axis-text">
            {formatNavDate(rows[index].date)}
          </text>
        ))}
        {series.map((item) => {
          let started = false;
          const path = rows
            .map((row, index) => {
              const value = toNumber(row[item.key]);
              if (value == null) {
                return '';
              }
              const command = started ? 'L' : 'M';
              started = true;
              return `${command} ${xFor(index).toFixed(2)} ${yFor(value).toFixed(2)}`;
            })
            .filter(Boolean)
            .join(' ');
          return <path key={item.key} d={path} fill="none" stroke={item.color} strokeWidth={2.2} strokeLinecap="round" strokeLinejoin="round" />;
        })}
      </svg>
    </div>
  );
}

function buildTrendRows(navs: FundNav[], period: TrendPeriod): TrendRow[] {
  const sorted = [...navs]
    .filter((item) => item.navDate && (toNumber(item.unitNav) != null || toNumber(item.accumulatedNav) != null))
    .sort((first, second) => first.navDate.localeCompare(second.navDate));
  const filtered = filterTrendPeriod(sorted, period);
  const base = filtered.map((item) => toNumber(item.accumulatedNav) ?? toNumber(item.unitNav)).find((value): value is number => value != null && value !== 0);

  return filtered.map((item) => {
    const unitNav = toNumber(item.unitNav) ?? undefined;
    const accumulatedNav = toNumber(item.accumulatedNav) ?? undefined;
    const returnBaseValue = accumulatedNav ?? unitNav;
    return {
      date: item.navDate,
      unitNav,
      accumulatedNav,
      returnRate: base && returnBaseValue != null ? ((returnBaseValue / base) - 1) * 100 : undefined
    };
  });
}

function filterTrendPeriod(navs: FundNav[], period: TrendPeriod) {
  if (period === 'ALL' || navs.length === 0) {
    return navs;
  }
  const lastDate = parseNavDate(navs[navs.length - 1].navDate);
  if (!lastDate) {
    return navs;
  }
  const startDate = new Date(lastDate);
  const periodMonths: Record<Exclude<TrendPeriod, 'ALL'>, number> = {
    '1M': 1,
    '3M': 3,
    '6M': 6,
    '1Y': 12,
    '3Y': 36
  };
  startDate.setMonth(startDate.getMonth() - periodMonths[period]);
  return navs.filter((item) => {
    const date = parseNavDate(item.navDate);
    return date ? date >= startDate : true;
  });
}

function parseNavDate(value: string) {
  if (!/^\d{8}$/.test(value)) {
    return null;
  }
  return new Date(Number(value.slice(0, 4)), Number(value.slice(4, 6)) - 1, Number(value.slice(6, 8)));
}

function formatNavDate(value: string) {
  if (!/^\d{8}$/.test(value)) {
    return value;
  }
  return `${value.slice(0, 4)}-${value.slice(4, 6)}-${value.slice(6, 8)}`;
}

function formatChartNumber(value: number, unit?: string) {
  const digits = Math.abs(value) >= 10 ? 2 : 4;
  return `${value.toFixed(digits)}${unit || ''}`;
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

function formatValue(value?: number | string | null) {
  return value == null || value === '' ? '-' : value;
}

function toNumber(value?: number | string | null) {
  if (value == null || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
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
    funds: '基金管理',
    users: '用户管理',
    roles: '角色管理',
    menus: '菜单管理'
  };
  return labels[view];
}
