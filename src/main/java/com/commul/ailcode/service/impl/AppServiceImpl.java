package com.commul.ailcode.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.commul.ailcode.constant.AppConstant;
import com.commul.ailcode.core.AiCodeGeneratorFacade;
import com.commul.ailcode.exception.BusinessException;
import com.commul.ailcode.exception.ErrorCode;
import com.commul.ailcode.exception.ThrowUtils;
import com.commul.ailcode.mapper.AppMapper;
import com.commul.ailcode.model.dto.app.AppAddRequest;
import com.commul.ailcode.model.dto.app.AppQueryRequest;
import com.commul.ailcode.model.dto.app.AppUpdateRequest;
import com.commul.ailcode.model.entity.App;
import com.commul.ailcode.model.entity.User;
import com.commul.ailcode.model.enums.ChatHistoryMessageTypeEnum;
import com.commul.ailcode.model.enums.CodeGenTypeEnum;
import com.commul.ailcode.model.vo.AppVO;
import com.commul.ailcode.model.vo.UserVO;
import com.commul.ailcode.service.AppService;
import com.commul.ailcode.service.ChatHistoryService;
import com.commul.ailcode.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import dev.langchain4j.data.message.ChatMessageType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author <a href="https://github.com/Linyu-H">lcode</a>
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App> implements AppService {

    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Resource
    private UserService userService;
    @Autowired
    private ChatHistoryService chatHistoryService;

    @Override
    public Long addApp(AppAddRequest appAddRequest, User loginUser) {
        ThrowUtils.throwIf(appAddRequest == null, ErrorCode.PARAMS_ERROR, "请求参数为空");
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        // initPrompt 必填
        String initPrompt = appAddRequest.getInitPrompt();
        ThrowUtils.throwIf(StrUtil.isBlank(initPrompt), ErrorCode.PARAMS_ERROR, "应用初始化 prompt 不能为空");

        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        app.setUserId(loginUser.getId());
        app.setCreateTime(LocalDateTime.now());
        // 应用名称未指定时，取 initPrompt 前 12 位作为默认名称
        if (StrUtil.isBlank(app.getAppName())) {
            app.setAppName(StrUtil.maxLength(initPrompt, 12));
        }
        // 新建应用默认优先级 0
        if (app.getPriority() == null) {
            app.setPriority(0);
        }

        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "创建应用失败");
        return app.getId();
    }

    @Override
    public boolean updateApp(AppUpdateRequest appUpdateRequest, User loginUser) {
        ThrowUtils.throwIf(appUpdateRequest == null || appUpdateRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);

        Long id = appUpdateRequest.getId();
        App oldApp = this.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可修改自己的应用
        ThrowUtils.throwIf(!Objects.equals(oldApp.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        App app = new App();
        app.setId(id);
        // 目前仅支持修改应用名称
        app.setAppName(appUpdateRequest.getAppName());
        return this.updateById(app);
    }

    @Override
    public boolean deleteApp(Long id, User loginUser) {
        ThrowUtils.throwIf(id == null || id <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);

        App oldApp = this.getById(id);
        ThrowUtils.throwIf(oldApp == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人可删除自己的应用
        ThrowUtils.throwIf(!Objects.equals(oldApp.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR);

        return this.removeById(id);
    }

    @Override
    public AppVO getAppVO(App app) {
        ThrowUtils.throwIf(app == null, ErrorCode.PARAMS_ERROR);
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        // 填充创建者信息（单查）
        if (app.getUserId() != null) {
            User user = userService.getById(app.getUserId());
            if (user != null) {
                appVO.setUser(userService.getUserVO(user));
            }
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return List.of();
        }
        // 批量查询创建者信息，避免 N+1
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<User> users = userService.listByIds(userIds);
            if (CollUtil.isNotEmpty(users)) {
                userVOMap = userService.getUserVOList(users).stream()
                        .collect(Collectors.toMap(UserVO::getId, v -> v, (a, b) -> a));
            }
        }
        Map<Long, UserVO> finalMap = userVOMap;
        return appList.stream().map(app -> {
            AppVO appVO = new AppVO();
            BeanUtils.copyProperties(app, appVO);
            if (app.getUserId() != null) {
                appVO.setUser(finalMap.get(app.getUserId()));
            }
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("id", id, v -> v != null && v > 0)
                .like("appName", appName, StrUtil::isNotBlank)
                .like("cover", cover, StrUtil::isNotBlank)
                .like("initPrompt", initPrompt, StrUtil::isNotBlank)
                .eq("codeGenType", codeGenType, StrUtil::isNotBlank)
                .like("deployKey", deployKey, StrUtil::isNotBlank)
                .eq("priority", priority, v -> v != null)
                .eq("userId", userId, v -> v != null && v > 0);
        // sortField 非空时才追加排序，避免 ORDER BY 空列报错
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper = queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        }
        return queryWrapper;
    }

    @Override
    public QueryWrapper getMyQueryWrapper(AppQueryRequest appQueryRequest, Long userId) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String appName = appQueryRequest.getAppName();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("userId", userId, v -> v != null && v > 0)
                .like("appName", appName, StrUtil::isNotBlank);
        if (StrUtil.isNotBlank(sortField)) {
            queryWrapper = queryWrapper.orderBy(sortField, "ascend".equals(sortOrder));
        } else {
            // 默认按创建时间倒序
            queryWrapper = queryWrapper.orderBy("createTime", false);
        }
        return queryWrapper;
    }

    @Override
    public QueryWrapper getFeaturedQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String appName = appQueryRequest.getAppName();
        // 精选应用：priority > 0，按优先级倒序、创建时间倒序
        return QueryWrapper.create()
                .gt("priority", 0)
                .like("appName", appName, StrUtil::isNotBlank)
                .orderBy("priority", false)
                .orderBy("createTime", false);
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String prompt, User loginUser) {

        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用ID错误");
        ThrowUtils.throwIf(prompt == null || prompt.length() < 5, ErrorCode.PARAMS_ERROR, "请输入至少5个字符");

        // 2.查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");

        // 3.权限校验，仅本人可以和自己的应用对话
        ThrowUtils.throwIf(!Objects.equals(app.getUserId(), loginUser.getId()), ErrorCode.NO_AUTH_ERROR, "无权限访问");

        // 4.获取代码生成类型
        CodeGenTypeEnum codeGenType = CodeGenTypeEnum.getEnumByValue(app.getCodeGenType());
        ThrowUtils.throwIf(codeGenType == null, ErrorCode.PARAMS_ERROR, "代码生成类型错误");

        // 5.调用ai前，保护用户信息在数据库中
        chatHistoryService.addChatMessage(appId, prompt, ChatHistoryMessageTypeEnum.USER.getValue(), loginUser.getId());

        // 6.调用代码生成器
        Flux<String> codeFlux = aiCodeGeneratorFacade.generateAndSaveCodeStream(prompt, codeGenType, appId);

        // 7.手机ai响应的内容，并且在完成对话后保存对话内容到数据库
        StringBuilder aiResponseBuilder = new StringBuilder();
        return codeFlux.map(content -> {
            // 实时收集ai响应的内容
            aiResponseBuilder.append(content);
            return content;
        }).doOnComplete(() -> {
            // 流式返回之后，保存对话消息到历史中
            chatHistoryService.addChatMessage(appId, aiResponseBuilder.toString(), ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        }).doOnError(error -> {
            // 如果ai回复失败，也需要保存记录到数据库中
            String errorMessgae = "ai 返回失败：" + error.getMessage();
            chatHistoryService.addChatMessage(appId, errorMessgae, ChatHistoryMessageTypeEnum.AI.getValue(), loginUser.getId());
        });
    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }
        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        return String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
    }


    @Override
    public boolean removeById(Serializable id) {
        if (id == null) {
            return false;
        }

        long appId = Long.parseLong(id.toString());
        if (appId <= 0) {
            return false;
        }
        try {
            chatHistoryService.deleteByAppId(appId);
        } catch (Exception e) {
            log.error("Failed to delete chat history by app ID: {}", appId, e);
        }
        // 删除应用
        return super.removeById(id);
    }
}
