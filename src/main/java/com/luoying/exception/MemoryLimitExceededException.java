package com.luoying.exception;

/**
 * @Author 落樱的悔恨
 * @Date 2025/3/17 20:28
 */
public class MemoryLimitExceededException extends RuntimeException{
    public MemoryLimitExceededException(String message) {
        super(message);
    }
}
