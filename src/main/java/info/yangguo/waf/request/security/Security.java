package info.yangguo.waf.request.security;

import info.yangguo.waf.model.SecurityConfigItem;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;

import java.util.List;

/**
 * @author:杨果
 * @date:2017/4/11 上午11:28
 * <p>
 * Description:
 * <p>
 * HTTP Request拦截器抽象类
 */
public abstract class Security {
    /**
     * httpRequest拦截逻辑
     *
     * @param originalRequest original request
     * @param httpObject      http请求
     * @return true:正则匹配成功,false:正则匹配失败
     */
    public abstract boolean doFilter(HttpRequest originalRequest, HttpObject httpObject, List<SecurityConfigItem> items);

    /**
     * 是否是黑名单
     *
     * @return 黑名单返回true, 白名单返回false, 白名单的实现类要重写次方法
     */
    public boolean isBlacklist() {
        return true;
    }

    /**
     * 被拦截是返回的HttpResponseStatus
     *
     * @return
     */
    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.FORBIDDEN;
    }

    /**
     * 记录hack日志
     *
     * @param realIp 用户IP
     * @param logger 日志logger
     * @param type   匹配的类型
     * @param cause  被拦截的原因
     */
    public void hackLog(Logger logger, String realIp, String type, String cause) {
        if (isBlacklist()) {
            logger.info("type:{},realIp:{},cause:{}", type, realIp, cause);
        } else {
            logger.debug("type:{},realIp:{},cause:{}", type, realIp, cause);
        }
    }
}
