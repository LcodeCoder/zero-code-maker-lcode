package com.commul.ailcode.core.parser;

import com.commul.ailcode.ai.model.HtmlCodeResult;
import com.commul.ailcode.exception.BusinessException;
import com.commul.ailcode.exception.ErrorCode;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML 单文件代码解析器。只保存完整文档，不将模型的说明文字当作网页。
 */
public class HtmlCodeParser implements CodeParser<HtmlCodeResult> {

    private static final Pattern HTML_DOCUMENT_PATTERN = Pattern.compile(
            "(?:<!DOCTYPE\\s+html\\b[^>]*>\\s*)?<html\\b[^>]*>[\\s\\S]*?</html\\s*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_BODY_PATTERN = Pattern.compile(
            "<body\\b[^>]*>[\\s\\S]*?</body\\s*>", Pattern.CASE_INSENSITIVE);

    @Override
    public HtmlCodeResult parseCode(String codeContent) {
        if (codeContent == null || codeContent.isBlank()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "模型未返回 HTML 代码，请重新生成");
        }
        // 同时兼容纯 HTML、Markdown 代码块以及代码块之外的说明文字。
        Matcher matcher = HTML_DOCUMENT_PATTERN.matcher(codeContent);
        if (!matcher.find() || !HTML_BODY_PATTERN.matcher(matcher.group()).find()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "生成的 HTML 不完整，缺少完整的 html 或 body 标签，请重新生成");
        }
        HtmlCodeResult result = new HtmlCodeResult();
        result.setHtmlCode(matcher.group().trim());
        return result;
    }
}
