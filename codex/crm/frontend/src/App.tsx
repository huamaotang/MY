import {
  ContactsOutlined,
  DashboardOutlined,
  FundOutlined,
  LogoutOutlined,
  MenuOutlined,
  InboxOutlined,
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
  Upload,
  Typography
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Customer,
  Fund,
  FundDailyValuation,
  FundDetail,
  FundFeature,
  FinanceNews,
  FundHolding,
  FundNav,
  FundPerformance,
  FundRating,
  PortfolioHoldingBatch,
  PortfolioHoldingImportPreview,
  PortfolioHoldingImportRow,
  PortfolioTradeAdjustment,
  StockQuote,
  Role,
  SysMenu,
  UserFundHolding,
  User,
  deleteCustomer,
  deleteFund,
  deleteFinanceNews,
  deleteMenu,
  deleteRole,
  deleteUser,
  confirmPortfolioHoldingImport,
  getPortfolioImport,
  getFundDetail,
  listFundFeatures,
  listFundValuations,
  listPortfolioHoldings,
  listPortfolioImports,
  listFundHoldings,
  listFundNavs,
  listFundRatings,
  listFinanceNews,
  listStocks,
  listStockHistory,
  listFunds,
  listCustomers,
  listMenus,
  listRoles,
  listUsers,
  login,
  previewPortfolioHoldings,
  saveCustomer,
  saveFund,
  saveMenu,
  saveRole,
  saveUser
} from './api';

const { Header, Sider, Content } = Layout;

type ViewKey = 'dashboard' | 'customers' | 'contacts' | 'follows' | 'funds' | 'portfolio' | 'stocks' | 'news' | 'users' | 'roles' | 'menus';

type WorkspaceState = {
  activeView: ViewKey;
  openViews: ViewKey[];
};

const DEFAULT_PAGE_SIZE = 10;
const PAGE_SIZE_OPTIONS = [10, 20, 50, 100];
const CHART_NAV_SIZE = 1000;
const WORKSPACE_STORAGE_KEY = 'crm_workspace_tabs';
const VIEW_KEYS: ViewKey[] = [
  'dashboard',
  'customers',
  'contacts',
  'follows',
  'funds',
  'portfolio',
  'stocks',
  'news',
  'users',
  'roles',
  'menus'
];

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
    children: [
      { key: 'funds', icon: <FundOutlined />, label: '基金管理' },
      { key: 'portfolio', icon: <InboxOutlined />, label: '持仓导入' },
      { key: 'stocks', icon: <FundOutlined />, label: '股票行情' },
      { key: 'news', icon: <MenuOutlined />, label: '资讯管理' }
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
  const [workspace, setWorkspace] = useState<WorkspaceState>(loadWorkspaceState);
  const { activeView, openViews } = workspace;

  useEffect(() => {
    sessionStorage.setItem(WORKSPACE_STORAGE_KEY, JSON.stringify(workspace));
  }, [workspace]);

  const openView = (view: ViewKey) => {
    setWorkspace((current) => ({
      activeView: view,
      openViews: current.openViews.includes(view) ? current.openViews : [...current.openViews, view]
    }));
  };

  const closeView = (view: ViewKey) => {
    if (view === 'dashboard') {
      return;
    }
    setWorkspace((current) => {
      const closingIndex = current.openViews.indexOf(view);
      const nextViews = current.openViews.filter((item) => item !== view);
      return {
        activeView: current.activeView === view
          ? nextViews[Math.max(0, closingIndex - 1)] || 'dashboard'
          : current.activeView,
        openViews: nextViews
      };
    });
  };

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
            selectedKeys={[activeView]}
            defaultOpenKeys={['crm', 'products', 'system']}
            items={menuItems}
            onClick={(item) => {
              if (isViewKey(item.key)) {
                openView(item.key);
              }
            }}
          />
        </Sider>
        <Layout>
          <Header className="topbar">
            <Typography.Text strong>客户管理系统</Typography.Text>
            <Button
              icon={<LogoutOutlined />}
              onClick={() => {
                localStorage.removeItem('crm_token');
                sessionStorage.removeItem(WORKSPACE_STORAGE_KEY);
                setWorkspace(defaultWorkspaceState());
                setToken(null);
              }}
            >
              退出
            </Button>
          </Header>
          <Content className="content workspace-content">
            <Tabs
              className="workspace-tabs"
              type="editable-card"
              hideAdd
              destroyInactiveTabPane={false}
              activeKey={activeView}
              onChange={(key) => {
                if (isViewKey(key)) {
                  setWorkspace((current) => ({ ...current, activeView: key }));
                }
              }}
              onEdit={(targetKey, action) => {
                if (action === 'remove' && isViewKey(targetKey)) {
                  closeView(targetKey);
                }
              }}
              items={openViews.map((view) => ({
                key: view,
                label: labelOf(view),
                closable: view !== 'dashboard',
                children: <WorkspaceView view={view} />
              }))}
            />
          </Content>
        </Layout>
      </Layout>
    </AntApp>
  );
}

function defaultWorkspaceState(): WorkspaceState {
  return { activeView: 'dashboard', openViews: ['dashboard'] };
}

function loadWorkspaceState(): WorkspaceState {
  try {
    const stored = sessionStorage.getItem(WORKSPACE_STORAGE_KEY);
    if (!stored) {
      return defaultWorkspaceState();
    }
    const saved = JSON.parse(stored) as Partial<WorkspaceState>;
    const savedViews = Array.isArray(saved.openViews) ? saved.openViews.filter(isViewKey) : [];
    const openViews = Array.from(new Set<ViewKey>(['dashboard', ...savedViews]));
    const activeView = isViewKey(saved.activeView) && openViews.includes(saved.activeView)
      ? saved.activeView
      : 'dashboard';
    return { activeView, openViews };
  } catch {
    return defaultWorkspaceState();
  }
}

function isViewKey(value: unknown): value is ViewKey {
  return typeof value === 'string' && VIEW_KEYS.includes(value as ViewKey);
}

