-- =====================================================================
-- 迁移：封面 / 头像列改 longtext（修复 base64 超长导致「系统错误」）
-- 日期：2026-06-24
-- 背景：
--   app.cover  原为 varchar(512)
--   user.userAvatar 原为 varchar(1024)
--   而前面上传均以 base64 直存，远超列长 → 写入抛 RuntimeException
--   → GlobalExceptionHandler 兜底返回「系统错误」。
--   例：管理员在「应用管理 → 编辑」上传封面后保存即触发。
-- 执行：在现有 ai_lcode_maker 库手动执行本脚本。
-- =====================================================================

use ai_lcode_maker;

-- 应用封面：varchar(512) -> longtext
ALTER TABLE `app`
    MODIFY COLUMN `cover` LONGTEXT NULL COMMENT '应用封面（base64）';

-- 用户头像：varchar(1024) -> longtext
ALTER TABLE `user`
    MODIFY COLUMN `userAvatar` LONGTEXT NULL COMMENT '用户头像（base64）';
