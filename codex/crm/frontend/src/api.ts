export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

const API_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '');

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('crm_token');
  const headers = new Headers(options.headers);
  headers.set('X-Client-Source', 'web');
  const isFormData = typeof FormData !== 'undefined' && options.body instanceof FormData;
  if (!isFormData && options.body !== undefined && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const text = await response.text();
  const body = text ? (JSON.parse(text) as ApiResponse<T>) : undefined;
  if (!response.ok || !body || body.code !== 0) {
    throw new Error(body?.message || `请求失败 (${response.status})`);
  }
  return body.data;
}

export type LoginResult = {
  token: string;
  username: string;
  permissions: string[];
};

export type Customer = {
  id?: number;
  customerName: string;
  customerType?: string;
  industry?: string;
  source?: string;
  level?: string;
  status?: string;
  ownerUserId?: number;
  phone?: string;
  email?: string;
  province?: string;
  city?: string;
  address?: string;
  remark?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type Fund = {
  id?: number;
  fundCode: string;
  fundName: string;
  inceptionDate?: string;
  fundManager?: string;
  fundType?: string;
  managementCompany?: string;
  netAssetScale?: string;
  scaleDate?: string;
  canBuy?: boolean;
  createdAt?: string;
  updatedAt?: string;
  latestPerformance?: FundPerformance;
  latestRating?: FundRating;
  features?: FundFeature[];
  latestValuation?: FundDailyValuation;
  latestScore?: FundScoreSummary;
  favorite?: boolean;
};

export type FundScoreSummary = {
  profileId: number;
  profileName: string;
  profileVersion: number;
  validationStatus: 'UNVERIFIED' | 'PASSED' | 'FAILED';
  asOfDate: string;
  totalScore?: number;
  profitProbability?: number;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW' | 'INSUFFICIENT';
  dataCoverage: number;
  comparisonGroup?: string;
  categoryRank?: number;
  categoryCount?: number;
  methodologyVersion: string;
};

export type FundScoreComponent = {
  factorKey: string;
  label: string;
  rawValue?: number;
  normalizedScore?: number;
  weight: number;
  effectiveWeight?: number;
  contribution?: number;
};

export type FundScoreDetail = {
  summary: FundScoreSummary;
  components: FundScoreComponent[];
  disclaimer: string;
};

export type FundScoreProfile = {
  id: number;
  profileName: string;
  versionNo: number;
  status: string;
  sourceType: string;
  targetMonths: number;
  weights: Record<string, number>;
  validationStatus: string;
  active: boolean;
  createdBy?: string;
  approvedBy?: string;
  approvedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type FundScoreBacktest = {
  id: number;
  profileId: number;
  trainStartDate?: string;
  trainEndDate?: string;
  testStartDate?: string;
  testEndDate?: string;
  sampleCount: number;
  foldCount: number;
  auc?: number;
  brierScore?: number;
  baselineBrierScore?: number;
  top20WinRate?: number;
  baselineWinRate?: number;
  winRateLift?: number;
  passed: boolean;
  limitationsJson?: string;
  metricsJson?: string;
  createdAt: string;
};

export type FundScoreJob = {
  id: number;
  jobType: string;
  profileId?: number;
  status: string;
  requestedBy?: string;
  message?: string;
  startedAt?: string;
  finishedAt?: string;
  createdAt: string;
  updatedAt: string;
};

export type FundNav = {
  id?: number;
  fundCode: string;
  navDate: string;
  unitNav?: number;
  accumulatedNav?: number;
  dailyGrowthRate?: number;
};

export type FundHolding = {
  id?: number;
  fundCode: string;
  reportPeriod?: string;
  reportDate: string;
  cutoffDate: string;
  rankNo?: number;
  stockCode: string;
  stockName?: string;
  latestPrice?: number;
  changeRate?: number;
  quoteTime?: string;
  netValueRatio?: number;
  holdingShares10k?: number;
  holdingMarketValue10k?: number;
};

export type FundFeature = {
  id?: number;
  fundCode: string;
  periodLabel: string;
  cutoffDate: string;
  standardDeviation?: number;
  sharpeRatio?: number;
};

export type FundRating = {
  id?: number;
  fundCode: string;
  ratingDate: string;
  zhaoshangRating?: number;
  shanghaiRating3y?: number;
  shanghaiRating5y?: number;
  jianRating?: number;
  morningStarRating?: number;
};

export type FundPerformance = {
  fundCode: string;
  navDate: string;
  fundNamePinyin?: string;
  inceptionDate?: string;
  weeklyReturnRate?: number;
  monthlyReturnRate?: number;
  threeMonthReturnRate?: number;
  sixMonthReturnRate?: number;
  oneYearReturnRate?: number;
  twoYearReturnRate?: number;
  threeYearReturnRate?: number;
  yearToDateReturnRate?: number;
  sinceInceptionReturnRate?: number;
  customStartDate: string;
  customEndDate: string;
  customReturnRate?: number;
  originalFeeRate?: number;
  discountedFeeRate?: number;
  discountFactor?: number;
  cashManagementFeeRate?: number;
};

export type FundDailyValuation = {
  fundCode: string;
  valuationDate: string;
  holdingReportDate?: string;
  holdingCutoffDate?: string;
  baseNavDate?: string;
  baseUnitNav?: number;
  estimatedUnitNav?: number;
  estimatedChangeRate?: number;
  holdingWeight?: number;
  quotedHoldingWeight?: number;
  quoteCoverageRate?: number;
  holdingCount?: number;
  quotedHoldingCount?: number;
  quoteUpdatedAt?: string;
};

export type FundDetail = {
  fund: Fund;
  latestNav?: FundNav;
  latestPerformance?: FundPerformance;
  latestValuation?: FundDailyValuation;
  latestHoldings: FundHolding[];
  features: FundFeature[];
  ratings: FundRating[];
  scoreDetail?: FundScoreDetail;
};

export type FinanceNews = { id: number; newsId: string; categoryTag: number; categoryName: string; content: string; createTime: string; sourceUpdateTime?: string; docUrl?: string; tagsJson?: string; imagesJson?: string };

export type PortfolioHoldingCandidate = {
  fundCode: string;
  fundName: string;
  score?: number;
};

export type PortfolioHoldingImportRow = {
  rowNo: number;
  fundCode?: string;
  fundName: string;
  holdingAmount?: number;
  holdingProfit?: number;
  holdingReturnRate?: number;
  holdingCost?: number;
  yesterdayProfit?: number;
  todayProfit?: number;
  holdingShares?: number;
  costNav?: number;
  screenshotDate?: string;
  confidence?: number;
  rawTexts: string[];
  candidates: PortfolioHoldingCandidate[];
};

export type PortfolioTradeAdjustment = {
  groupKey: string;
  fundCode?: string;
  fundName: string;
  buyAmount: number;
  sellAmount: number;
  netAmount: number;
  currentHoldingAmount?: number;
  projectedHoldingAmount?: number;
  transactionCount: number;
  skippedCount: number;
  applicable: boolean;
  warnings: string[];
  candidates: PortfolioHoldingCandidate[];
};

export type PortfolioHoldingImportPreview = {
  importId: number;
  sourceLabel: string;
  importType: 'holding' | 'trade';
  status: string;
  screenshotDate?: string;
  imageCount: number;
  imageHashes: string[];
  warnings: string[];
  rows: PortfolioHoldingImportRow[];
  tradeAdjustments: PortfolioTradeAdjustment[];
};

export type PortfolioHoldingBatch = {
  id: number;
  status: string;
  sourceLabel: string;
  importType: 'holding' | 'trade';
  screenshotDate?: string;
  imageCount: number;
  itemCount: number;
  transactionCount: number;
  appliedCount: number;
  skippedCount: number;
  confirmedAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export type PortfolioHoldingConfirmResponse = {
  affectedHoldingCount: number;
  appliedTransactionCount: number;
  skippedTransactionCount: number;
  warnings: string[];
};

export type UserFundHolding = {
  id: number;
  ownerUsername: string;
  fundCode: string;
  fundName: string;
  holdingAmount?: number;
  holdingProfit?: number;
  holdingReturnRate?: number;
  holdingCost?: number;
  yesterdayProfit?: number;
  todayProfit?: number;
  holdingShares?: number;
  costNav?: number;
  valuationDate?: string;
  holdingReportDate?: string;
  holdingCutoffDate?: string;
  estimatedChangeRate?: number;
  estimatedDailyProfit?: number;
  estimatedHoldingAmount?: number;
  estimatedUnitNav?: number;
  estimatedCumulativeChangeRate?: number;
  estimatedCumulativeProfit?: number;
  valuationCoverageRate?: number;
  valuationUpdatedAt?: string;
  screenshotDate?: string;
  latestImportId?: number;
  latestImportAt?: string;
  createdAt?: string;
  updatedAt?: string;
};

export function listFinanceNews(params: { current: number; size: number; keyword?: string; categoryTag?: number }) {
  const search = new URLSearchParams({ current: String(params.current), size: String(params.size) });
  if (params.keyword) search.set('keyword', params.keyword);
  if (params.categoryTag !== undefined) search.set('categoryTag', String(params.categoryTag));
  return request<PageResult<FinanceNews>>(`/news?${search.toString()}`);
}

export function deleteFinanceNews(id: number) { return request<void>(`/news/${id}`, { method: 'DELETE' }); }

export function previewPortfolioHoldings(
  files: File[],
  sourceLabel: 'alipay' | 'tencent' = 'alipay',
  importType: 'holding' | 'trade' = 'holding'
) {
  const formData = new FormData();
  files.forEach((file) => formData.append('images', file));
  const search = new URLSearchParams({ sourceLabel, importType });
  return request<PortfolioHoldingImportPreview>(`/portfolio/imports/ocr?${search.toString()}`, {
    method: 'POST',
    body: formData
  });
}

export function confirmPortfolioHoldingImport(
  importId: number,
  body: {
    screenshotDate?: string;
    items?: Array<Partial<PortfolioHoldingImportRow> & { rowNo: number; fundCode?: string; fundName: string }>;
    tradeMappings?: Array<{ groupKey: string; fundCode: string }>;
  }
) {
  return request<PortfolioHoldingConfirmResponse>(`/portfolio/imports/${importId}/confirm`, {
    method: 'POST',
    body: JSON.stringify(body)
  });
}

export function listPortfolioHoldings(params: { current: number; size: number; keyword?: string }) {
  const search = new URLSearchParams({ current: String(params.current), size: String(params.size) });
  if (params.keyword) search.set('keyword', params.keyword);
  return request<PageResult<UserFundHolding>>(`/portfolio/holdings?${search.toString()}`);
}

export function listPortfolioImports(params: { current: number; size: number }) {
  const search = new URLSearchParams({ current: String(params.current), size: String(params.size) });
  return request<PageResult<PortfolioHoldingBatch>>(`/portfolio/imports?${search.toString()}`);
}

export function getPortfolioImport(importId: number) {
  return request<PortfolioHoldingImportPreview>(`/portfolio/imports/${importId}`);
}

export type StockQuote = {
  id?: number; stockCode: string; stockName?: string; marketCode?: number; exchangeName?: string;
  listingDate?: string; tradeDate?: string; quoteTime?: string; updatedAt?: string; comment?: string; latestPrice?: number; changeRate?: number;
  changeAmount?: number; volume?: number; amount?: number; amplitude?: number; turnoverRate?: number;
  peDynamic?: number; peTtm?: number; volumeRatio?: number; fiveMinChangeRate?: number;
  highPrice?: number; lowPrice?: number; openPrice?: number; previousClose?: number;
  totalMarketCap?: number; floatMarketCap?: number; speedRate?: number; pbRatio?: number;
  changeRate60d?: number; changeRateYtd?: number; mainNetInflow?: number;
};

export function listStocks(params: { current: number; size: number; keyword?: string; marketCode?: number; sortField?: string; sortOrder?: string }) {
  const search = new URLSearchParams({ current: String(params.current), size: String(params.size) });
  if (params.keyword) search.set('keyword', params.keyword);
  if (params.marketCode !== undefined) search.set('marketCode', String(params.marketCode));
  if (params.sortField) search.set('sortField', params.sortField);
  if (params.sortOrder) search.set('sortOrder', params.sortOrder);
  return request<PageResult<StockQuote>>(`/stocks?${search}`);
}

export function getStock(stockCode: string) { return request<StockQuote>(`/stocks/${stockCode}`); }
export function listStockHistory(stockCode: string, current = 1, size = 50) {
  return request<PageResult<StockQuote>>(`/stocks/${stockCode}/history?current=${current}&size=${size}`);
}

export type Role = {
  id?: number;
  roleName: string;
  roleCode: string;
  dataScope?: string;
  status?: number;
  menuIds?: number[];
  createdAt?: string;
  updatedAt?: string;
};

export type User = {
  id?: number;
  deptId?: number;
  username: string;
  password?: string;
  realName: string;
  mobile?: string;
  email?: string;
  status?: number;
  roleIds?: number[];
  roleNames?: string[];
  createdAt?: string;
  updatedAt?: string;
};

export type SysMenu = {
  id?: number;
  parentId?: number;
  menuName: string;
  menuType: 'CATALOG' | 'MENU' | 'BUTTON';
  path?: string;
  component?: string;
  permissionCode?: string;
  icon?: string;
  sortOrder?: number;
  visible?: number;
  createdAt?: string;
  updatedAt?: string;
  children?: SysMenu[];
};

export type PageResult<T> = {
  records: T[];
  total: number;
  size: number;
  current: number;
};

export function login(username: string, password: string) {
  return request<LoginResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  });
}

export function listCustomers(params: { current: number; size: number; keyword?: string }) {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  if (params.keyword) {
    search.set('keyword', params.keyword);
  }
  return request<PageResult<Customer>>(`/customers?${search.toString()}`);
}

export function saveCustomer(customer: Customer) {
  if (customer.id) {
    return request<void>(`/customers/${customer.id}`, { method: 'PUT', body: JSON.stringify(customer) });
  }
  return request<void>('/customers', { method: 'POST', body: JSON.stringify(customer) });
}

export function deleteCustomer(id: number) {
  return request<void>(`/customers/${id}`, { method: 'DELETE' });
}

export function listFunds(params: {
  current: number;
  size: number;
  keyword?: string;
  fundType?: string;
  favoritesOnly?: boolean;
  sortField?: string;
  sortOrder?: string;
}) {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  if (params.keyword) {
    search.set('keyword', params.keyword);
  }
  if (params.fundType) {
    search.set('fundType', params.fundType);
  }
  if (params.sortField) search.set('sortField', params.sortField);
  if (params.sortOrder) search.set('sortOrder', params.sortOrder);
  const path = params.favoritesOnly ? '/funds/favorites' : '/funds';
  return request<PageResult<Fund>>(`${path}?${search.toString()}`);
}

export function addFundFavorite(fundCode: string) {
  return request<void>(`/funds/${encodeURIComponent(fundCode)}/favorite`, { method: 'POST' });
}

export function removeFundFavorite(fundCode: string) {
  return request<void>(`/funds/${encodeURIComponent(fundCode)}/favorite`, { method: 'DELETE' });
}

export function getFundDetail(fundCode: string) {
  return request<FundDetail>(`/funds/${encodeURIComponent(fundCode)}`);
}

export function listFundNavs(fundCode: string, params: { current: number; size: number }) {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  return request<PageResult<FundNav>>(`/funds/${encodeURIComponent(fundCode)}/navs?${search.toString()}`);
}

export function listFundHoldings(fundCode: string, params: { current: number; size: number; reportDate?: string }) {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  if (params.reportDate) {
    search.set('reportDate', params.reportDate);
  }
  return request<PageResult<FundHolding>>(`/funds/${encodeURIComponent(fundCode)}/holdings?${search.toString()}`);
}

export function listFundValuations(fundCode: string, params: { current: number; size: number }) {
  const search = new URLSearchParams();
  search.set('current', String(params.current));
  search.set('size', String(params.size));
  return request<PageResult<FundDailyValuation>>(
    `/funds/${encodeURIComponent(fundCode)}/valuations?${search.toString()}`
  );
}

export function listFundFeatures(fundCode: string) {
  return request<FundFeature[]>(`/funds/${encodeURIComponent(fundCode)}/features`);
}

export function listFundRatings(fundCode: string) {
  return request<FundRating[]>(`/funds/${encodeURIComponent(fundCode)}/ratings`);
}

export function saveFund(fund: Fund) {
  if (fund.id) {
    return request<void>(`/funds/${encodeURIComponent(fund.fundCode)}`, { method: 'PUT', body: JSON.stringify(fund) });
  }
  return request<void>('/funds', { method: 'POST', body: JSON.stringify(fund) });
}

export function deleteFund(fundCode: string) {
  return request<void>(`/funds/${encodeURIComponent(fundCode)}`, { method: 'DELETE' });
}

export function listFundScoreProfiles() {
  return request<FundScoreProfile[]>('/funds/scoring/profiles');
}

export function saveFundScoreProfile(profile: { id?: number; profileName: string; weights: Record<string, number> }) {
  const path = profile.id ? `/funds/scoring/profiles/${profile.id}` : '/funds/scoring/profiles';
  return request<FundScoreProfile>(path, {
    method: profile.id ? 'PUT' : 'POST',
    body: JSON.stringify({ profileName: profile.profileName, weights: profile.weights })
  });
}

export function backtestFundScoreProfile(id: number) {
  return request<FundScoreJob>(`/funds/scoring/profiles/${id}/backtest`, { method: 'POST' });
}

export function activateFundScoreProfile(id: number) {
  return request<void>(`/funds/scoring/profiles/${id}/activate`, { method: 'POST' });
}

export function recommendFundScoreProfile() {
  return request<FundScoreJob>('/funds/scoring/recommend', { method: 'POST' });
}

export function getFundScoreBacktest(id: number) {
  return request<FundScoreBacktest | undefined>(`/funds/scoring/profiles/${id}/backtest`);
}

export function listFundScoreJobs() {
  return request<FundScoreJob[]>('/funds/scoring/jobs');
}

export function listUsers(keyword?: string) {
  const search = new URLSearchParams();
  if (keyword) {
    search.set('keyword', keyword);
  }
  return request<User[]>(`/users${search.toString() ? `?${search.toString()}` : ''}`);
}

export function saveUser(user: User) {
  if (user.id) {
    return request<void>(`/users/${user.id}`, { method: 'PUT', body: JSON.stringify(user) });
  }
  return request<void>('/users', { method: 'POST', body: JSON.stringify(user) });
}

export function deleteUser(id: number) {
  return request<void>(`/users/${id}`, { method: 'DELETE' });
}

export function listRoles() {
  return request<Role[]>('/roles');
}

export function saveRole(role: Role) {
  if (role.id) {
    return request<void>(`/roles/${role.id}`, { method: 'PUT', body: JSON.stringify(role) });
  }
  return request<void>('/roles', { method: 'POST', body: JSON.stringify(role) });
}

export function deleteRole(id: number) {
  return request<void>(`/roles/${id}`, { method: 'DELETE' });
}

export function listMenus() {
  return request<SysMenu[]>('/menus');
}

export function saveMenu(menu: SysMenu) {
  if (menu.id) {
    return request<void>(`/menus/${menu.id}`, { method: 'PUT', body: JSON.stringify(menu) });
  }
  return request<void>('/menus', { method: 'POST', body: JSON.stringify(menu) });
}

export function deleteMenu(id: number) {
  return request<void>(`/menus/${id}`, { method: 'DELETE' });
}
