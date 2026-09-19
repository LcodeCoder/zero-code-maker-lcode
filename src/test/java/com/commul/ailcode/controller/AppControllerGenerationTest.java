package com.commul.ailcode.controller;

import com.commul.ailcode.exception.BusinessException;
import com.commul.ailcode.exception.ErrorCode;
import com.commul.ailcode.model.entity.User;
import com.commul.ailcode.service.AppService;
import com.commul.ailcode.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppControllerGenerationTest {
    private final AppService appService = mock(AppService.class);
    private final UserService userService = mock(UserService.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final User user = new User();
    private final AppController controller = new AppController();

    AppControllerGenerationTest() {
        ReflectionTestUtils.setField(controller, "appService", appService);
        ReflectionTestUtils.setField(controller, "userService", userService);
        when(userService.getLoginUser(request)).thenReturn(user);
    }

    @Test
    void successfulStreamEndsWithNonEmptyFinishEvent() {
        when(appService.chatToGenCode(1L, "企业官网首页", user)).thenReturn(Flux.just("HTML"));
        var events = Objects.requireNonNull(controller.chatToGenCode(1L, "企业官网首页", request).collectList().block());
        assertEquals(2, events.size());
        assertEquals("finish", events.get(1).event());
        assertEquals("done", events.get(1).data());
    }

    @Test
    void failedStreamReportsReasonWithoutFinishEvent() {
        when(appService.chatToGenCode(1L, "企业官网首页", user)).thenReturn(Flux.concat(
                Flux.just("截断的内容"),
                Flux.error(new BusinessException(ErrorCode.OPERATION_ERROR, "生成的 HTML 不完整"))));
        var events = Objects.requireNonNull(controller.chatToGenCode(1L, "企业官网首页", request).collectList().block());
        assertEquals(2, events.size());
        assertEquals("generation_error", events.get(1).event());
        assertTrue(Objects.requireNonNull(events.get(1).data()).contains("不完整"));
        assertTrue(events.stream().noneMatch(event -> "finish".equals(event.event())));
    }
}
