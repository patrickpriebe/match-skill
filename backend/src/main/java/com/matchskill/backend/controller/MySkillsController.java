package com.matchskill.backend.controller;

import com.matchskill.backend.dto.skill.MySkillsResponse;
import com.matchskill.backend.dto.skill.ReplaceMySkillsRequest;
import com.matchskill.backend.security.CurrentUser;
import com.matchskill.backend.service.UserSkillService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me/skills")
public class MySkillsController {

    private final UserSkillService userSkillService;

    public MySkillsController(UserSkillService userSkillService) {
        this.userSkillService = userSkillService;
    }

    @GetMapping
    public MySkillsResponse getMySkills(Authentication authentication) {
        return userSkillService.getMySkills(CurrentUser.id(authentication));
    }

    @PutMapping
    public MySkillsResponse replaceMySkills(
            Authentication authentication, @Valid @RequestBody ReplaceMySkillsRequest request) {
        return userSkillService.replaceMySkills(
                CurrentUser.id(authentication), request.offeredSkillIds(), request.wantedSkillIds());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMySkill(Authentication authentication, @PathVariable UUID id) {
        userSkillService.deleteMySkill(CurrentUser.id(authentication), id);
        return ResponseEntity.noContent().build();
    }
}
