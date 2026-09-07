package com.matchskill.backend.controller;

import com.matchskill.backend.dto.common.PageResponse;
import com.matchskill.backend.dto.match.MatchResponse;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.MatchService;
import java.util.UUID;
import com.matchskill.backend.util.Pagination;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @GetMapping("/matches")
    public PageResponse<MatchResponse> matches(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return matchService.getMatches(CurrentUser.id(authentication), Pagination.pageRequest(page, size));
    }

    @GetMapping("/search")
    public PageResponse<MatchResponse> search(
            Authentication authentication,
            @RequestParam UUID skill,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return matchService.search(CurrentUser.id(authentication), skill, Pagination.pageRequest(page, size));
    }
}
