SET NAMES utf8mb4;

INSERT IGNORE INTO sys_menu (
  id, parent_id, menu_name, menu_type, path, component,
  permission_code, icon, sort_order, visible
) VALUES (
  25, 21, '基金评分配置', 'BUTTON', NULL, NULL,
  'fund:score-config', NULL, 25, 1
);

INSERT IGNORE INTO sys_role_menu (role_id, menu_id)
SELECT 1, id FROM sys_menu WHERE id = 25;
