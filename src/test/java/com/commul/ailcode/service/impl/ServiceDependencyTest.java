package com.commul.ailcode.service.impl;

import com.commul.ailcode.core.AiCodeGeneratorFacade;
import com.commul.ailcode.mapper.AppMapper;
import com.commul.ailcode.mapper.ChatHistoryMapper;
import com.commul.ailcode.service.AppService;
import com.commul.ailcode.service.ChatHistoryService;
import com.commul.ailcode.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ServiceDependencyTest {

    @Test
    void servicesStartWithCircularReferencesDisabled() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory().setAllowCircularReferences(false);
            context.getBeanFactory().registerSingleton("appMapper", mock(AppMapper.class));
            context.getBeanFactory().registerSingleton("chatHistoryMapper", mock(ChatHistoryMapper.class));
            context.getBeanFactory().registerSingleton("userService", mock(UserService.class));
            context.getBeanFactory().registerSingleton("aiCodeGeneratorFacade", mock(AiCodeGeneratorFacade.class));
            context.register(AppServiceImpl.class, ChatHistoryServiceImpl.class);

            context.refresh();

            assertNotNull(context.getBean(AppService.class));
            assertNotNull(context.getBean(ChatHistoryService.class));
        }
    }
}
