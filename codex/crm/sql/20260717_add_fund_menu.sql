SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, permission_code, icon, sort_order, visible) VALUES
(20, 0, '产品管理', 'CATALOG', '/products', NULL, 'fund:view', 'FundOutlined', 20, 1),
(21, 20, '基金管理', 'MENU', '/products/funds', 'FundList', 'fund:list', 'FundOutlined', 21, 1),
(22, 21, '新增基金', 'BUTTON', NULL, NULL, 'fund:create', NULL, 22, 1),
(23, 21, '编辑基金', 'BUTTON', NULL, NULL, 'fund:update', NULL, 23, 1),
(24, 21, '删除基金', 'BUTTON', NULL, NULL, 'fund:delete', NULL, 24, 1);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id IN (20, 21, 22, 23, 24);
