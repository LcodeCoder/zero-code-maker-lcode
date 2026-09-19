package com.commul.ailcode.core;

import com.commul.ailcode.ai.AiCodeGeneratorService;
import com.commul.ailcode.core.saver.CodeFileSaverExecutor;
import com.commul.ailcode.exception.BusinessException;
import com.commul.ailcode.model.enums.CodeGenTypeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiCodeGeneratorFacadeTest {
    @Test
    void truncatedStreamFailsWithoutSaving() {
        AiCodeGeneratorService generator = mock(AiCodeGeneratorService.class);
        when(generator.generateHtmlCodeStream("官网")).thenReturn(Flux.just(
                "这是为您生成的网站\n```html\n<!DOCTYPE html>",
                "<html><head><style>.section { padding: 80px 0; }"));
        AiCodeGeneratorFacade facade = new AiCodeGeneratorFacade();
        ReflectionTestUtils.setField(facade, "aiCodeGeneratorService", generator);

        try (var saver = mockStatic(CodeFileSaverExecutor.class)) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> facade.generateAndSaveCodeStream("官网", CodeGenTypeEnum.HTML, 1L).blockLast());
            assertTrue(error.getMessage().contains("不完整"));
            saver.verifyNoInteractions();
        }
    }
}
