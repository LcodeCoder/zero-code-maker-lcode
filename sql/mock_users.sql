-- 10 条用户模拟数据
-- 密码统一为 12345678，哈希 = md5('12345678' + 'ailcode') = 043d44617cde91a90f77a5f097ac1182
-- 账号规则 mockuser01 ~ mockuser10，其中 mockuser01 为管理员

USE ai_lcode_maker;

INSERT INTO user
    (userAccount, userPassword, userEmail, userName, userAvatar, userProfile, userRole, createTime, updateTime, editTime)
VALUES
    ('mockuser01', '043d44617cde91a90f77a5f097ac1182', 'mock01@lcode.dev',  '林一',   'https://api.dicebear.com/7.x/avataaars/svg?seed=01', '平台管理员',         'admin', NOW(), NOW(), NOW()),
    ('mockuser02', '043d44617cde91a90f77a5f097ac1182', 'mock02@lcode.dev',  '陈二',   'https://api.dicebear.com/7.x/avataaars/svg?seed=02', '全栈工程师',         'user',  NOW(), NOW(), NOW()),
    ('mockuser03', '043d44617cde91a90f77a5f097ac1182', 'mock03@lcode.dev',  '王三',   'https://api.dicebear.com/7.x/avataaars/svg?seed=03', '前端工程师',         'user',  NOW(), NOW(), NOW()),
    ('mockuser04', '043d44617cde91a90f77a5f097ac1182', 'mock04@lcode.dev',  '李四',   'https://api.dicebear.com/7.x/avataaars/svg?seed=04', '后端工程师',         'user',  NOW(), NOW(), NOW()),
    ('mockuser05', '043d44617cde91a90f77a5f097ac1182', 'mock05@lcode.dev',  '赵五',   'https://api.dicebear.com/7.x/avataaars/svg?seed=05', '测试工程师',         'user',  NOW(), NOW(), NOW()),
    ('mockuser06', '043d44617cde91a90f77a5f097ac1182', 'mock06@lcode.dev',  '孙六',   'https://api.dicebear.com/7.x/avataaars/svg?seed=06', '产品经理',           'user',  NOW(), NOW(), NOW()),
    ('mockuser07', '043d44617cde91a90f77a5f097ac1182', 'mock07@lcode.dev',  '周七',   'https://api.dicebear.com/7.x/avataaars/svg?seed=07', 'UI 设计师',          'user',  NOW(), NOW(), NOW()),
    ('mockuser08', '043d44617cde91a90f77a5f097ac1182', 'mock08@lcode.dev',  '吴八',   'https://api.dicebear.com/7.x/avataaars/svg?seed=08', 'DevOps 工程师',      'user',  NOW(), NOW(), NOW()),
    ('mockuser09', '043d44617cde91a90f77a5f097ac1182', 'mock09@lcode.dev',  '郑九',   'https://api.dicebear.com/7.x/avataaars/svg?seed=09', '数据分析师',         'user',  NOW(), NOW(), NOW()),
    ('mockuser10', '043d44617cde91a90f77a5f097ac1182', 'mock10@lcode.dev',  '冯十',   'https://api.dicebear.com/7.x/avataaars/svg?seed=10', '算法工程师',         'user',  NOW(), NOW(), NOW());
