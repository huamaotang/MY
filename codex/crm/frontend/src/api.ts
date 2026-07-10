export type ApiResponse<T> = {
  code: number;
  message: string;
  data: T;
};

const API_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '');

export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('crm_token');
  const headers = new Headers(options.headers);
  headers.set('Content-Type', 'application/json');
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
