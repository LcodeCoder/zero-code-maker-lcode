package com.commul.ailcode.core.parser;

import com.commul.ailcode.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class HtmlCodeParserTest {
    private final HtmlCodeParser parser = new HtmlCodeParser();
    private static final String HTML = "<!DOCTYPE html>\n<html lang=\"zh-CN\"><head><style>body { color: blue; }</style></head>"
            + "<body><h1>星云科技</h1><script>console.log('hello')</script></body></html>";

    @Test
    void extractsDocumentWithoutExplanationsOrFences() {
        assertEquals(HTML, parser.parseCode("这是为您生成的网站\n```html\n" + HTML
                + "\n```\n保存后打开即可").getHtmlCode());
        assertEquals(HTML, parser.parseCode("说明文字\n" + HTML + "\n结束说明").getHtmlCode());
        assertEquals(HTML, parser.parseCode(HTML).getHtmlCode());
    }

    @Test
    void acceptsCompleteDocumentEvenIfMarkdownFenceIsNotClosed() {
        assertEquals(HTML, parser.parseCode("说明文字\n```html\n" + HTML).getHtmlCode());
    }

    @Test
    void acceptsCaseInsensitiveTagsAndCrLf() {
        String html = "<!DOCTYPE HTML>\r\n<HTML><HEAD></HEAD><BODY>内容</BODY></HTML>";
        assertEquals(html, parser.parseCode("```HTML\r\n" + html + "\r\n```").getHtmlCode());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ", "这是为您生成的网站", "```html\n```",
            "说明文字\n```html\n<!DOCTYPE html><html><head><style>.section { padding: 80px 0; }",
            "<html><head></head></html>", "<html><body>正文</html>", "<html><body>正文</body>"
    })
    void rejectsMissingOrTruncatedDocuments(String content) {
        assertThrows(BusinessException.class, () -> parser.parseCode(content));
    }
}
