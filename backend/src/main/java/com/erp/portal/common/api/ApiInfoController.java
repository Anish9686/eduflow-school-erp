package com.erp.portal.common.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Platform", description = "Non-business API foundation")
public class ApiInfoController {

    @GetMapping({"", "/info"})
    @Operation(summary = "API foundation status")
    public ApiInfoResponse info() {
        return new ApiInfoResponse("ERP Portal API", "v1", "READY");
    }
}
