package io.choerodon.oauth.infra.common.util;

import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.oauth.domain.entity.ClientE;
import io.choerodon.oauth.infra.enums.ClientTypeEnum;
import io.choerodon.oauth.infra.feign.DevopsFeignClient;
import io.choerodon.oauth.infra.mapper.ClientMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.NoSuchClientException;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author zongw.lee@gmail.com
 * @date 2019/10/18
 */
@Component
public class CustomClientInterceptor implements HandlerInterceptor {

    private static final String CLIENT_ID = "client_id";
    private static final String CHECK_TOKEN = "/**/check_token";
    private ClientMapper clientMapper;
    private DevopsFeignClient devopsFeignClient;
    private AntPathMatcher antPathMatcher = new AntPathMatcher();

    public CustomClientInterceptor(ClientMapper clientMapper, DevopsFeignClient devopsFeignClient) {
        this.clientMapper = clientMapper;
        this.devopsFeignClient = devopsFeignClient;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (antPathMatcher.match(CHECK_TOKEN,request.getRequestURI())) {
            return true;
        }
        Long userId;
        String clientId = request.getParameter(CLIENT_ID);
        ClientE client = getClientByName(clientId);
        if (client == null) {
            throw new NoSuchClientException("No client found : " + clientId);
        }
        // 不需要做普罗米修斯的客户端权限校验
        if (!ClientTypeEnum.CLUSTER.value().equals(client.getSourceType())) {
            return true;
        }

        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof CustomUserDetails) {
            userId = ((CustomUserDetails) principal).getUserId();
        } else {
            return false;
        }
        // 调用devops接口校验用户是否有访问集群的权限
        Boolean result = devopsFeignClient.checkUserClusterPermission(client.getSourceId(), userId).getBody();
        if (Boolean.FALSE.equals(result)) {
            throw new AccessDeniedException("权限不足");
        }
        return true;
    }

    private ClientE getClientByName(String clientName) {
        ClientE clientE = new ClientE();
        clientE.setName(clientName);
        return clientMapper.selectOne(clientE);
    }
}
