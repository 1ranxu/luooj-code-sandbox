package com.luoying.exception;

/**
 * @Author 落樱的悔恨
 * @Date 2025/3/17 20:28
 */
public class TimeLimitExceededException extends RuntimeException{
    public TimeLimitExceededException(String message) {
        super(message);
    }
}
