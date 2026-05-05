package com.fishmoun.soulroute.controller;

import com.fishmoun.soulroute.auth.CurrentUserService;
import com.fishmoun.soulroute.skill.SkillEvolutionRequest;
import com.fishmoun.soulroute.skill.TravelSkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel/skills")
@Tag(name = "旅行 Skill 接口", description = "旅行提示词 Skill 和自进化沉淀能力")
public class SkillController {

    private final TravelSkillService skillService;
    private final CurrentUserService currentUserService;

    public SkillController(TravelSkillService skillService, CurrentUserService currentUserService) {
        this.skillService = skillService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    @Operation(summary = "查看旅行 Skill 列表")
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(skillService.listSkills());
    }

    @GetMapping("/{skillId}/versions")
    @Operation(summary = "查看 Skill 版本列表")
    public ResponseEntity<?> versions(@PathVariable Long skillId) {
        return ResponseEntity.ok(skillService.listVersions(skillId));
    }

    @PostMapping("/evolve")
    @Operation(summary = "沉淀新的 Skill 候选版本")
    public ResponseEntity<?> evolve(HttpServletRequest servletRequest,
                                    @RequestBody SkillEvolutionRequest request) {
        try {
            Long userId = currentUserService.currentUser(servletRequest).map(user -> user.id()).orElse(null);
            return ResponseEntity.ok(skillService.evolve(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new TravelController.ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{skillId}/versions/{versionId}/activate")
    @Operation(summary = "激活指定 Skill 版本")
    public ResponseEntity<?> activate(@PathVariable Long skillId, @PathVariable Long versionId) {
        try {
            return ResponseEntity.ok(skillService.activateVersion(skillId, versionId, "manual-activate"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new TravelController.ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/{skillId}/rollback/{versionId}")
    @Operation(summary = "回滚到指定 Skill 版本")
    public ResponseEntity<?> rollback(@PathVariable Long skillId, @PathVariable Long versionId) {
        try {
            return ResponseEntity.ok(skillService.rollback(skillId, versionId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new TravelController.ErrorResponse(e.getMessage()));
        }
    }
}
