export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

const API_BASE = '/api';

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('crm_token');
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
  const body = (await response.json()) as ApiResponse<T>;
  if (!response.ok || body.code !== 0) {
    throw new Error(body.message || '请求失败');
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
