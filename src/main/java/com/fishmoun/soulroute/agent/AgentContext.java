package com.fishmoun.soulroute.agent;

import com.fishmoun.soulroute.preference.TravelPreference;
import com.fishmoun.soulroute.skill.TravelSkill;

import java.util.ArrayList;
import java.util.List;

class AgentContext {

    private final String task;
    private final String chatId;
    private final Long userId;
    private final TravelPreference preference;
    private final List<TravelSkill> skills;
    private final List<AgentStep> steps = new ArrayList<>();

    AgentContext(String task, String chatId) {
        this(task, chatId, null, null, List.of());
    }

    AgentContext(String task, String chatId, Long userId, TravelPreference preference, List<TravelSkill> skills) {
        this.task = task;
        this.chatId = chatId;
        this.userId = userId;
        this.preference = preference;
        this.skills = skills == null ? List.of() : List.copyOf(skills);
    }

    String task() {
        return task;
    }

    String chatId() {
        return chatId;
    }

    Long userId() {
        return userId;
    }

    TravelPreference preference() {
        return preference;
    }

    List<TravelSkill> skills() {
        return skills;
    }

    List<AgentStep> steps() {
        return steps;
    }

    void addStep(AgentStep step) {
        steps.add(step);
    }
}
