/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dromara.hertzbeat.manager.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.dromara.hertzbeat.common.entity.dto.Message;
import org.dromara.hertzbeat.common.entity.job.Job;
import org.dromara.hertzbeat.common.entity.manager.ParamDefine;
import org.dromara.hertzbeat.manager.pojo.dto.Hierarchy;
import org.dromara.hertzbeat.manager.pojo.dto.MonitorDefineDto;
import org.dromara.hertzbeat.manager.service.AppService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Locale;

import static org.dromara.hertzbeat.common.constants.CommonConstants.FAIL_CODE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

/**
 * Monitoring Type Management API
 * 监控类型管理API
 *
 * @author tomsun28
 */
@Tag(name = "Monitor Type Manage API | 监控类型管理API")
@RestController
@RequestMapping(path = "/api/apps", produces = {APPLICATION_JSON_VALUE})
public class AppController {

    private static final String[] RISKY_STR_ARR = {"ScriptEngineManager", "URLClassLoader"};
    
    @Autowired
    private AppService appService;

    @GetMapping(path = "/{app}/params")
    @Operation(summary = "The structure of the input parameters required to specify the monitoring type according to the app query", description = "根据app查询指定监控类型的需要输入参数的结构")
    public ResponseEntity<Message<List<ParamDefine>>> queryAppParamDefines(
            @Parameter(description = "en: Monitoring type name,zh: 监控类型名称", example = "api") @PathVariable("app") final String app) {
        List<ParamDefine> paramDefines = appService.getAppParamDefines(app.toLowerCase());
        return ResponseEntity.ok(Message.success(paramDefines));
    }

    @GetMapping(path = "/{app}/define")
    @Operation(summary = "The definition structure of the specified monitoring type according to the app query", description = "根据app查询指定监控类型的定义结构")
    public ResponseEntity<Message<Job>> queryAppDefine(
            @Parameter(description = "en: Monitoring type name,zh: 监控类型名称", example = "api") @PathVariable("app") final String app) {
        Job define = appService.getAppDefine(app.toLowerCase());
        return ResponseEntity.ok(Message.success(define));
    }

    @GetMapping(path = "/{app}/define/yml")
    @Operation(summary = "The definition yml of the specified monitoring type according to the app query", description = "根据app查询指定监控类型的定义YML")
    public ResponseEntity<Message<String>> queryAppDefineYml(
            @Parameter(description = "en: Monitoring type name,zh: 监控类型名称", example = "api") @PathVariable("app") final String app) {
        String defineContent = appService.getMonitorDefineFileContent(app);
        return ResponseEntity.ok(Message.successWithData(defineContent));
    }

    @DeleteMapping(path = "/{app}/define/yml")
    @Operation(summary = "Delete monitor define yml", description = "根据app删除指定监控类型的定义YML")
    public ResponseEntity<Message<Void>> deleteAppDefineYml(
            @Parameter(description = "en: Monitoring type name,zh: 监控类型名称", example = "api") @PathVariable("app") final String app) {
        try {
            appService.deleteMonitorDefine(app);
        } catch (Exception e) {
            return ResponseEntity.ok(Message.fail(FAIL_CODE, e.getMessage()));
        }
        return ResponseEntity.ok(Message.success());
    }

    @PostMapping(path = "/define/yml")
    @Operation(summary = "Add new monitoring type define yml", description = "新增监控类型的定义YML")
    public ResponseEntity<Message<Void>> newAppDefineYml(@Valid @RequestBody MonitorDefineDto defineDto) {
        try {
            for (String riskyToken : RISKY_STR_ARR) {
                if (defineDto.getDefine().contains(riskyToken)) {
                    return ResponseEntity.ok(Message.fail(FAIL_CODE, "can not has malicious remote script"));
                }   
            }
            appService.applyMonitorDefineYml(defineDto.getDefine(), false);
        } catch (Exception e) {
            return ResponseEntity.ok(Message.fail(FAIL_CODE, e.getMessage()));
        }
        return ResponseEntity.ok(Message.success());
    }

    @PutMapping(path = "/define/yml")
    @Operation(summary = "Update monitoring type define yml", description = "更新监控类型的定义YML")
    public ResponseEntity<Message<Void>> updateAppDefineYml(@Valid @RequestBody MonitorDefineDto defineDto) {
        try {
            for (String riskyToken : RISKY_STR_ARR) {
                if (defineDto.getDefine().contains(riskyToken)) {
                    return ResponseEntity.ok(Message.fail(FAIL_CODE, "can not has malicious remote script"));
                }
            }
            appService.applyMonitorDefineYml(defineDto.getDefine(), true);
        } catch (Exception e) {
            return ResponseEntity.ok(Message.fail(FAIL_CODE, e.getMessage()));
        }
        return ResponseEntity.ok(Message.success());
    }

    @GetMapping(path = "/hierarchy")
    @Operation(summary = "Query all monitor metrics level, output in a hierarchical structure", description = "查询所有监控的类型-指标组-指标层级,以层级结构输出")
    public ResponseEntity<Message<List<Hierarchy>>> queryAppsHierarchy(
            @Parameter(description = "en: language type,zh: 语言类型",
                    example = "zh-CN")
            @RequestParam(name = "lang", required = false) String lang) {
        if (lang == null || lang.isEmpty()) {
            lang = "zh-CN";
        }
        if (lang.contains(Locale.ENGLISH.getLanguage())) {
            lang = "en-US";
        } else if (lang.contains(Locale.CHINESE.getLanguage())) {
            lang = "zh-CN";
        } else {
            lang = "en-US";
        }
        List<Hierarchy> appHierarchies = appService.getAllAppHierarchy(lang);
        return ResponseEntity.ok(Message.success(appHierarchies));
    }
}
