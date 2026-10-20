package com.wanfo.bi.security;

public class BiAccessException extends RuntimeException
{
    private final int code;

    public BiAccessException(int code, String message)
    {
        super(message);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
