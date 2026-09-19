package com.commul.ailcode.service;

import com.commul.ailcode.model.dto.chathistory.ChatHistoryQueryRequest;
import com.commul.ailcode.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.commul.ailcode.model.entity.ChatHistory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author <a href="https://github.com/Linyu-H">lcode</a>
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加聊天记录
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return 是否成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);


    /**
     * 根据应用id删除对话记录
     * @param appId
     * @return
     */
    boolean deleteByAppId(Long appId);


    /**
     * 获取查询包装类
     *
     * @param chatHistoryQueryRequest
     * @return
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);


    /**
     * 根据应用id分页获取对话记录
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);
}
