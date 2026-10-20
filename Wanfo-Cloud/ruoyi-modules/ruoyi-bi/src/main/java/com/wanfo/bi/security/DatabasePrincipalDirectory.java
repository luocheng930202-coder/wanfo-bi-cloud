package com.wanfo.bi.security;

import com.wanfo.bi.mapper.BiPrincipalMapper;
import org.springframework.stereotype.Service;

@Service
public class DatabasePrincipalDirectory implements PrincipalDirectory
{
    private final BiPrincipalMapper mapper;

    public DatabasePrincipalDirectory(BiPrincipalMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public Long resolve(String source, String type, String externalId, String displayName)
    {
        if (source == null || source.isBlank() || source.length() > 32
                || !java.util.Set.of("user", "role", "organization").contains(type == null ? "" : type)
                || externalId == null || externalId.isBlank() || externalId.length() > 128)
        {
            throw new IllegalArgumentException("无效的主体映射标识");
        }
        Long id = mapper.find(source, type, externalId);
        if (id == null)
        {
            mapper.insert(source, type, externalId, displayName);
            id = mapper.find(source, type, externalId);
        }
        if (id == null)
        {
            throw new IllegalStateException("主体映射失败");
        }
        return id;
    }
}
