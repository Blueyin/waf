/*
 * Copyright 2018-present yangguo@outlook.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.yangguo.waf;


import com.codahale.metrics.Slf4jReporter;
import info.yangguo.waf.config.ContextHolder;
import info.yangguo.waf.model.ServerConfig;
import info.yangguo.waf.model.WeightedRoundRobinScheduling;
import info.yangguo.waf.service.ClusterService;
import info.yangguo.waf.util.NetUtils;
import info.yangguo.waf.util.WafSelfSignedSslEngineSource;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import net.lightbody.bmp.mitm.CertificateAndKeySource;
import net.lightbody.bmp.mitm.KeyStoreFileCertificateSource;
import net.lightbody.bmp.mitm.keys.ECKeyGenerator;
import net.lightbody.bmp.mitm.manager.ImpersonatingMitmManager;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.littleshoot.proxy.*;
import org.littleshoot.proxy.impl.DefaultHttpProxyServer;
import org.littleshoot.proxy.impl.ThreadPoolConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportResource;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@ImportResource({"classpath:spring/applicationContext.xml"})
public class Application {
    private static Logger logger = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) {
        //初始化metric reporter
        Slf4jReporter reporter = Slf4jReporter.forRegistry(Constant.metrics)
                .outputTo(LoggerFactory.getLogger("info.yangguo.waf.metrics"))
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(1, TimeUnit.MINUTES);

        SpringApplication.run(Application.class, args);

        ServiceLoader<ClusterService> serviceLoader = ServiceLoader.load(ClusterService.class);
        Iterator<ClusterService> iterator = serviceLoader.iterator();
        if (iterator.hasNext()) {
            ClusterService clusterService = iterator.next();
            ContextHolder.setClusterService(clusterService);
            logger.info("ClusterService SPI:{}", clusterService.getClass().getName());
        } else {
            logger.error("请指定ClusterService SPI实现类");
            System.exit(1);
        }


        ThreadPoolConfiguration threadPoolConfiguration = new ThreadPoolConfiguration();
        threadPoolConfiguration.withAcceptorThreads(Constant.AcceptorThreads);
        threadPoolConfiguration.withClientToProxyWorkerThreads(Constant.ClientToProxyWorkerThreads);
        threadPoolConfiguration.withProxyToServerWorkerThreads(Constant.ProxyToServerWorkerThreads);

        InetSocketAddress inetSocketAddress = new InetSocketAddress(Constant.ServerPort);
        HttpProxyServerBootstrap httpProxyServerBootstrap = DefaultHttpProxyServer.bootstrap()
                .withAddress(inetSocketAddress);
        httpProxyServerBootstrap.withIdleConnectionTimeout(Constant.IdleConnectionTimeout);
        boolean proxyGateway = "on".equals(Constant.wafConfs.get("waf.gateway"));
        boolean proxyGatewayTls = "on".equals(Constant.wafConfs.get("waf.gateway.tls"));
        boolean proxyMitm = "on".equals(Constant.wafConfs.get("waf.mitm"));
        if (proxyGateway && proxyMitm) {
            logger.error("waf.gateway和waf.mitm不能同时开启");
            throw new IllegalArgumentException("waf.gateway和waf.mitm不能同时开启");
        }

        httpProxyServerBootstrap.withAllowRequestToOriginServer(true)
                .withProxyAlias("waf")
                .withThreadPoolConfiguration(threadPoolConfiguration);
        if (proxyGateway) {
            logger.info("gateway模式开启");
            httpProxyServerBootstrap.withServerResolver(new HostResolverImpl());
            if (proxyGatewayTls) {
                logger.info("gateway开启TLS支持");
                httpProxyServerBootstrap
                        //不验证client端证书
                        .withAuthenticateSslClients(false)
                        .withSslEngineSource(new WafSelfSignedSslEngineSource());
            }
            httpProxyServerBootstrap
                    //xff设置
                    .plusActivityTracker(new ActivityTrackerAdapter() {
                        @Override
                        public void requestReceivedFromClient(FlowContext flowContext,
                                                              HttpRequest httpRequest) {

                            StringBuilder xff = new StringBuilder();
                            List<String> headerValues1 = httpRequest.headers().getAll(WafHttpHeaderNames.X_FORWARDED_FOR);
                            if (headerValues1.size() > 0 && headerValues1.get(0) != null) {
                                //逗号面一定要带一个空格
                                xff.append(headerValues1.get(0)).append(", ");
                            }
                            xff.append(NetUtils.getLocalHost());
                            httpRequest.headers().set(WafHttpHeaderNames.X_FORWARDED_FOR, xff.toString());
                        }
                    })
                    //x-real-ip设置
                    .plusActivityTracker(
                            new ActivityTrackerAdapter() {
                                @Override
                                public void requestReceivedFromClient(FlowContext flowContext,
                                                                      HttpRequest httpRequest) {
                                    String realIp = httpRequest.headers().getAsString(WafHttpHeaderNames.X_REAL_IP);
                                    if (realIp == null) {
                                        String remoteAddress = flowContext.getClientAddress().getAddress().getHostAddress();
                                        httpRequest.headers().add(WafHttpHeaderNames.X_REAL_IP, remoteAddress);
                                    }
                                }
                            }
                    )
                    //x-waf-route设置
                    .plusActivityTracker(
                            new ActivityTrackerAdapter() {
                                @Override
                                public void requestReceivedFromClient(FlowContext flowContext,
                                                                      HttpRequest httpRequest) {
                                    if (httpRequest.headers().get(WafHttpHeaderNames.X_WAF_ROUTE) == null) {
                                        //Host包含多个值，只取第一个
                                        List<String> hosts = httpRequest.headers().getAll(HttpHeaderNames.HOST);
                                        if (hosts != null && !hosts.isEmpty()) {
                                            httpRequest.headers().add(WafHttpHeaderNames.X_WAF_ROUTE, hosts.get(0));
                                        }
                                    }
                                }
                            }
                    )
                    .withFiltersSource(new HttpFiltersSourceAdapter() {
                        @Override
                        public HttpFilters filterRequest(HttpRequest originalRequest, ChannelHandlerContext ctx) {
                            return new HttpFilterAdapterImpl(originalRequest, ctx);
                        }
                        @Override
                        public int getMaximumRequestBufferSizeInBytes() {
                            return Integer.parseInt(Constant.wafConfs.get("waf.gateway.forward.maximum_request_buffer_size_bytes"));
                        }
                    })
                    .start();
            ScheduledThreadPoolExecutor scheduledThreadPoolExecutor = new ScheduledThreadPoolExecutor(1);
            scheduledThreadPoolExecutor.scheduleAtFixedRate(new ServerCheckTask(), Integer.parseInt(Constant.wafConfs.get("waf.gateway.forward.http.fail_timeout")), Integer.parseInt(Constant.wafConfs.get("waf.gateway.forward.http.fail_timeout")), TimeUnit.SECONDS);
        } else if (proxyMitm) {
            logger.info("mitm模式开启");
            WafSelfSignedSslEngineSource wafSelfSignedSslEngineSource = new WafSelfSignedSslEngineSource();
            CertificateAndKeySource certificateAndKeySource;
            certificateAndKeySource = new KeyStoreFileCertificateSource(
                    "JKS",
                    wafSelfSignedSslEngineSource.keyStoreFile,
                    "waf",
                    WafSelfSignedSslEngineSource.PASSWORD);


            ImpersonatingMitmManager mitmManager = ImpersonatingMitmManager.builder()
                    .rootCertificateSource(certificateAndKeySource)
                    .serverKeyGenerator(new ECKeyGenerator())
                    .build();
            httpProxyServerBootstrap.withManInTheMiddle(mitmManager).start();
        } else {
            logger.info("proxy模式开启");
            httpProxyServerBootstrap.start();
        }
    }

    private static class ServerCheckTask implements Runnable {
        private final HttpClient client = HttpClientBuilder.create().build();

        @Override
        public void run() {
            logger.debug("upstream server check");
            try {
                for (Map.Entry<String, WeightedRoundRobinScheduling> entry : ContextHolder.getClusterService().getUpstreamConfig().entrySet()) {
                    WeightedRoundRobinScheduling weightedRoundRobinScheduling = entry.getValue();
                    List<ServerConfig> delServerConfigs = new ArrayList<>();
                    CloseableHttpResponse httpResponse = null;
                    for (ServerConfig serverConfig : weightedRoundRobinScheduling.getUnhealthilyServerConfigs()) {
                        HttpGet request = new HttpGet("http://" + serverConfig.getIp() + ":" + serverConfig.getPort());
                        try {
                            httpResponse = (CloseableHttpResponse) client.execute(request);
                            logger.warn("statuscode:{}", httpResponse.getStatusLine().getStatusCode());
                            weightedRoundRobinScheduling.getHealthilyServerConfigs().add(weightedRoundRobinScheduling.getServersMap().get(serverConfig.getIp() + "_" + serverConfig.getPort()));
                            delServerConfigs.add(serverConfig);
                            logger.info("Domain host->{},ip->{},port->{} is healthy.", entry.getKey(), serverConfig.getIp(), serverConfig.getPort());
                        } catch (Exception e1) {
                            logger.warn("Domain host->{},ip->{},port->{} is unhealthy.", entry.getKey(), serverConfig.getIp(), serverConfig.getPort());
                        } finally {
                            if (httpResponse != null) {
                                httpResponse.close();
                            }
                        }
                    }
                    if (delServerConfigs.size() > 0) {
                        weightedRoundRobinScheduling.getUnhealthilyServerConfigs().removeAll(delServerConfigs);
                    }
                }
            } catch (Exception e) {
                logger.error("ServerConfig check task is error.", e);
            }
        }
    }
}
