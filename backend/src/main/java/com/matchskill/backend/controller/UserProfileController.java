package com.matchskill.backend.controller;

import com.matchskill.backend.dto.feedback.FeedbackResponse;
import com.matchskill.backend.dto.user.UserProfileResponse;
import com.matchskill.backend.service.FeedbackService;
import com.matchskill.backend.service.UserProfileService;
import com.matchskill.backend.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final FeedbackService feedbackService;

    public UserProfileController(UserProfileService userProfileService, FeedbackService feedbackService) {
        this.userProfileService = userProfileService;
        this.feedbackService = feedbackService;
    }

    @GetMapping("/{id}")
    public UserProfileResponse getProfile(@PathVariable UUID id) {
        return userProfileService.getProfile(id);
    }

    @GetMapping("/{id}/feedback")
    public List<FeedbackResponse> getFeedback(Authentication authentication, @PathVariable UUID id) {
        return feedbackService.getReceivedByUser(id, CurrentUser.id(authentication));
    }
}
