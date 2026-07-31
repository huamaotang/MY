SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS sys_role_menu;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_api_log;
DROP TABLE IF EXISTS sys_login_log;
DROP TABLE IF EXISTS sys_dict_data;
DROP TABLE IF EXISTS sys_dict_type;
DROP TABLE IF EXISTS crm_payment;
DROP TABLE IF EXISTS crm_contract;
DROP TABLE IF EXISTS crm_opportunity;
DROP TABLE IF EXISTS crm_follow_record;
DROP TABLE IF EXISTS crm_contact;
DROP TABLE IF EXISTS crm_customer;
DROP TABLE IF EXISTS sys_menu;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_dept;

CREATE TABLE sys_dept (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  dept_name VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='部门';

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dept_id BIGINT NULL,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(120) NOT NULL,
  real_name VARCHAR(80) NOT NULL,
  mobile VARCHAR(30) NULL,
  email VARCHAR(120) NULL,
  status TINYINT NOT NULL DEFAULT 1,
  last_login_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_user_username (username),
  KEY idx_sys_user_dept (dept_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE sys_role (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  role_name VARCHAR(80) NOT NULL,
  role_code VARCHAR(80) NOT NULL,
  data_scope VARCHAR(30) NOT NULL DEFAULT 'ALL',
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_sys_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色';

CREATE TABLE sys_menu (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  menu_name VARCHAR(80) NOT NULL,
  menu_type VARCHAR(20) NOT NULL COMMENT 'CATALOG/MENU/BUTTON',
  path VARCHAR(160) NULL,
  component VARCHAR(160) NULL,
  permission_code VARCHAR(120) NULL,
  icon VARCHAR(80) NULL,
  sort_order INT NOT NULL DEFAULT 0,
  visible TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_sys_menu_parent (parent_id),
  KEY idx_sys_menu_permission (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限';

CREATE TABLE sys_user_role (
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色';

CREATE TABLE sys_role_menu (
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  PRIMARY KEY (role_id, menu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单';

CREATE TABLE sys_login_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  username VARCHAR(50) NOT NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(300) NULL,
  success TINYINT NOT NULL,
  message VARCHAR(200) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='登录日志';

CREATE TABLE sys_api_log (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  trace_id VARCHAR(64) NOT NULL,
  service_name VARCHAR(80) NULL,
  request_method VARCHAR(10) NOT NULL,
  request_uri VARCHAR(300) NOT NULL,
  query_string VARCHAR(1000) NULL,
  source VARCHAR(40) NOT NULL DEFAULT 'unknown' COMMENT 'web/ios/android 等调用来源',
  user_id BIGINT NULL,
  username VARCHAR(50) NULL,
  ip VARCHAR(64) NULL,
  user_agent VARCHAR(500) NULL,
  http_status INT NOT NULL,
  success TINYINT NOT NULL,
  error_message VARCHAR(500) NULL,
  duration_ms BIGINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_sys_api_log_created (created_at),
  KEY idx_sys_api_log_user (username),
  KEY idx_sys_api_log_source (source),
  KEY idx_sys_api_log_uri (request_uri)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口访问日志';

CREATE TABLE sys_dict_type (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_name VARCHAR(80) NOT NULL,
  dict_code VARCHAR(80) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  UNIQUE KEY uk_sys_dict_type_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典类型';

CREATE TABLE sys_dict_data (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  dict_code VARCHAR(80) NOT NULL,
  label VARCHAR(80) NOT NULL,
  value VARCHAR(80) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  KEY idx_sys_dict_data_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='字典数据';

CREATE TABLE crm_customer (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_name VARCHAR(160) NOT NULL,
  customer_type VARCHAR(40) NULL,
  industry VARCHAR(80) NULL,
  source VARCHAR(80) NULL,
  level VARCHAR(40) NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'POTENTIAL',
  owner_user_id BIGINT NULL,
  phone VARCHAR(40) NULL,
  email VARCHAR(120) NULL,
  province VARCHAR(80) NULL,
  city VARCHAR(80) NULL,
  address VARCHAR(240) NULL,
  remark VARCHAR(500) NULL,
  created_by BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_crm_customer_name (customer_name),
  KEY idx_crm_customer_owner (owner_user_id),
  KEY idx_crm_customer_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客户';

CREATE TABLE crm_contact (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  contact_name VARCHAR(80) NOT NULL,
  gender VARCHAR(20) NULL,
  title VARCHAR(80) NULL,
  mobile VARCHAR(40) NULL,
  email VARCHAR(120) NULL,
  wechat VARCHAR(80) NULL,
  is_primary TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_crm_contact_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联系人';

CREATE TABLE crm_follow_record (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  contact_id BIGINT NULL,
  follow_type VARCHAR(40) NOT NULL,
  content VARCHAR(1000) NOT NULL,
  next_follow_at DATETIME NULL,
  owner_user_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_crm_follow_customer (customer_id),
  KEY idx_crm_follow_next (next_follow_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进记录';

CREATE TABLE crm_opportunity (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  opportunity_name VARCHAR(160) NOT NULL,
  stage VARCHAR(40) NOT NULL DEFAULT 'NEW',
  amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  expected_close_date DATE NULL,
  owner_user_id BIGINT NULL,
  remark VARCHAR(500) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_crm_opp_customer (customer_id),
  KEY idx_crm_opp_owner (owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商机';

CREATE TABLE crm_contract (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  contract_no VARCHAR(80) NOT NULL,
  contract_name VARCHAR(160) NOT NULL,
  amount DECIMAL(12,2) NOT NULL DEFAULT 0,
  sign_date DATE NULL,
  start_date DATE NULL,
  end_date DATE NULL,
  status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
  owner_user_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_crm_contract_no (contract_no),
  KEY idx_crm_contract_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='合同';

CREATE TABLE crm_payment (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  contract_id BIGINT NOT NULL,
  customer_id BIGINT NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  paid_at DATE NOT NULL,
  pay_method VARCHAR(40) NULL,
  remark VARCHAR(300) NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_crm_payment_contract (contract_id),
  KEY idx_crm_payment_customer (customer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='回款';

INSERT INTO sys_dept (id, parent_id, dept_name, sort_order) VALUES
(1, 0, '总公司', 1),
(2, 1, '销售部', 10);

INSERT INTO sys_user (id, dept_id, username, password, real_name, mobile, email, status) VALUES
(1, 1, 'admin', '{noop}admin123', '系统管理员', '13800000000', 'admin@example.com', 1);

INSERT INTO sys_role (id, role_name, role_code, data_scope, status) VALUES
(1, '超级管理员', 'ADMIN', 'ALL', 1),
(2, '销售', 'SALES', 'SELF', 1);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission_code, icon, sort_order, visible) VALUES
(1, 0, '工作台', 'MENU', '/dashboard', 'Dashboard', 'dashboard:view', 'DashboardOutlined', 1, 1),
(10, 0, '客户管理', 'CATALOG', '/crm', NULL, 'crm:view', 'TeamOutlined', 10, 1),
(11, 10, '客户列表', 'MENU', '/crm/customers', 'CustomerList', 'crm:customer:list', 'UserOutlined', 11, 1),
(12, 11, '新增客户', 'BUTTON', NULL, NULL, 'crm:customer:create', NULL, 12, 1),
(13, 11, '编辑客户', 'BUTTON', NULL, NULL, 'crm:customer:update', NULL, 13, 1),
(14, 11, '删除客户', 'BUTTON', NULL, NULL, 'crm:customer:delete', NULL, 14, 1),
(15, 10, '联系人', 'MENU', '/crm/contacts', 'ContactList', 'crm:contact:list', 'ContactsOutlined', 15, 1),
(16, 10, '跟进记录', 'MENU', '/crm/follows', 'FollowList', 'crm:follow:list', 'CommentOutlined', 16, 1),
(20, 0, '产品管理', 'CATALOG', '/products', NULL, 'fund:view', 'FundOutlined', 20, 1),
(21, 20, '基金管理', 'MENU', '/products/funds', 'FundList', 'fund:list', 'FundOutlined', 21, 1),
(22, 21, '新增基金', 'BUTTON', NULL, NULL, 'fund:create', NULL, 22, 1),
(23, 21, '编辑基金', 'BUTTON', NULL, NULL, 'fund:update', NULL, 23, 1),
(24, 21, '删除基金', 'BUTTON', NULL, NULL, 'fund:delete', NULL, 24, 1),
(25, 21, '基金评分配置', 'BUTTON', NULL, NULL, 'fund:score-config', NULL, 25, 1),
(26, 20, '自选列表', 'MENU', '/products/fund-favorites', 'FundFavoriteList', 'fund:list', 'StarOutlined', 22, 1),
(30, 0, '系统管理', 'CATALOG', '/system', NULL, 'system:view', 'SettingOutlined', 30, 1),
(31, 30, '用户管理', 'MENU', '/system/users', 'UserAdmin', 'system:user:list', 'UserSwitchOutlined', 31, 1),
(32, 30, '角色管理', 'MENU', '/system/roles', 'RoleAdmin', 'system:role:list', 'SafetyCertificateOutlined', 32, 1),
(33, 30, '菜单管理', 'MENU', '/system/menus', 'MenuAdmin', 'MenuOutlined', 'MenuOutlined', 33, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1);

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu;

INSERT INTO sys_dict_type (id, dict_name, dict_code, status) VALUES
(1, '客户状态', 'customer_status', 1),
(2, '客户级别', 'customer_level', 1),
(3, '跟进方式', 'follow_type', 1);

INSERT INTO sys_dict_data (dict_code, label, value, sort_order, status) VALUES
('customer_status', '潜在客户', 'POTENTIAL', 1, 1),
('customer_status', '成交客户', 'DEAL', 2, 1),
('customer_status', '流失客户', 'LOST', 3, 1),
('customer_level', 'A 级', 'A', 1, 1),
('customer_level', 'B 级', 'B', 2, 1),
('customer_level', 'C 级', 'C', 3, 1),
('follow_type', '电话', 'PHONE', 1, 1),
('follow_type', '微信', 'WECHAT', 2, 1),
('follow_type', '拜访', 'VISIT', 3, 1);

INSERT INTO crm_customer (id, customer_name, customer_type, industry, source, level, status, owner_user_id, phone, province, city, address, remark, created_by) VALUES
(1, '示例科技有限公司', '企业客户', '软件服务', '官网咨询', 'A', 'POTENTIAL', 1, '021-88888888', '上海市', '上海市', '浦东新区', '初始化示例客户', 1);

INSERT INTO crm_contact (customer_id, contact_name, gender, title, mobile, email, is_primary, remark) VALUES
(1, '张三', 'MALE', '采购经理', '13900000000', 'zhangsan@example.com', 1, '主要联系人');

INSERT INTO crm_follow_record (customer_id, contact_id, follow_type, content, next_follow_at, owner_user_id) VALUES
(1, 1, 'PHONE', '首次电话沟通，客户关注私有化部署能力。', DATE_ADD(NOW(), INTERVAL 3 DAY), 1);

SET FOREIGN_KEY_CHECKS = 1;