function WorkspaceView({ view }: { view: ViewKey }) {
  switch (view) {
    case 'dashboard':
      return <Dashboard />;
    case 'customers':
      return <CustomerList />;
    case 'funds':
      return <FundList />;
    case 'portfolio':
      return <PortfolioAdmin />;
    case 'stocks':
      return <StockMarket />;
    case 'news':
      return <NewsAdmin />;
    case 'users':
      return <UserAdmin />;
    case 'roles':
      return <RoleAdmin />;
    case 'menus':
      return <MenuAdmin />;
    case 'contacts':
    case 'follows':
      return <Placeholder title={labelOf(view)} />;
  }
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

function NewsAdmin() {
  const { message } = AntApp.useApp();
  const [rows, setRows] = useState<FinanceNews[]>([]);
  const [keyword, setKeyword] = useState('');
  const [categoryTag, setCategoryTag] = useState(-1);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const load = async (page = current, size = pageSize, tag = categoryTag) => {
    setLoading(true);
    try { const result = await listFinanceNews({ current: page, size, keyword, categoryTag: tag < 0 ? undefined : tag }); setRows(result.records); setCurrent(result.current); setPageSize(result.size); setTotal(result.total); }
    catch (error) { message.error((error as Error).message); } finally { setLoading(false); }
  };
  useEffect(() => { load(1); }, []);
  const columns: ColumnsType<FinanceNews> = [
    { title: '时间', dataIndex: 'createTime', width: 180 },
    { title: '类型', dataIndex: 'categoryName', width: 90, render: (value) => <Tag color={value === 'A股' ? 'red' : 'blue'}>{value}</Tag> },
    { title: '资讯内容', dataIndex: 'content', ellipsis: true },
    { title: '标签', dataIndex: 'tagsJson', width: 180, render: (value) => value || '-' },
    { title: '原文', dataIndex: 'docUrl', width: 90, render: (value) => value ? <a href={value} target="_blank" rel="noreferrer">查看</a> : '-' },
    { title: '操作', width: 90, render: (_, row) => <Popconfirm title="确认删除？" onConfirm={async () => { await deleteFinanceNews(row.id); message.success('已删除'); load(); }}><Button danger type="link">删除</Button></Popconfirm> }
  ];
  return <div className="page"><div className="page-header"><Typography.Title level={3}>7×24 资讯管理</Typography.Title><Space wrap><Select value={categoryTag} style={{ width: 110 }} options={[{ label: '全部', value: -1 }, { label: 'A股', value: 10 }, { label: '宏观', value: 1 }, { label: '产业', value: 110 }, { label: '公司', value: 3 }, { label: '数据', value: 4 }, { label: '市场', value: 5 }, { label: '国际', value: 102 }, { label: '观点', value: 6 }, { label: '央行', value: 7 }, { label: '其他', value: 8 }]} onChange={(tag) => { setCategoryTag(tag); load(1, pageSize, tag); }}/><Input value={keyword} onChange={e => setKeyword(e.target.value)} onPressEnter={() => load(1)} placeholder="搜索资讯内容"/><Button onClick={() => load(1)} icon={<SearchOutlined />}>查询</Button></Space></div><Table rowKey="id" loading={loading} columns={columns} dataSource={rows} pagination={{ current, pageSize, total, showSizeChanger: true, onChange: load }}/></div>;
}

function PortfolioAdmin() {
  const { message } = AntApp.useApp();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState<PortfolioHoldingImportPreview | null>(null);
  const [holdings, setHoldings] = useState<UserFundHolding[]>([]);
  const [imports, setImports] = useState<PortfolioHoldingBatch[]>([]);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [total, setTotal] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [sourceLabel, setSourceLabel] = useState<'alipay' | 'tencent'>('alipay');
  const [importType, setImportType] = useState<'holding' | 'trade'>('holding');

  const loadHoldings = async (page = current, size = pageSize) => {
    setLoading(true);
    try {
      const result = await listPortfolioHoldings({ current: page, size, keyword });
      setHoldings(result.records);
      setTotal(result.total);
      setCurrent(result.current);
      setPageSize(result.size);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  };

  const loadImports = async () => {
    setHistoryLoading(true);
    try {
      const result = await listPortfolioImports({ current: 1, size: 20 });
      setImports(result.records);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    loadHoldings(1);
    loadImports();
  }, []);

  const updateRow = (rowNo: number, patch: Partial<PortfolioHoldingImportRow>) => {
    setPreview((currentPreview) => {
      if (!currentPreview) {
        return currentPreview;
      }
      return {
        ...currentPreview,
        rows: currentPreview.rows.map((row) => (row.rowNo === rowNo ? { ...row, ...patch } : row))
      };
    });
  };

  const updateTradeAdjustment = (groupKey: string, patch: Partial<PortfolioTradeAdjustment>) => {
    setPreview((currentPreview) => {
      if (!currentPreview) {
        return currentPreview;
      }
      return {
        ...currentPreview,
        tradeAdjustments: (currentPreview.tradeAdjustments || []).map((row) =>
          row.groupKey === groupKey ? { ...row, ...patch } : row
        )
      };
    });
  };

  const confirm = async () => {
    if (!preview) {
      message.error('请先上传截图');
      return;
    }
    if (preview.importType === 'holding') {
      const unresolved = preview.rows.find((row) => !row.fundCode);
      if (unresolved) {
        message.error(`第 ${unresolved.rowNo} 行还没有绑定基金代码`);
        return;
      }
    }
    setUploading(true);
    try {
      const result = await confirmPortfolioHoldingImport(
        preview.importId,
        preview.importType === 'trade'
          ? {
              tradeMappings: (preview.tradeAdjustments || [])
                .filter((row) => row.fundCode)
                .map((row) => ({ groupKey: row.groupKey, fundCode: row.fundCode! }))
            }
          : {
              screenshotDate: preview.screenshotDate,
              items: preview.rows.map((row) => ({
                rowNo: row.rowNo,
                fundCode: row.fundCode,
                fundName: row.fundName,
                holdingAmount: row.holdingAmount,
                holdingProfit: row.holdingProfit,
                holdingReturnRate: row.holdingReturnRate,
                holdingCost: row.holdingCost,
                yesterdayProfit: row.yesterdayProfit,
                todayProfit: row.todayProfit,
                holdingShares: row.holdingShares,
                costNav: row.costNav,
                screenshotDate: row.screenshotDate,
                confidence: row.confidence,
                rawTexts: row.rawTexts
              }))
            }
      );
      if (preview.importType === 'trade') {
        message.success(
          `已调整 ${result.affectedHoldingCount} 只基金，应用 ${result.appliedTransactionCount} 条，跳过 ${result.skippedTransactionCount} 条`
        );
        if (result.warnings?.length) {
          message.warning(result.warnings.join('；'));
        }
      } else {
        message.success(`已覆盖 ${result.affectedHoldingCount} 只基金持仓`);
      }
      setPreview(null);
      await Promise.all([loadHoldings(1), loadImports()]);
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setUploading(false);
    }
  };

  const openImportDetail = async (importId: number) => {
    try {
      setPreview(await getPortfolioImport(importId));
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const chooseFiles = () => fileInputRef.current?.click();

  const previewColumns: ColumnsType<PortfolioHoldingImportRow> = [
    { title: '行号', dataIndex: 'rowNo', width: 70 },
    { title: '基金名称', dataIndex: 'fundName', width: 240, render: (value, row) => <div><div>{value}</div><Typography.Text type="secondary" style={{ fontSize: 12 }}>{(row.rawTexts || []).join(' / ')}</Typography.Text></div> },
    {
      title: '基金代码',
      dataIndex: 'fundCode',
      width: 220,
      render: (value, row) => (
        <Space direction="vertical" size={4}>
          <Select
              showSearch
              allowClear
              value={value}
              disabled={preview?.status === 'CONFIRMED'}
              style={{ width: 200 }}
              placeholder="选择候选基金"
              optionFilterProp="label"
              onChange={(nextFundCode) => {
                const selected = row.candidates?.find((candidate) => candidate.fundCode === nextFundCode);
                updateRow(row.rowNo, {
                  fundCode: nextFundCode,
                  fundName: selected?.fundName || row.fundName
                });
              }}
              options={[
                ...(value && !(row.candidates || []).some((candidate) => candidate.fundCode === value)
                  ? [{ value, label: `${value} ${row.fundName}` }]
                  : []),
                ...(row.candidates || []).map((candidate) => ({
                  value: candidate.fundCode,
                  label: `${candidate.fundCode} ${candidate.fundName} (${candidate.score ?? 0})`
                }))
              ]}
          />
          <Input
            value={value}
            disabled={preview?.status === 'CONFIRMED'}
            maxLength={20}
            placeholder="也可手工输入基金代码"
            onChange={(event) => updateRow(row.rowNo, { fundCode: event.target.value.trim() || undefined })}
          />
        </Space>
      )
    },
    { title: '持有金额', dataIndex: 'holdingAmount', width: 120, render: formatMoney },
    { title: '持有收益', dataIndex: 'holdingProfit', width: 120, render: renderSignedValue },
    { title: '持有收益率', dataIndex: 'holdingReturnRate', width: 120, render: renderSignedPercent },
    { title: '净值成本', dataIndex: 'costNav', width: 120, render: formatValue },
    { title: '昨日收益', dataIndex: 'yesterdayProfit', width: 120, render: renderSignedValue },
    { title: '置信度', dataIndex: 'confidence', width: 100, render: formatValue }
  ];

  const tradeColumns: ColumnsType<PortfolioTradeAdjustment> = [
    {
      title: '基金',
      dataIndex: 'fundName',
      width: 260,
      render: (value, row) => (
        <Space direction="vertical" size={4}>
          <Typography.Text>{value}</Typography.Text>
          <Select
            showSearch
            allowClear
            value={row.fundCode}
            disabled={preview?.status === 'CONFIRMED'}
            style={{ width: 240 }}
            placeholder="仅可映射到当前账户持仓"
            optionFilterProp="label"
            onChange={(fundCode) => {
              const selected = row.candidates?.find((candidate) => candidate.fundCode === fundCode);
              updateTradeAdjustment(row.groupKey, {
                fundCode,
                fundName: selected?.fundName || row.fundName,
                applicable: Boolean(fundCode)
              });
            }}
            options={(row.candidates || []).map((candidate) => ({
              value: candidate.fundCode,
              label: `${candidate.fundCode} ${candidate.fundName} (${candidate.score ?? 0})`
            }))}
          />
        </Space>
      )
    },
    { title: '买入合计', dataIndex: 'buyAmount', width: 120, render: formatMoney },
    { title: '卖出合计', dataIndex: 'sellAmount', width: 120, render: formatMoney },
    { title: '净增减', dataIndex: 'netAmount', width: 120, render: renderSignedValue },
    {
      title: '金额变化',
      width: 190,
      render: (_, row) => `${formatMoney(row.currentHoldingAmount)} → ${formatMoney(row.projectedHoldingAmount)}`
    },
    {
      title: '交易',
      width: 120,
      render: (_, row) => `应用 ${row.transactionCount} / 跳过 ${row.skippedCount}`
    },
    {
      title: '状态',
      width: 110,
      render: (_, row) => row.applicable ? <Tag color="green">可应用</Tag> : <Tag color="orange">将跳过</Tag>
    },
    {
      title: '提示',
      dataIndex: 'warnings',
      width: 260,
      render: (value: string[]) => value?.length ? value.join('；') : '-'
    }
  ];

  const holdingColumns: ColumnsType<UserFundHolding> = [
    { title: '基金代码', dataIndex: 'fundCode', width: 120 },
    { title: '基金名称', dataIndex: 'fundName', width: 220 },
    { title: '持有金额', dataIndex: 'holdingAmount', width: 110, render: formatMoney },
    { title: '预估涨跌幅', dataIndex: 'estimatedChangeRate', width: 120, render: renderSignedPercent },
    { title: '预估盈亏', dataIndex: 'estimatedDailyProfit', width: 110, render: renderSignedValue },
    { title: '累计预估涨跌幅', dataIndex: 'estimatedCumulativeChangeRate', width: 145, render: renderSignedPercent },
    { title: '累计预估盈亏', dataIndex: 'estimatedCumulativeProfit', width: 130, render: renderSignedValue },
    { title: '估值后金额', dataIndex: 'estimatedHoldingAmount', width: 120, render: formatMoney },
    { title: '预估净值', dataIndex: 'estimatedUnitNav', width: 110, render: formatValue },
    { title: '估值日期', dataIndex: 'valuationDate', width: 110 },
    { title: '行情覆盖率', dataIndex: 'valuationCoverageRate', width: 120, render: formatPercent },
    { title: '重仓报告日', dataIndex: 'holdingReportDate', width: 115, render: formatNavDate },
    { title: '持仓截止日', dataIndex: 'holdingCutoffDate', width: 115, render: formatNavDate },
    { title: '估值更新时间', dataIndex: 'valuationUpdatedAt', width: 170 },
    { title: '持有收益', dataIndex: 'holdingProfit', width: 110, render: renderSignedValue },
    { title: '持有收益率', dataIndex: 'holdingReturnRate', width: 110, render: renderSignedPercent },
    { title: '净值成本', dataIndex: 'costNav', width: 110, render: formatValue },
    { title: '昨日收益', dataIndex: 'yesterdayProfit', width: 110, render: renderSignedValue },
    { title: '截图日期', dataIndex: 'screenshotDate', width: 110 },
    { title: '最近导入', dataIndex: 'latestImportAt', width: 170 }
  ];

  const valuedHoldings = holdings.filter((holding) => toNumber(holding.estimatedDailyProfit) != null);
  const pageEstimatedProfit = valuedHoldings.reduce(
    (totalValue, holding) => totalValue + (toNumber(holding.estimatedDailyProfit) || 0),
    0
  );
  const cumulativeValuedHoldings = holdings.filter(
    (holding) => toNumber(holding.estimatedCumulativeProfit) != null
  );
  const pageEstimatedCumulativeProfit = cumulativeValuedHoldings.reduce(
    (totalValue, holding) => totalValue + (toNumber(holding.estimatedCumulativeProfit) || 0),
    0
  );

  const importColumns: ColumnsType<PortfolioHoldingBatch> = [
    { title: '批次ID', dataIndex: 'id', width: 90 },
    { title: '状态', dataIndex: 'status', width: 100 },
    { title: '来源', dataIndex: 'sourceLabel', width: 110, render: (value) => value === 'tencent' ? '腾讯理财通' : '支付宝' },
    { title: '类型', dataIndex: 'importType', width: 100, render: (value) => value === 'trade' ? '交易增减' : '持仓覆盖' },
    { title: '截图日期', dataIndex: 'screenshotDate', width: 110 },
    { title: '图片数', dataIndex: 'imageCount', width: 90 },
    { title: '基金数', dataIndex: 'itemCount', width: 90 },
    { title: '交易数', dataIndex: 'transactionCount', width: 90 },
    { title: '应用/跳过', width: 110, render: (_, row) => row.importType === 'trade' ? `${row.appliedCount}/${row.skippedCount}` : '-' },
    { title: '确认时间', dataIndex: 'confirmedAt', width: 170 },
    { title: '创建时间', dataIndex: 'createdAt', width: 170 },
    {
      title: '操作',
      width: 90,
      render: (_, row) => (
        <Button type="link" onClick={() => openImportDetail(row.id)}>
          查看
        </Button>
      )
    }
  ];

  const previewItems = preview?.rows || [];
  const tradePreviewItems = preview?.tradeAdjustments || [];

  return (
    <div className="page">
      <div className="page-header">
        <Typography.Title level={3}>持仓导入</Typography.Title>
        <Space wrap>
          <Input
            placeholder="筛选当前持仓"
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={() => loadHoldings(1)}
          />
          <Button icon={<SearchOutlined />} onClick={() => loadHoldings(1)}>
            查询
          </Button>
          <Select
            value={sourceLabel}
            style={{ width: 130 }}
            onChange={setSourceLabel}
            options={[
              { value: 'alipay', label: '支付宝' },
              { value: 'tencent', label: '腾讯理财通' }
            ]}
          />
          <Select
            value={importType}
            style={{ width: 130 }}
            onChange={setImportType}
            options={[
              { value: 'holding', label: '持仓列表覆盖' },
              { value: 'trade', label: '交易批量增减' }
            ]}
          />
          <Button icon={<InboxOutlined />} onClick={chooseFiles}>
            上传截图
          </Button>
          <Button onClick={loadImports}>刷新历史</Button>
        </Space>
      </div>
      <input
        ref={fileInputRef}
        type="file"
        accept=".jpg,.jpeg,.png,image/jpeg,image/png"
        multiple
        style={{ display: 'none' }}
        onChange={async (event) => {
          const files = Array.from(event.target.files || []).slice(0, 3);
          event.target.value = '';
          if (!files.length) {
            return;
          }
          setUploading(true);
          try {
            setPreview(await previewPortfolioHoldings(files, sourceLabel, importType));
          } catch (error) {
            message.error((error as Error).message);
          } finally {
            setUploading(false);
          }
        }}
      />
      <Tabs
        items={[
          {
            key: 'preview',
            label: preview
              ? `识别预览 (${preview.importType === 'trade' ? tradePreviewItems.length : preview.rows.length})`
              : '识别预览',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={16}>
                {preview ? (
                  <>
                    <Space wrap>
                      <Typography.Text>来源：{preview.sourceLabel}</Typography.Text>
                      <Typography.Text>类型：{preview.importType === 'trade' ? '交易批量增减' : '持仓列表覆盖'}</Typography.Text>
                      <Typography.Text>状态：{preview.status}</Typography.Text>
                      <Typography.Text>图片数：{preview.imageCount}</Typography.Text>
                      <Typography.Text>截图日期：{preview.screenshotDate || '-'}</Typography.Text>
                    </Space>
                    {preview.warnings?.length ? <Typography.Text type="danger">{preview.warnings.join('；')}</Typography.Text> : null}
                    {preview.importType === 'trade' ? (
                      <Table
                        rowKey="groupKey"
                        loading={uploading}
                        columns={tradeColumns}
                        dataSource={tradePreviewItems}
                        pagination={false}
                        scroll={{ x: 1300 }}
                      />
                    ) : (
                      <Table rowKey="rowNo" loading={uploading} columns={previewColumns} dataSource={previewItems} pagination={false} scroll={{ x: 1350 }} />
                    )}
                    <Space>
                      {preview.status === 'PREVIEWED' ? (
                        <Button type="primary" loading={uploading} onClick={confirm}>
                          确认入库
                        </Button>
                      ) : (
                        <Tag color="green">已入库</Tag>
                      )}
                      <Button onClick={() => setPreview(null)}>取消预览</Button>
                    </Space>
                  </>
                ) : (
                  <div className="portfolio-dropzone" onClick={chooseFiles}>
                    <InboxOutlined style={{ fontSize: 40 }} />
                    <Typography.Title level={4} style={{ margin: 0 }}>
                      点击上传{sourceLabel === 'tencent' ? '腾讯理财通' : '支付宝'}
                      {importType === 'trade' ? '交易明细' : '基金持仓'}截图
                    </Typography.Title>
                    <Typography.Text type="secondary">
                      支持 1-3 张 PNG / JPG；{importType === 'trade' ? '仅调整已有持仓金额' : '确认后覆盖同平台持仓'}。
                    </Typography.Text>
                  </div>
                )}
              </Space>
            )
          },
          {
            key: 'holdings',
            label: '当前持仓',
            children: (
              <Space direction="vertical" style={{ width: '100%' }} size={12}>
                <Typography.Text>
                  本页预估盈亏：{valuedHoldings.length ? renderSignedValue(pageEstimatedProfit) : '-'}
                  {valuedHoldings.length ? `（已估值 ${valuedHoldings.length}/${holdings.length} 只基金）` : ''}
                  {'；累计预估盈亏：'}
                  {cumulativeValuedHoldings.length ? renderSignedValue(pageEstimatedCumulativeProfit) : '-'}
                </Typography.Text>
                <Table
                  rowKey="id"
                  loading={loading}
                  columns={holdingColumns}
                  dataSource={holdings}
                  scroll={{ x: 2620 }}
                  pagination={{ current, pageSize, total, showSizeChanger: true, onChange: (page, size) => loadHoldings(page, size) }}
                />
              </Space>
            )
          },
          {
            key: 'imports',
            label: '导入历史',
            children: <Table rowKey="id" loading={historyLoading} columns={importColumns} dataSource={imports} pagination={false} />
          }
        ]}
      />
    </div>
  );
}

function StockMarket() {
  const { message } = AntApp.useApp();
  const [rows, setRows] = useState<StockQuote[]>([]);
  const [keyword, setKeyword] = useState('');
  const [marketCode, setMarketCode] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [total, setTotal] = useState(0);
  const [sortField, setSortField] = useState<string>();
  const [sortOrder, setSortOrder] = useState<string>();
  const [selected, setSelected] = useState<StockQuote>();
  const [history, setHistory] = useState<StockQuote[]>([]);
  const load = async (page = current, size = pageSize, field = sortField, order = sortOrder) => {
    setLoading(true);
    try {
      const result = await listStocks({ current: page, size, keyword, marketCode, sortField: field, sortOrder: order });
      setRows(result.records); setCurrent(result.current); setPageSize(result.size); setTotal(result.total);
    } catch (error) { message.error((error as Error).message); } finally { setLoading(false); }
  };
  useEffect(() => { load(1); }, []);
  const openHistory = async (row: StockQuote) => {
    setSelected(row);
    try { setHistory((await listStockHistory(row.stockCode, 1, 100)).records); }
    catch (error) { message.error((error as Error).message); }
  };
  const signed = (value?: number) => {
    const color = value == null ? undefined : value > 0 ? '#cf1322' : value < 0 ? '#389e0d' : undefined;
    return <span style={{ color }}>{value == null ? '-' : `${value}%`}</span>;
  };
  const columns: ColumnsType<StockQuote> = [
    { title: '代码', dataIndex: 'stockCode', width: 90, sorter: true },
    { title: '名称', dataIndex: 'stockName', width: 110, sorter: true },
    { title: '最新价', dataIndex: 'latestPrice', width: 90, sorter: true },
    { title: '涨跌幅', dataIndex: 'changeRate', width: 95, sorter: true, render: signed },
    { title: '涨跌额', dataIndex: 'changeAmount', width: 90, sorter: true },
    { title: '成交额', dataIndex: 'amount', width: 115, sorter: true, render: (v) => compactNumber(v) },
    { title: '最后更新时间', dataIndex: 'updatedAt', width: 170 },
    { title: '备注', dataIndex: 'comment', width: 220 },
    { title: '换手率', dataIndex: 'turnoverRate', width: 95, sorter: true, render: signed },
    { title: '量比', dataIndex: 'volumeRatio', width: 75, sorter: true },
    { title: '市盈率', dataIndex: 'peDynamic', width: 90, sorter: true },
    { title: '市净率', dataIndex: 'pbRatio', width: 80, sorter: true },
    { title: '总市值', dataIndex: 'totalMarketCap', width: 110, sorter: true, render: (v) => compactNumber(v) },
    { title: '60日', dataIndex: 'changeRate60d', width: 85, sorter: true, render: signed },
    { title: '年初至今', dataIndex: 'changeRateYtd', width: 95, sorter: true, render: signed },
    { title: '操作', fixed: 'right', width: 80, render: (_, row) => <Button type="link" onClick={() => openHistory(row)}>历史</Button> }
  ];
  return <div className="page">
    <div className="page-header"><Typography.Title level={3}>股票行情</Typography.Title>
      <Space><Select allowClear placeholder="市场" style={{ width: 110 }} value={marketCode} onChange={setMarketCode} options={[{ value: 1, label: '上海' }, { value: 0, label: '深圳/北京' }, { value: 116, label: '香港' }]}/><Input value={keyword} onChange={e => setKeyword(e.target.value)} onPressEnter={() => load(1)} placeholder="代码或名称"/><Button icon={<SearchOutlined/>} onClick={() => load(1)}>查询</Button></Space>
    </div>
    <Table rowKey="stockCode" scroll={{ x: 1680 }} loading={loading} columns={columns} dataSource={rows}
      pagination={{ current, pageSize, total, showSizeChanger: true, pageSizeOptions: PAGE_SIZE_OPTIONS }}
      onChange={(pagination, _filters, sorter) => { const item = Array.isArray(sorter) ? sorter[0] : sorter; const field = item?.field as string | undefined; const order = item?.order || undefined; setSortField(field); setSortOrder(order); load(pagination.current, pagination.pageSize, field, order); }}/>
    <Modal open={!!selected} width={1100} footer={null} onCancel={() => { setSelected(undefined); setHistory([]); }} title={`${selected?.stockName || ''} ${selected?.stockCode || ''} 日行情`}>
      <Table rowKey="tradeDate" size="small" scroll={{ x: 1250 }} pagination={false} dataSource={history} columns={[
        { title: '日期', dataIndex: 'tradeDate', width: 110 }, { title: '最后更新时间', dataIndex: 'updatedAt', width: 170 }, { title: '备注', dataIndex: 'comment', width: 220 },
        { title: '开盘', dataIndex: 'openPrice' },
        { title: '最高', dataIndex: 'highPrice' }, { title: '最低', dataIndex: 'lowPrice' },
        { title: '收盘', dataIndex: 'latestPrice' }, { title: '涨跌幅', dataIndex: 'changeRate', render: signed },
        { title: '成交量', dataIndex: 'volume', render: compactNumber }, { title: '成交额', dataIndex: 'amount', render: compactNumber },
        { title: '换手率', dataIndex: 'turnoverRate', render: signed }, { title: '市盈率', dataIndex: 'peDynamic' },
        { title: '市净率', dataIndex: 'pbRatio' }
      ]}/>
    </Modal>
  </div>;
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
  const [sortField, setSortField] = useState<string>();
  const [sortOrder, setSortOrder] = useState<string>();
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<Fund | null>(null);
  const [detailFundCode, setDetailFundCode] = useState<string | null>(null);
  const [form] = Form.useForm<Fund>();

  const load = async (page = current, size = pageSize, nextSortField = sortField, nextSortOrder = sortOrder) => {
    setLoading(true);
    try {
      const result = await listFunds({ current: page, size, keyword, fundType, sortField: nextSortField, sortOrder: nextSortOrder });
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
    { title: '基金代码', dataIndex: 'fundCode', fixed: 'left', width: 120, sorter: true },
    { title: '基金名称', dataIndex: 'fundName', fixed: 'left', width: 240, sorter: true },
    {
      title: '可购买',
      dataIndex: 'canBuy',
      width: 100,
      sorter: true,
      render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '可购' : '不可购'}</Tag>
    },
    {
      title: '当日预估',
      key: 'estimatedChangeRate',
      width: 120,
      render: (_, row) => renderSignedPercent(row.latestValuation?.estimatedChangeRate)
    },
    {
      title: '估值日期',
      key: 'valuationDate',
      width: 110,
      render: (_, row) => row.latestValuation?.valuationDate || '-'
    },
    { title: '类型', dataIndex: 'fundType', width: 120, render: (value) => value || '-', sorter: true },
    { title: '基金经理', dataIndex: 'fundManager', width: 160, render: (value) => value || '-', sorter: true },
    { title: '管理人', dataIndex: 'managementCompany', width: 220, render: (value) => value || '-', sorter: true },
    { title: '规模', dataIndex: 'netAssetScale', width: 140, render: (value) => value || '-', sorter: true },
    { title: '成立日期', dataIndex: 'inceptionDate', width: 130, render: (value) => value || '-', sorter: true },
    { title: '招商评级', key: 'zhaoshangRating', width: 110, render: (_, row) => renderRatingStars(row.latestRating?.zhaoshangRating), sorter: true },
    { title: '晨星评级', key: 'morningStarRating', width: 110, render: (_, row) => renderRatingStars(row.latestRating?.morningStarRating), sorter: true },
    ...performanceListColumns(),
    { title: '标准差（近3年）', key: 'standardDeviation', width: 140, render: (_, row) => threeYearFeatureValue(row.features, 'standardDeviation'), sorter: true },
    { title: '夏普比率（近3年）', key: 'sharpeRatio', width: 150, render: (_, row) => threeYearFeatureValue(row.features, 'sharpeRatio'), sorter: true },
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
        scroll={{ x: 4030 }}
        onChange={(pagination, _filters, sorter) => {
          const selected = Array.isArray(sorter) ? sorter[0] : sorter;
          const field = selected?.order ? String(selected.field ?? selected.columnKey ?? '') : undefined;
          const order = selected?.order ?? undefined;
          setSortField(field);
          setSortOrder(order);
          load(pagination.current || 1, pagination.pageSize || pageSize, field, order);
        }}
        pagination={{
          total,
          current,
          pageSize,
          showSizeChanger: true,
          pageSizeOptions: PAGE_SIZE_OPTIONS,
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
  const [valuations, setValuations] = useState<FundDailyValuation[]>([]);
  const [valuationTotal, setValuationTotal] = useState(0);
  const [valuationCurrent, setValuationCurrent] = useState(1);
  const [valuationPageSize, setValuationPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [features, setFeatures] = useState<FundFeature[]>([]);
  const [ratings, setRatings] = useState<FundRating[]>([]);

  const loadDetail = async () => {
    if (!fundCode) {
      return;
    }
    setLoading(true);
    try {
      const result = await getFundDetail(fundCode);
      setDetail(result);
      setFeatures(result.features || []);
      setRatings(result.ratings || []);
      await Promise.all([loadChartNavs(), loadNavs(1), loadHoldings(1), loadValuations(1)]);
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

  const loadValuations = async (page = valuationCurrent, size = valuationPageSize) => {
    if (!fundCode) {
      return;
    }
    const result = await listFundValuations(fundCode, { current: page, size });
    setValuations(result.records);
    setValuationTotal(result.total);
    setValuationCurrent(result.current);
    setValuationPageSize(result.size);
  };

  const refreshFeatures = async () => {
    if (!fundCode) {
      return;
    }
    setFeatures(await listFundFeatures(fundCode));
  };

  const refreshRatings = async () => {
    if (!fundCode) {
      return;
    }
    setRatings(await listFundRatings(fundCode));
  };

  useEffect(() => {
    if (open && fundCode) {
      loadDetail();
    } else {
      setDetail(null);
      setChartNavs([]);
      setNavs([]);
      setHoldings([]);
      setValuations([]);
      setFeatures([]);
      setRatings([]);
    }
  }, [open, fundCode]);

  const navColumns: ColumnsType<FundNav> = [
    { title: '净值日期', dataIndex: 'navDate', width: 120 },
    { title: '单位净值', dataIndex: 'unitNav', width: 120, render: formatValue },
    { title: '累计净值', dataIndex: 'accumulatedNav', width: 120, render: formatValue },
    { title: '日增长率', dataIndex: 'dailyGrowthRate', width: 120, render: renderSignedPercent }
  ];

  const holdingColumns: ColumnsType<FundHolding> = [
    { title: '报告日期', dataIndex: 'reportDate', width: 110, render: formatNavDate },
    { title: '截止日', dataIndex: 'cutoffDate', width: 110, render: formatNavDate },
    { title: '排名', dataIndex: 'rankNo', width: 80 },
    { title: '股票代码', dataIndex: 'stockCode', width: 110 },
    { title: '股票名称', dataIndex: 'stockName', width: 140 },
    { title: '实时价格', dataIndex: 'latestPrice', width: 110, render: formatValue },
    { title: '当日涨跌幅', dataIndex: 'changeRate', width: 125, render: renderSignedPercent },
    { title: '行情时间', dataIndex: 'quoteTime', width: 170, render: formatDateTime },
    { title: '占净值比例', dataIndex: 'netValueRatio', width: 120, render: renderSignedPercent },
    { title: '持股数(万股)', dataIndex: 'holdingShares10k', width: 130, render: formatValue },
    { title: '持仓市值(万元)', dataIndex: 'holdingMarketValue10k', width: 150, render: formatValue }
  ];

  const valuationColumns: ColumnsType<FundDailyValuation> = [
    { title: '估值日期', dataIndex: 'valuationDate', width: 110 },
    { title: '预估涨跌幅', dataIndex: 'estimatedChangeRate', width: 120, render: renderSignedPercent },
    { title: '预估单位净值', dataIndex: 'estimatedUnitNav', width: 130, render: formatValue },
    { title: '基准净值日期', dataIndex: 'baseNavDate', width: 125 },
    { title: '基准单位净值', dataIndex: 'baseUnitNav', width: 130, render: formatValue },
    { title: '重仓报告日', dataIndex: 'holdingReportDate', width: 115, render: formatNavDate },
    { title: '持仓截止日', dataIndex: 'holdingCutoffDate', width: 115, render: formatNavDate },
    { title: '重仓占净值', dataIndex: 'holdingWeight', width: 120, render: formatPercent },
    { title: '有行情占净值', dataIndex: 'quotedHoldingWeight', width: 130, render: formatPercent },
    { title: '行情覆盖率', dataIndex: 'quoteCoverageRate', width: 120, render: formatPercent },
    {
      title: '股票覆盖',
      key: 'holdingCoverage',
      width: 110,
      render: (_, row) => `${row.quotedHoldingCount ?? 0}/${row.holdingCount ?? 0}`
    },
    { title: '行情更新时间', dataIndex: 'quoteUpdatedAt', width: 170 }
  ];

  const featureColumns: ColumnsType<FundFeature> = [
    { title: '截止日期', dataIndex: 'cutoffDate', width: 120 },
    { title: '统计周期', dataIndex: 'periodLabel', width: 100 },
    { title: '标准差', dataIndex: 'standardDeviation', width: 120, render: formatValue },
    { title: '夏普比率', dataIndex: 'sharpeRatio', width: 120, render: formatValue }
  ];

  const ratingColumns: ColumnsType<FundRating> = [
    { title: '评级日期', dataIndex: 'ratingDate', width: 120 },
    { title: '招商评级', dataIndex: 'zhaoshangRating', width: 120, render: renderRatingStars },
    { title: '上海三年', dataIndex: 'shanghaiRating3y', width: 120, render: renderRatingStars },
    { title: '上海五年', dataIndex: 'shanghaiRating5y', width: 120, render: renderRatingStars },
    { title: '济安金信', dataIndex: 'jianRating', width: 120, render: renderRatingStars },
    { title: '晨星评级', dataIndex: 'morningStarRating', width: 120, render: renderRatingStars }
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
                <Descriptions.Item label="购买状态">
                  <Tag color={detail?.fund.canBuy ? 'green' : 'default'}>{detail?.fund.canBuy ? '可购买' : '不可购买'}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="最新净值">{formatValue(detail?.latestNav?.unitNav)}</Descriptions.Item>
                <Descriptions.Item label="净值日期">{detail?.latestNav?.navDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="当日预估涨跌">
                  {renderSignedPercent(detail?.latestValuation?.estimatedChangeRate)}
                </Descriptions.Item>
                <Descriptions.Item label="预估单位净值">{formatValue(detail?.latestValuation?.estimatedUnitNav)}</Descriptions.Item>
                <Descriptions.Item label="估值日期">{detail?.latestValuation?.valuationDate || '-'}</Descriptions.Item>
                <Descriptions.Item label="行情覆盖率">{formatPercent(detail?.latestValuation?.quoteCoverageRate)}</Descriptions.Item>
                <Descriptions.Item label="重仓报告日">
                  {detail?.latestValuation?.holdingReportDate
                    ? formatNavDate(detail.latestValuation.holdingReportDate)
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="持仓截止日">
                  {detail?.latestValuation?.holdingCutoffDate
                    ? formatNavDate(detail.latestValuation.holdingCutoffDate)
                    : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="行情更新时间">{detail?.latestValuation?.quoteUpdatedAt || '-'}</Descriptions.Item>
              </Descriptions>
            )
          },
          {
            key: 'performance',
            label: '业绩表现',
            children: detail?.latestPerformance ? (
              <Descriptions bordered column={3} size="small">
                <Descriptions.Item label="净值日期">{detail.latestPerformance.navDate}</Descriptions.Item>
                <Descriptions.Item label="近一周">{renderSignedPercent(detail.latestPerformance.weeklyReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近一月">{renderSignedPercent(detail.latestPerformance.monthlyReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近三月">{renderSignedPercent(detail.latestPerformance.threeMonthReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近六月">{renderSignedPercent(detail.latestPerformance.sixMonthReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近一年">{renderSignedPercent(detail.latestPerformance.oneYearReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近两年">{renderSignedPercent(detail.latestPerformance.twoYearReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="近三年">{renderSignedPercent(detail.latestPerformance.threeYearReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="今年以来">{renderSignedPercent(detail.latestPerformance.yearToDateReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="成立以来">{renderSignedPercent(detail.latestPerformance.sinceInceptionReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="自定义区间">
                  {detail.latestPerformance.customStartDate} 至 {detail.latestPerformance.customEndDate}
                </Descriptions.Item>
                <Descriptions.Item label="区间收益">{renderSignedPercent(detail.latestPerformance.customReturnRate)}</Descriptions.Item>
                <Descriptions.Item label="原手续费">{renderSignedPercent(detail.latestPerformance.originalFeeRate)}</Descriptions.Item>
                <Descriptions.Item label="折后手续费">{renderSignedPercent(detail.latestPerformance.discountedFeeRate)}</Descriptions.Item>
                <Descriptions.Item label="活期宝手续费">{renderSignedPercent(detail.latestPerformance.cashManagementFeeRate)}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Typography.Text type="secondary">暂无业绩数据</Typography.Text>
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
            key: 'valuations',
            label: '每日估值',
            children: (
              <Table
                rowKey={(record) => `${record.valuationDate}-${record.fundCode}`}
                columns={valuationColumns}
                dataSource={valuations}
                loading={loading}
                scroll={{ x: 1500 }}
                pagination={{
                  total: valuationTotal,
                  current: valuationCurrent,
                  pageSize: valuationPageSize,
                  showSizeChanger: true,
                  pageSizeOptions: PAGE_SIZE_OPTIONS,
                  onChange: loadValuations
                }}
              />
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
                scroll={{ x: 1370 }}
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
            key: 'ratings',
            label: '评级',
            children: (
              <Table
                rowKey={(record) => `${record.ratingDate}-${record.fundCode}`}
                columns={ratingColumns}
                dataSource={ratings}
                loading={loading}
                pagination={false}
                onChange={refreshRatings}
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

function formatDateTime(value?: string | null) {
  if (!value) {
    return '-';
  }
  return value.replace('T', ' ').slice(0, 19);
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

function formatMoney(value?: number | string | null) {
  const numeric = toNumber(value);
  return numeric == null
    ? '-'
    : numeric.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function compareValues(first?: number | string | null, second?: number | string | null) {
  const firstNumber = toNumber(first);
  const secondNumber = toNumber(second);
  if (firstNumber != null && secondNumber != null) return firstNumber - secondNumber;
  return String(first ?? '').localeCompare(String(second ?? ''), 'zh-CN');
}

function performanceListColumns(): ColumnsType<Fund> {
  const fields: Array<[string, keyof FundPerformance]> = [
    ['近一周', 'weeklyReturnRate'], ['近一月', 'monthlyReturnRate'], ['近三月', 'threeMonthReturnRate'],
    ['近六月', 'sixMonthReturnRate'], ['近一年', 'oneYearReturnRate'], ['近两年', 'twoYearReturnRate'],
    ['近三年', 'threeYearReturnRate'], ['今年以来', 'yearToDateReturnRate'], ['成立以来', 'sinceInceptionReturnRate'],
    ['区间收益', 'customReturnRate'], ['原手续费', 'originalFeeRate'], ['折后手续费', 'discountedFeeRate'],
    ['活期宝手续费', 'cashManagementFeeRate']
  ];
  return fields.map(([title, key]) => ({
    title, key: String(key), width: 120,
    render: (_, row) => renderSignedPercent(row.latestPerformance?.[key] as number | undefined),
    sorter: true
  }));
}

function threeYearFeatureValue(features: FundFeature[] | undefined, key: 'standardDeviation' | 'sharpeRatio') {
  const feature = features?.find((item) => item.periodLabel === '近3年');
  return formatValue(feature?.[key]);
}

function formatPercent(value?: number | string | null) {
  return value == null || value === '' ? '-' : `${value}%`;
}

function renderSignedPercent(value?: number | string | null) {
  const numeric = toNumber(value);
  if (numeric == null) {
    return '-';
  }
  const color = numeric > 0 ? '#cf1322' : numeric < 0 ? '#389e0d' : undefined;
  return <span style={{ color }}>{formatPercent(value)}</span>;
}

function renderSignedValue(value?: number | string | null) {
  const numeric = toNumber(value);
  if (numeric == null) {
    return '-';
  }
  const color = numeric > 0 ? '#cf1322' : numeric < 0 ? '#389e0d' : undefined;
  return <span style={{ color }}>{formatMoney(value)}</span>;
}

function renderRatingStars(value?: number | string | null) {
  const rating = toNumber(value);
  if (!rating) {
    return '-';
  }
  return <Tag color="gold">{'★'.repeat(Math.max(0, Math.min(5, Math.round(rating))))}</Tag>;
}

function toNumber(value?: number | string | null) {
  if (value == null || value === '') {
    return null;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function compactNumber(value?: number | string | null) {
  const numeric = toNumber(value);
  if (numeric == null) return '-';
  if (Math.abs(numeric) >= 100000000) return `${(numeric / 100000000).toFixed(2)}亿`;
  if (Math.abs(numeric) >= 10000) return `${(numeric / 10000).toFixed(2)}万`;
  return numeric.toLocaleString();
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
    portfolio: '持仓导入',
    stocks: '股票行情',
    news: '资讯管理',
    users: '用户管理',
    roles: '角色管理',
    menus: '菜单管理'
  };
  return labels[view];
}
