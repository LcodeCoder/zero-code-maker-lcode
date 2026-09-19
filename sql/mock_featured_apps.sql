-- ============================================================
-- 精选应用模拟数据
-- 说明：直接复用已真实生成代码（tmp/code_output 下有 index.html）的应用，
--       补上有意义的应用名并设为精选（priority = 99），
--       这样首页「精选应用」卡片点进去能真正预览，不会 404。
-- 幂等：可重复执行，仅按 id 更新既有行。
-- ============================================================

-- 创新科技企业官网
UPDATE app
SET appName  = '创新科技 · 企业官网',
    initPrompt = '生成一个现代科技公司的企业官网首页，含导航、Banner、产品介绍与联系方式',
    codeGenType = 'html',
    priority = 99
WHERE id = 427119265506787328;

-- Lcode 个人简历
UPDATE app
SET appName  = 'Lcode · 个人简历',
    initPrompt = '给我生成 Lcode 的个人简历网站，含头像、技能条与项目经历',
    codeGenType = 'html',
    priority = 99
WHERE id = 427121413061738496;

-- Lcode 个人博客（蓝）
UPDATE app
SET appName  = 'Lcode · 极简博客',
    initPrompt = '生成一个极简风格的 Lcode 个人博客首页，含文章列表与分类',
    codeGenType = 'html',
    priority = 98
WHERE id = 427125781634875392;

-- Lcode 个人博客（精选首位）
UPDATE app
SET appName  = 'Lcode · 创作者博客',
    initPrompt = '生成一个 Lcode 个人博客，突出作者介绍与最新动态',
    codeGenType = 'html',
    priority = 97
WHERE id = 427129441752330240;

-- 把那条没有真实代码目录的旧精选降级，避免点进去 404
UPDATE app
SET priority = 0
WHERE id = 427115270776160256;

-- 查看结果
SELECT id, appName, codeGenType, priority
FROM app
WHERE priority > 0
ORDER BY priority DESC;
