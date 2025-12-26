package org.lyflexi.entity;

import lombok.Data;

@Data
public class Stock {
    private Long id;
    private Long productId;
    private Long count;
    private Long version;
    private String name;
}
