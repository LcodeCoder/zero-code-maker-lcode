package com.commul.ailcode.constant;

/**
 * 应用常量
 */
public interface AppConstant {

    /**
     * 应用生成目录
     */
    String CODE_OUTPUT_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_output";

    /**
     * 应用部署目录
     */
    String CODE_DEPLOY_ROOT_DIR = System.getProperty("user.dir") + "/tmp/code_deploy";

    /**
     * 应用部署访问地址前缀（指向后端静态资源服务）
     */
    String CODE_DEPLOY_HOST = "http://localhost:8080/api/static";

}
