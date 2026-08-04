package com.huazaiki.common.event;

/**
 * Outbox 状态常量。
 */
public final class OutboxStatus {

    public static final String PENDING = "PENDING";
    public static final String PUBLISHED = "PUBLISHED";
    public static final String FAILED = "FAILED";

    private OutboxStatus() {}
}