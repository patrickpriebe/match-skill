package com.matchskill.backend.controller;

import com.matchskill.backend.dto.common.PageResponse;
import com.matchskill.backend.dto.skill.SkillResponse;
import com.matchskill.backend.dto.skill.SuggestSkillRequest;
import com.matchskill.backend.service.SkillService;
import jakarta.validation.Valid;
import com.matchskill.backend.util.Pagination;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public PageResponse<SkillResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                skillService.search(query, Pagination.pageRequest(page, size)
                        .withSort(Sort.by("name", "id"))).map(SkillResponse::from));
    }

    @PostMapping("/suggest")
    public ResponseEntity<SkillResponse> suggest(@Valid @RequestBody SuggestSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SkillResponse.from(skillService.suggest(request.name())));
    }
}
