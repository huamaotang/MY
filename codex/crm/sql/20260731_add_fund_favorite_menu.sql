SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
  id, parent_id, menu_name, menu_type, path, component,
  permission_code, icon, sort_order, visible
) VALUES (
  26, 20, '自选列表', 'MENU', '/products/fund-favorites', 'FundFavoriteList',
  'fund:list', 'StarOutlined', 22, 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 26;
