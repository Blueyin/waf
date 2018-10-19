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
package info.yangguo.waf.controller;

import com.google.common.collect.Maps;
import info.yangguo.waf.config.ContextHolder;
import info.yangguo.waf.dto.*;
import info.yangguo.waf.model.*;
import info.yangguo.waf.validator.ExistSequence;
import info.yangguo.waf.validator.NotExistSequence;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = "api/config")
@Api(value = "api/config", description = "配置相关的接口")
public class ConfigController {
    @ApiOperation(value = "获取security filter配置")
    @ResponseBody
    @GetMapping(value = "security")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<SecurityConfig>> getSecurityConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<SecurityConfig> value = ContextHolder
                .getClusterService()
                .getSecurityConfigs()
                .entrySet()
                .stream()
                .map(entry -> entry.getValue())
                .collect(Collectors.toList());
        resultDto.setValue(value);
        return resultDto;
    }

    @ApiOperation(value = "获取response filter配置")
    @ResponseBody
    @GetMapping(value = "response")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<ResponseConfig>> getResponseConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<ResponseConfig> value = ContextHolder
                .getClusterService()
                .getResponseConfigs()
                .entrySet()
                .stream()
                .map(entry -> entry.getValue())
                .collect(Collectors.toList());
        resultDto.setValue(value);
        return resultDto;
    }

    @ApiOperation(value = "获取upstream配置")
    @ResponseBody
    @GetMapping(value = "forward/http/upstream")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<UpstreamConfig>> getUpstreamConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<UpstreamConfig> upstreamConfigs = ContextHolder.getClusterService().getUpstreamConfig().entrySet().stream().map(hostEntry -> {
            UpstreamConfig upstreamConfig = UpstreamConfig.builder().wafRoute(hostEntry.getKey()).config(hostEntry.getValue().getBasicConfig()).build();

            Map<String, ServerConfig> map = hostEntry.getValue().getServersMap().entrySet().stream().collect(Collectors.toMap(serverEntry -> {
                return serverEntry.getKey();
            }, entry2 -> {
                return ServerConfig.builder()
                        .ip(entry2.getValue().getIp())
                        .port(entry2.getValue().getPort())
                        .config(entry2.getValue().getConfig())
                        .build();
            }));

            hostEntry.getValue().getUnhealthilyServerConfigs().stream().forEach(server -> {
                map.get(server.getIp() + "_" + server.getPort()).setIsHealth(false);
            });
            hostEntry.getValue().getHealthilyServerConfigs().stream().forEach(server -> {
                map.get(server.getIp() + "_" + server.getPort()).setIsHealth(true);
            });

            List<ServerConfig> serverDtos = map.entrySet().stream().map(entry3 -> {
                return entry3.getValue();
            }).collect(Collectors.toList());

            upstreamConfig.setServerConfigs(serverDtos);

            return upstreamConfig;
        }).collect(Collectors.toList());
        resultDto.setValue(upstreamConfigs);
        return resultDto;
    }


    @ApiOperation(value = "获取rewrite配置")
    @ResponseBody
    @GetMapping(value = "rewrite")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<RewriteConfigDto>> getRewriteConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<RewriteConfigDto> value = ContextHolder.getClusterService().getRewriteConfigs()
                .entrySet().stream()
                .map(entry -> {
                    RewriteConfigDto rewriteConfigDto = RewriteConfigDto.builder().wafRoute(entry.getKey())
                            .isStart(entry.getValue().getIsStart()).build();
                    List<String> items = entry.getValue().getExtension().entrySet().stream()
                            .map(item -> {
                                StringBuilder stringBuilder = new StringBuilder();
                                stringBuilder.append(item.getKey()).append(" ");
                                stringBuilder.append(item.getValue());
                                return stringBuilder.toString();
                            }).collect(Collectors.toList());
                    rewriteConfigDto.setItems(items);
                    return rewriteConfigDto;
                }).collect(Collectors.toList());
        resultDto.setValue(value);
        return resultDto;
    }

    @ApiOperation(value = "获取redirect配置")
    @ResponseBody
    @GetMapping(value = "redirect")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<RedirectConfigDto>> getRedirectConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<RedirectConfigDto> value = ContextHolder.getClusterService().getRedirectConfigs()
                .entrySet().stream()
                .map(entry -> {
                    RedirectConfigDto rewriteConfigDto = RedirectConfigDto.builder().wafRoute(entry.getKey())
                            .isStart(entry.getValue().getIsStart()).build();
                    List<String> items = entry.getValue().getExtension().entrySet().stream()
                            .map(item -> {
                                StringBuilder stringBuilder = new StringBuilder();
                                stringBuilder.append(item.getKey()).append(" ");
                                stringBuilder.append(item.getValue());
                                return stringBuilder.toString();
                            }).collect(Collectors.toList());
                    rewriteConfigDto.setItems(items);
                    return rewriteConfigDto;
                }).collect(Collectors.toList());
        resultDto.setValue(value);
        return resultDto;
    }


    @ApiOperation(value = "设置security filter")
    @ResponseBody
    @PutMapping(value = "security")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setRequestConfig(@RequestBody @Validated SecurityConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().setSecurityConfig(Optional.of(dto.getFilterName()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).build()));
        return resultDto;
    }

    @ApiOperation(value = "设置security filter item")
    @ResponseBody
    @PutMapping(value = "security/item")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setRequestConfig(@RequestBody @Validated(ExistSequence.class) SecurityConfigItemDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());

        ContextHolder.getClusterService().setSecurityConfigItem(Optional.of(dto.getFilterName()),
                Optional.of(dto.getName()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).extension(dto.getExtension()).build()));

        return resultDto;
    }

    @ApiOperation(value = "删除security filter item")
    @ResponseBody
    @DeleteMapping(value = "security/item")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteRequestItem(@RequestBody @Validated(NotExistSequence.class) SecurityConfigItemDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteSecurityConfigItem(Optional.of(dto.getFilterName()), Optional.of(dto.getName()));
        return resultDto;
    }

    @ApiOperation(value = "设置response filter")
    @ResponseBody
    @PutMapping(value = "response")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setResponseConfig(@RequestBody @Validated ResponseConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().setResponseConfig(Optional.of(dto.getFilterName()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).build()));
        return resultDto;
    }


    @ApiOperation(value = "设置upstream")
    @ResponseBody
    @PutMapping(value = "forward/http/upstream")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setUpstreamConfig(@RequestBody @Validated(ExistSequence.class) UpstreamConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().setUpstreamConfig(Optional.of(dto.getWafRoute()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).build()));
        return resultDto;
    }

    @ApiOperation(value = "设置upstream server")
    @ResponseBody
    @PutMapping(value = "forward/http/upstream/server")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setUpstreamConfig(@RequestBody @Validated(ExistSequence.class) UpstreamServerConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().setUpstreamServerConfig(Optional.of(dto.getWafRoute()),
                Optional.of(dto.getIp()),
                Optional.of(dto.getPort()),
                Optional.of(ServerBasicConfig.builder().isStart(dto.getIsStart()).weight(dto.getWeight()).build()));

        return resultDto;
    }

    @ApiOperation(value = "删除upstream")
    @ResponseBody
    @DeleteMapping(value = "forward/http/upstream")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteUpstream(@RequestBody @Validated(NotExistSequence.class) UpstreamConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteUpstream(Optional.of(dto.getWafRoute()));
        return resultDto;
    }

    @ApiOperation(value = "删除upstream server")
    @ResponseBody
    @DeleteMapping(value = "forward/http/upstream/server")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteUpstreamServer(@RequestBody @Validated(NotExistSequence.class) UpstreamServerConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteUpstreamServer(Optional.of(dto.getWafRoute()), Optional.of(dto.getIp()), Optional.of(dto.getPort()));
        return resultDto;
    }


    @ApiOperation(value = "设置rewrite")
    @ResponseBody
    @PutMapping(value = "rewrite")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setRewriteConfig(@RequestBody @Validated(ExistSequence.class) RewriteConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        Map<String, Object> extensions = dto.getItems().stream()
                .map(item -> {
                    String[] parts = item.split(" +");
                    return parts;
                })
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1]));
        ContextHolder.getClusterService().setRewriteConfig(Optional.of(dto.getWafRoute()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).extension(extensions).build()));

        return resultDto;
    }

    @ApiOperation(value = "删除rewrite")
    @ResponseBody
    @DeleteMapping(value = "rewrite")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteRewrite(@RequestBody @Validated(NotExistSequence.class) RewriteConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteRewrite(Optional.of(dto.getWafRoute()));
        return resultDto;
    }


    @ApiOperation(value = "设置redirect")
    @ResponseBody
    @PutMapping(value = "redirect")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setRedirectConfig(@RequestBody @Validated(ExistSequence.class) RedirectConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        Map<String, Object> extensions = dto.getItems().stream()
                .map(item -> {
                    String[] parts = item.split(" +");
                    return parts;
                })
                .collect(Collectors.toMap(parts -> parts[0], parts -> parts[1] + " " + parts[2]));
        ContextHolder.getClusterService().setRedirectConfig(Optional.of(dto.getWafRoute()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).extension(extensions).build()));

        return resultDto;
    }

    @ApiOperation(value = "删除redirect")
    @ResponseBody
    @DeleteMapping(value = "redirect")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteRedirect(@RequestBody @Validated(NotExistSequence.class) RedirectConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteRedirect(Optional.of(dto.getWafRoute()));
        return resultDto;
    }


    @ApiOperation(value = "设置forward translate")
    @ResponseBody
    @PutMapping(value = "forward/translate")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setForwardConfig(@RequestBody @Validated ForwardConfigDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().setTranslateConfig(Optional.of(dto.getWafRoute()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).build()));
        return resultDto;
    }


    @ApiOperation(value = "获取forward translate配置")
    @ResponseBody
    @GetMapping(value = "forward/translate")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto<List<ForwardConfig>> getForwardConfigs() {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        List<ForwardConfig> value = ContextHolder
                .getClusterService()
                .getTranslateConfigs()
                .entrySet()
                .stream()
                .map(entry -> entry.getValue())
                .collect(Collectors.toList());
        resultDto.setValue(value);
        return resultDto;
    }

    @ApiOperation(value = "设置forward translate item")
    @ResponseBody
    @PutMapping(value = "forward/translate/item")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto setForwardConfigItem(@RequestBody @Validated(ExistSequence.class) ForwardConfigItemDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());

        Map<String,Object> extension=Maps.newHashMap();
        extension.put("type",dto.getType().name());
        ContextHolder.getClusterService().setTranslateConfigItem(Optional.of(dto.getWafRoute()),
                Optional.of(dto.getName()), Optional.of(BasicConfig.builder().isStart(dto.getIsStart()).extension(extension).build()));

        return resultDto;
    }

    @ApiOperation(value = "删除forward translate item")
    @ResponseBody
    @DeleteMapping(value = "forward/translate/item")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteForwardConfigItem(@RequestBody @Validated(NotExistSequence.class) ForwardConfigItemDto dto) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteTranslateConfigItem(Optional.of(dto.getWafRoute()), Optional.of(dto.getName()));
        return resultDto;
    }
    @ApiOperation(value = "删除forward translate")
    @ResponseBody
    @DeleteMapping(value = "forward/translate")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "WAFTOKEN", value = "WAFTOKEN",
                    dataType = "string", paramType = "cookie")
    })
    public ResultDto deleteForwardConfig(@RequestBody String waf_route) {
        ResultDto resultDto = new ResultDto();
        resultDto.setCode(HttpStatus.OK.value());
        ContextHolder.getClusterService().deleteTranslateConfig(Optional.of(waf_route));
        return resultDto;
    }
}
