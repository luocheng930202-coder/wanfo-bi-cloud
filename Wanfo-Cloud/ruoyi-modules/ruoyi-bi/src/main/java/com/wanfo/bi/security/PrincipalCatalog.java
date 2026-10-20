package com.wanfo.bi.security;

import java.util.List;
import java.util.Map;

public interface PrincipalCatalog
{
    List<Map<String, Object>> search(String query);
}
