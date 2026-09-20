package com.matchskill.backend.controller;

import com.matchskill.backend.dto.ring.RingResponse;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.RingService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RingController {

    /**
     * Unpaginated on purpose. A ring is an offer to act on now, not a corpus
     * to browse: past the first handful the page stops being a shortlist and
     * the search cost stops being worth paying on a home-feed load.
     */
    private static final int MAX_RINGS = 10;

    private final RingService ringService;

    public RingController(RingService ringService) {
        this.ringService = ringService;
    }

    @GetMapping("/rings")
    public List<RingResponse> rings(
            Authentication authentication, @RequestParam(defaultValue = "5") int limit) {
        int bounded = Math.max(1, Math.min(limit, MAX_RINGS));
        return ringService.findRings(CurrentUser.id(authentication), bounded);
    }
}
