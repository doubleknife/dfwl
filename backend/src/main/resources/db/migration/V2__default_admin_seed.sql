-- 系统权限、ADMIN 角色及默认管理员初始化。默认管理员首次登录后必须修改密码。
INSERT IGNORE INTO sys_role (role_code, role_name, is_system_fixed)
VALUES
  ('DRIVER', '司机', 1),
  ('ADMIN', '管理员', 1);

INSERT IGNORE INTO sys_permission (permission_code, permission_name, permission_type)
VALUES
  ('vehicle:list', '车辆列表', 'API'),
  ('vehicle:add', '车辆新增', 'API'),
  ('vehicle:view', '车辆查看', 'API'),
  ('vehicle:edit', '车辆编辑', 'API'),
  ('vehicle:delete', '车辆删除', 'API'),
  ('vehicle:bindDriver', '车辆绑定司机', 'API'),
  ('vehicle:unbindDriver', '车辆解绑司机', 'API'),
  ('vehicle:bindingHistory', '车辆绑定历史', 'API'),
  ('trailer:list', '车挂列表', 'API'),
  ('trailer:add', '车挂新增', 'API'),
  ('trailer:edit', '车挂编辑', 'API'),
  ('trailer:bindVehicle', '车挂绑定车辆', 'API'),
  ('trailer:unbindVehicle', '车挂解绑车辆', 'API'),
  ('trailer:bindingHistory', '车挂绑定历史', 'API'),
  ('driver:list', '司机列表', 'API'),
  ('driver:add', '司机新增', 'API'),
  ('driver:edit', '司机编辑', 'API'),
  ('driver:bindVehicle', '司机绑定车辆', 'API'),
  ('driver:unbindVehicle', '司机解绑车辆', 'API'),
  ('driver:resetPassword', '司机重置密码', 'API'),
  ('route:list', '线路列表', 'API'),
  ('route:create', '线路创建', 'API'),
  ('route:edit', '线路编辑', 'API'),
  ('route:publish', '线路发布', 'API'),
  ('route:cancel', '线路取消', 'API'),
  ('route:delete', '线路删除', 'API'),
  ('route:depart', '线路发车', 'API'),
  ('route:unload', '线路卸货', 'API'),
  ('route:void', '线路作废', 'API'),
  ('route:reactivate', '线路重新启用', 'API'),
  ('route:weight:adjust', '线路重量调整', 'API'),
  ('expense:list', '费用列表', 'API'),
  ('expense:add', '费用新增', 'API'),
  ('expense:edit', '费用编辑', 'API'),
  ('expense:delete', '费用删除', 'API'),
  ('expense:attribution:edit', '费用归属调整', 'API'),
  ('expense:reversal', '费用冲销', 'API'),
  ('expense:approval:view', '费用审批链查看', 'API'),
  ('approval:create', '审批发起', 'API'),
  ('approval:process', '审批处理', 'API'),
  ('approval:return', '审批打回', 'API'),
  ('approval:history:view', '审批历史查看', 'API'),
  ('approval:flow:manage', '审批流程管理', 'API'),
  ('tire:list', '轮胎列表', 'API'),
  ('tire:import', '轮胎导入', 'API'),
  ('tire:request', '换胎申请', 'API'),
  ('tire:approval:view', '轮胎审批查看', 'API'),
  ('import:preview', '导入预览', 'API'),
  ('import:commit', '导入提交', 'API'),
  ('import:history', '导入历史', 'API'),
  ('import:failure:export', '失败明细导出', 'API'),
  ('salary:view', '工资查看', 'API'),
  ('salary:manage', '工资管理', 'API'),
  ('salary:mine', '本人薪资查看', 'API'),
  ('report:dashboard', '经营看板', 'API'),
  ('report:profit', '利润报表', 'API'),
  ('report:vehicle', '车辆支出报表', 'API'),
  ('report:driver', '司机支出报表', 'API'),
  ('report:attendance', '出勤报表', 'API'),
  ('report:energy', '能源报表', 'API'),
  ('settlement:view', '结算查看', 'API'),
  ('settlement:generate', '结算生成', 'API'),
  ('settlement:diff:view', '结算差异查看', 'API'),
  ('user:manage', '用户管理', 'API'),
  ('role:manage', '角色管理', 'API'),
  ('permission:manage', '权限管理', 'API'),
  ('audit:view', '审计查看', 'API');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN';

-- 默认管理员账号：13900000001。初始密码见部署说明，首次登录后请立即修改密码。
-- password_hash 由项目当前 BCryptPasswordEncoder 生成，数据库不保存明文密码。
INSERT IGNORE INTO sys_user (phone, password_hash, role_id, status)
SELECT '13900000001', '$2a$10$f1bYNYeJ1dGlLjWNF0MWRuVxizwhkJCootIqF736TFBvuJzcywZ6q', r.id, 1
FROM sys_role r
WHERE r.role_code = 'ADMIN';
