package com.erp.portal.administration;

import com.erp.portal.administration.SettingsDtos.SettingDto;
import com.erp.portal.administration.SettingsDtos.UpdateSettingRequest;
import com.erp.portal.security.SecurityPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsController {
    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SETTINGS:READ')")
    public ResponseEntity<List<SettingDto>> listSettings(@AuthenticationPrincipal SecurityPrincipal principal) {
        return ResponseEntity.ok(settingsService.listSettings(principal));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAuthority('SETTINGS:UPDATE')")
    public ResponseEntity<SettingDto> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request,
            @AuthenticationPrincipal SecurityPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(settingsService.updateSetting(key, request, principal, httpRequest));
    }
}
