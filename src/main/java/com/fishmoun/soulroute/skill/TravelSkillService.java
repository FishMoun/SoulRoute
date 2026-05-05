package com.fishmoun.soulroute.skill;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

@Service
public class TravelSkillService {

    private static final String DEFAULT_TRAVEL_PLANNING_PROMPT = """
            旅行规划 Skill：
            - 回答前优先识别目的地、出发地、天数、预算、同行人、节奏和偏好缺口。
            - 本地知识库适合作为景点、美食、人文、避坑和路线素材来源。
            - 生成攻略时结构应包含出行准备、每日路线、交通建议、餐饮建议、预算提示、避坑提醒。
            - 如果用户要求 PDF，必须先整合完整内容，再调用 PDF 工具生成可下载文件。
            """;

    private final JdbcTemplate jdbcTemplate;

    public TravelSkillService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void ensureDefaultSkill() {
        Long skillId = jdbcTemplate.query("""
                        SELECT id FROM travel_skills WHERE skill_key = 'travel-planning'
                        """,
                rs -> rs.next() ? rs.getLong("id") : null);
        if (skillId != null) {
            return;
        }
        skillId = jdbcTemplate.queryForObject("""
                        INSERT INTO travel_skills(skill_key, name, description)
                        VALUES ('travel-planning', '旅行规划基础提示词', 'SoulRoute 默认旅行规划行为 Skill')
                        RETURNING id
                        """, Long.class);
        Long versionId = jdbcTemplate.queryForObject("""
                        INSERT INTO travel_skill_versions(skill_id, version_no, prompt, status, source)
                        VALUES (?, 1, ?, 'ACTIVE', 'bootstrap')
                        RETURNING id
                        """, Long.class, skillId, DEFAULT_TRAVEL_PLANNING_PROMPT);
        jdbcTemplate.update("UPDATE travel_skills SET active_version_id = ?, updated_at = now() WHERE id = ?", versionId, skillId);
    }

    public List<TravelSkill> activeSkills() {
        return jdbcTemplate.query("""
                        SELECT s.id, s.skill_key, s.name, s.description, s.active_version_id, v.prompt
                        FROM travel_skills s
                        JOIN travel_skill_versions v ON v.id = s.active_version_id
                        WHERE v.status = 'ACTIVE'
                        ORDER BY s.id
                        """,
                (rs, rowNum) -> map(rs));
    }

    public List<TravelSkill> listSkills() {
        return jdbcTemplate.query("""
                        SELECT s.id, s.skill_key, s.name, s.description, s.active_version_id, v.prompt
                        FROM travel_skills s
                        LEFT JOIN travel_skill_versions v ON v.id = s.active_version_id
                        ORDER BY s.id
                        """,
                (rs, rowNum) -> map(rs));
    }

    public SkillEvolutionResponse evolve(Long userId, SkillEvolutionRequest request) {
        validate(request);
        DraftSkillVersion draft = createDraftVersion(userId, request);
        return new SkillEvolutionResponse(draft.eventId(), draft.skillId(), draft.versionId(), draft.versionNo(), "DRAFT", null, false);
    }

    public DraftSkillVersion createDraftVersion(Long userId, SkillEvolutionRequest request) {
        validate(request);
        TravelSkill skill = findOrCreateSkill(request.skillKey());
        Integer nextVersion = jdbcTemplate.queryForObject("""
                        SELECT COALESCE(MAX(version_no), 0) + 1 FROM travel_skill_versions WHERE skill_id = ?
                        """, Integer.class, skill.id());
        Long versionId = jdbcTemplate.queryForObject("""
                        INSERT INTO travel_skill_versions(skill_id, version_no, prompt, status, source)
                        VALUES (?, ?, ?, 'DRAFT', ?)
                        RETURNING id
                        """, Long.class, skill.id(), nextVersion, request.proposal().trim(), source(request.source()));
        Long eventId = jdbcTemplate.queryForObject("""
                        INSERT INTO skill_evolution_events(skill_id, user_id, source, signal, proposal, created_version_id)
                        VALUES (?, ?, ?, ?, ?, ?)
                        RETURNING id
                        """, Long.class, skill.id(), userId, source(request.source()), request.signal().trim(), request.proposal().trim(), versionId);
        return new DraftSkillVersion(eventId, skill.id(), versionId, nextVersion);
    }

