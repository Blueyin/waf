package info.yangguo.waf.request.security;

import com.codahale.metrics.Timer;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.RateLimiter;
import info.yangguo.waf.Constant;
import info.yangguo.waf.WafHttpHeaderNames;
import info.yangguo.waf.config.ContextHolder;
import info.yangguo.waf.model.SecurityConfigItem;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author:杨果
 * @date:2017/5/12 上午11:37
 * <p>
 * Description:
 * cc拦截
 */
public class CCSecurity extends Security {
    private static final Logger logger = LoggerFactory.getLogger(CCSecurity.class);
    private LoadingCache<String, RateLimiter> loadingCache;


    public CCSecurity() {
        loadingCache = CacheBuilder.newBuilder()
                .maximumSize(1000)
                .removalListener(notification -> {
                    logger.debug("key:{} remove from cache", notification.getKey());
                })
                .build(new CacheLoader<String, RateLimiter>() {
                    @Override
                    public RateLimiter load(String key) {
                        RateLimiter rateLimiter = RateLimiter.create(Integer.valueOf(key.split(":")[2]));
                        logger.debug("RateLimiter for key:{} have been created", key);
                        return rateLimiter;
                    }
                });
    }

    public HttpResponseStatus getHttpResponseStatus() {
        return HttpResponseStatus.SERVICE_UNAVAILABLE;
    }

    @Override
    public boolean doFilter(HttpRequest originalRequest, HttpObject httpObject, List<SecurityConfigItem> items) {
        if (httpObject instanceof HttpRequest) {
            logger.debug("filter:{}", this.getClass().getName());
            HttpRequest httpRequest = (HttpRequest) httpObject;
            Optional<String> url;
            int index = httpRequest.uri().indexOf("?");
            if (index > -1) {
                url = Optional.ofNullable(httpRequest.uri().substring(0, index));
            } else {
                url = Optional.ofNullable(httpRequest.uri());
            }
            if (url.isPresent()) {
                //多个正则表达式匹配，选择值最小的作为阈值
                Optional<Map.Entry<String, Object>> matching = ContextHolder
                        .getClusterService()
                        .getSecurityConfigs()
                        .get(CCSecurity.class.getName())
                        .getSecurityConfigItems()
                        .parallelStream()
                        .filter(item -> {
                            if (originalRequest.headers().getAsString(WafHttpHeaderNames.X_WAF_ROUTE).equals(item.getName()) && item.getConfig().getIsStart())
                                return true;
                            else
                                return false;
                        })
                        .flatMap(item -> {
                            return item.getConfig().getExtension().entrySet().parallelStream();
                        }).filter(entry -> {
                            Timer itemTimer = Constant.metrics.timer("CCSecurity[" + entry.getKey() + "]");
                            Timer.Context itemContext = itemTimer.time();
                            try {
                                Pattern pattern = Pattern.compile(entry.getKey());
                                Matcher matcher = pattern.matcher(url.get());
                                if (matcher.matches()) {
                                    return true;
                                } else {
                                    return false;
                                }
                            } finally {
                                itemContext.stop();
                            }
                        }).min((e1, e2) -> ((Integer) e1.getValue()).compareTo(((Integer) e2.getValue())));

                if (matching.isPresent()) {
                    //由于正则包含各种特色字符，所以直接算了一直hash值，方便loadingCache直接根据冒号切割。
                    //阈值放到key中原因如下：
                    //1.由于阈值可能出现变化，从而保证随时阈值实时生效。
                    //2.rate limiter需要使用。
                    String key = originalRequest.headers().getAsString(WafHttpHeaderNames.X_WAF_ROUTE) + ":" + String.valueOf(Hashing.murmur3_32().hashBytes(matching.get().getKey().getBytes()).padToLong()) + ":" + String.valueOf(matching.get().getValue());
                    try {
                        RateLimiter rateLimiter = loadingCache.get(key);
                        if (rateLimiter.tryAcquire()) {
                            return false;
                        } else {
                            hackLog(logger, originalRequest.headers().getAsString(WafHttpHeaderNames.X_REAL_IP), "cc", String.valueOf(rateLimiter.getRate()));
                            return true;
                        }
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }
}
