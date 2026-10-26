package com.z.liveconfig.service;

import com.z.liveconfig.dto.EvaluateDTO;
import com.z.liveconfig.engine.EvalResult;

import java.util.List;

public interface ISdkEvaluateService {

    EvalResult evaluate(Long envId, EvaluateDTO dto);

    /** 控制台调试用：同样走引擎，但不把填进去的 IP 登记成真实实例 */
    EvalResult evaluateConsole(Long envId, EvaluateDTO dto);

    List<EvalResult> evaluateBatch(Long envId, List<String> flagKeys, EvaluateDTO dto);
}