    public void saveEvaluation(Long skillId, Long versionId, String source, double score, boolean passed, String metricsJson) {
        jdbcTemplate.update("""
                        INSERT INTO skill_evaluation_runs(skill_id, version_id, evaluation_source, score, passed, metrics_json)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, skillId, versionId, source(source), score, passed, metricsJson);
        jdbcTemplate.update("""
                        UPDATE travel_skill_versions
                        SET metrics_json = ?
                        WHERE id = ?
                        """, metricsJson, versionId);
    }

    public SkillActivationResponse activateVersion(Long skillId, Long versionId, String reason) {
        TravelSkillVersion target = findVersion(skillId, versionId);
        if (target == null) {
            throw new IllegalArgumentException("目标 Skill 版本不存在");
        }
        Long previous = jdbcTemplate.query("""
                        SELECT active_version_id FROM travel_skills WHERE id = ?
                        """,
                rs -> rs.next() ? rs.getObject("active_version_id", Long.class) : null,
                skillId);
        jdbcTemplate.update("""
                        UPDATE travel_skill_versions
                        SET status = 'INACTIVE'
                        WHERE skill_id = ? AND status = 'ACTIVE' AND id <> ?
                        """, skillId, versionId);
        jdbcTemplate.update("UPDATE travel_skill_versions SET status = 'ACTIVE' WHERE id = ? AND skill_id = ?", versionId, skillId);
        jdbcTemplate.update("UPDATE travel_skills SET active_version_id = ?, updated_at = now() WHERE id = ?", versionId, skillId);
        jdbcTemplate.update("""
                        INSERT INTO skill_activation_history(skill_id, previous_version_id, active_version_id, reason)
                        VALUES (?, ?, ?, ?)
                        """, skillId, previous, versionId, reason);
        return new SkillActivationResponse(skillId, previous, versionId, "ACTIVE");
    }

    public SkillActivationResponse rollback(Long skillId, Long targetVersionId) {
        TravelSkillVersion target = findVersion(skillId, targetVersionId);
        if (target == null) {
            throw new IllegalArgumentException("目标 Skill 版本不存在");
        }
        return activateVersion(skillId, targetVersionId, "rollback");
    }

    public List<TravelSkillVersion> listVersions(Long skillId) {
        return jdbcTemplate.query("""
                        SELECT id, skill_id, version_no, prompt, status, source, metrics_json, created_at
                        FROM travel_skill_versions
                        WHERE skill_id = ?
                        ORDER BY version_no DESC
                        """,
                (rs, rowNum) -> mapVersion(rs),
                skillId);
    }

    public TravelSkillVersion findVersion(Long skillId, Long versionId) {
        return jdbcTemplate.query("""
                        SELECT id, skill_id, version_no, prompt, status, source, metrics_json, created_at
                        FROM travel_skill_versions
                        WHERE skill_id = ? AND id = ?
                        """,
                rs -> rs.next() ? mapVersion(rs) : null,
                skillId,
                versionId);
    }

    private TravelSkill findOrCreateSkill(String skillKey) {
        TravelSkill existing = jdbcTemplate.query("""
                        SELECT s.id, s.skill_key, s.name, s.description, s.active_version_id, v.prompt
                        FROM travel_skills s
                        LEFT JOIN travel_skill_versions v ON v.id = s.active_version_id
                        WHERE s.skill_key = ?
                        """,
                rs -> rs.next() ? map(rs) : null,
                skillKey.trim());
        if (existing != null) {
            return existing;
        }
        Long id = jdbcTemplate.queryForObject("""
                        INSERT INTO travel_skills(skill_key, name, description)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """, Long.class, skillKey.trim(), skillKey.trim(), "由自进化机制沉淀的旅行 Skill");
        return new TravelSkill(id, skillKey.trim(), skillKey.trim(), "由自进化机制沉淀的旅行 Skill", null, null);
    }

    private void validate(SkillEvolutionRequest request) {
        if (request == null || !StringUtils.hasText(request.skillKey())
                || !StringUtils.hasText(request.signal()) || !StringUtils.hasText(request.proposal())) {
            throw new IllegalArgumentException("skillKey、signal 和 proposal 不能为空");
        }
    }

    private String source(String source) {
        return StringUtils.hasText(source) ? source.trim() : "manual";
    }

    private TravelSkill map(ResultSet rs) throws SQLException {
        return new TravelSkill(
                rs.getLong("id"),
                rs.getString("skill_key"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getObject("active_version_id", Long.class),
                rs.getString("prompt")
        );
    }

    private TravelSkillVersion mapVersion(ResultSet rs) throws SQLException {
        return new TravelSkillVersion(
                rs.getLong("id"),
                rs.getLong("skill_id"),
                rs.getInt("version_no"),
                rs.getString("prompt"),
                rs.getString("status"),
                rs.getString("source"),
                rs.getString("metrics_json"),
                rs.getTimestamp("created_at") == null ? Instant.now() : rs.getTimestamp("created_at").toInstant()
        );
    }

    public record DraftSkillVersion(Long eventId, Long skillId, Long versionId, int versionNo) {
    }
}
