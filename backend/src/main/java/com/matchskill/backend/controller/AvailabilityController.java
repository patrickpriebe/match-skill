package com.matchskill.backend.controller;

import com.matchskill.backend.dto.availability.AvailabilityWindowResponse;
import com.matchskill.backend.dto.availability.ReplaceAvailabilityRequest;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.AvailabilityService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailabilityWindowResponse> getMyAvailability(Authentication authentication) {
        return availabilityService.getMyAvailability(CurrentUser.id(authentication));
    }

    @PutMapping
    public List<AvailabilityWindowResponse> replaceMyAvailability(
            Authentication authentication, @Valid @RequestBody ReplaceAvailabilityRequest request) {
        return availabilityService.replaceMyAvailability(
                CurrentUser.id(authentication), request.windows(), request.timeZone());
    }
}
