package com.commul.ailcode.model.dto.app;

import lombok.Data;
import lombok.Getter;

import java.io.Serializable;


@Data
public class AppDeployRequest implements Serializable {
    private static final long serialVersionUID = 1L;

    private long appId;
}
