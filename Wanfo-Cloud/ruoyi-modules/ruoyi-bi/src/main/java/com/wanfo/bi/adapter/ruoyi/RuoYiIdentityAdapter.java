package com.wanfo.bi.adapter.ruoyi;

import java.util.LinkedHashSet;
import java.util.Set;
import com.wanfo.bi.security.ActorScope;
import com.wanfo.bi.security.BiAccessException;
import com.wanfo.bi.security.BiActor;
import com.wanfo.bi.security.CapabilityProvider;
import com.wanfo.bi.security.CurrentActorProvider;
import com.wanfo.bi.security.PrincipalDirectory;
import com.wanfo.common.security.service.TokenService;
import com.wanfo.common.security.utils.SecurityUtils;
import com.wanfo.system.api.model.LoginUser;
import com.wanfo.system.api.domain.SysUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.PatternMatchUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** The only BI implementation coupled to RuoYi sessions and domain identities. */
@Component
@ConditionalOnProperty(name = "bi.security.mode", havingValue = "ruoyi", matchIfMissing = true)
public class RuoYiIdentityAdapter implements CurrentActorProvider, CapabilityProvider
{
    private static final String LOGIN = RuoYiIdentityAdapter.class.getName() + ".login";
    private static final String ACTOR = RuoYiIdentityAdapter.class.getName() + ".actor";
    private final TokenService tokens;
    private final PrincipalDirectory principals;

    public RuoYiIdentityAdapter(TokenService tokens, PrincipalDirectory principals)
    {
        this.tokens = tokens;
        this.principals = principals;
    }

    private HttpServletRequest request()
    {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes))
        {
            throw new BiAccessException(401, "BI 请求缺少可信登录上下文");
        }
        return attributes.getRequest();
    }

    private LoginUser verifiedLogin()
    {
        HttpServletRequest request = request();
        if (request.getAttribute(LOGIN) instanceof LoginUser cached)
        {
            return cached;
        }
        // TokenService verifies the signed JWT and reads the server-side Redis session.
        // Do not use SecurityContextHolder user_id/username: those may originate from request headers.
        String token = SecurityUtils.getToken(request);
        LoginUser login = token == null || token.isBlank() ? null : tokens.getLoginUser(token);
        if (login == null || login.getExpireTime() == null || login.getExpireTime() <= System.currentTimeMillis()
                || login.getSysUser() == null || login.getSysUser().getUserId() == null
                || login.getSysUser().getUserName() == null || login.getSysUser().getUserName().isBlank()
                || !"0".equals(login.getSysUser().getStatus()) || !"0".equals(login.getSysUser().getDelFlag()))
        {
            throw new BiAccessException(401, "未登录或登录状态已失效");
        }
        request.setAttribute(LOGIN, login);
        return login;
    }

    @Override
    public BiActor currentActor()
    {
        BiActor bound = ActorScope.actor();
        if (bound != null)
        {
            return bound;
        }
        HttpServletRequest request = request();
        if (request.getAttribute(ACTOR) instanceof BiActor cached)
        {
            return cached;
        }
        SysUser user = verifiedLogin().getSysUser();
        Long userId = principals.resolve("ruoyi", "user", user.getUserId().toString(), user.getUserName());
        Long organization = user.getDeptId() == null ? null
                : principals.resolve("ruoyi", "organization", user.getDeptId().toString(), null);
        Set<Long> roles = new LinkedHashSet<>();
        if (user.getRoles() != null)
        {
            user.getRoles().stream()
                    .filter(role -> role.getRoleId() != null && "0".equals(role.getStatus())
                            && !"2".equals(role.getDelFlag()))
                    .forEach(role -> roles.add(principals.resolve("ruoyi", "role",
                            role.getRoleId().toString(), role.getRoleName())));
        }
        BiActor actor = new BiActor("ruoyi", userId, user.getUserName(), organization, roles);
        request.setAttribute(ACTOR, actor);
        return actor;
    }

    @Override
    public void require(String capability)
    {
        if (ActorScope.active())
        {
            if (!ActorScope.allows(capability))
            {
                throw new BiAccessException(403, "没有访问权限，请联系管理员授权");
            }
            return;
        }
        Set<String> permissions = verifiedLogin().getPermissions();
        if (capability == null || capability.isBlank() || permissions == null
                || permissions.stream().filter(p -> p != null && !p.isBlank())
                    .noneMatch(p -> "*:*:*".equals(p) || PatternMatchUtils.simpleMatch(p, capability)))
        {
            throw new BiAccessException(403, "没有访问权限，请联系管理员授权");
        }
    }
}
