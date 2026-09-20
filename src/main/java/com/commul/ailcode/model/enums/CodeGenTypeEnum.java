package com.commul.ailcode.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

import java.util.Locale;

/**
 * 代码生成menu
 */
@Getter
public enum CodeGenTypeEnum {

    HTML("原生 HTML 模式", "html"),
    MULTI_FILE("原生多文件模式", "multi_file"),
    VUE("Vue 工程模式", "vue_project");

    private final String text;
    private final String value;

    CodeGenTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举。
     *
     * <p>兼容历史版本保存的 {@code vue}，新数据统一使用 {@code vue_project}。</p>
     *
     * @param value 枚举值的value
     * @return 枚举值
     */
    public static CodeGenTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        String normalizedValue = value.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        for (CodeGenTypeEnum anEnum : CodeGenTypeEnum.values()) {
            if (anEnum.value.equals(normalizedValue)
                    || anEnum.name().toLowerCase(Locale.ROOT).equals(normalizedValue)) {
                return anEnum;
            }
        }
        return switch (normalizedValue) {
            case "vue", "vue3", "vueproject" -> VUE;
            default -> null;
        };
    }
}
