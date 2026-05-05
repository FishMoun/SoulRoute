package com.fishmoun.soulroute.preference;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;

@Service
public class TravelPreferenceService {

    private final JdbcTemplate jdbcTemplate;

    public TravelPreferenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<TravelPreference> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                        SELECT departure_city, budget_level, travel_pace, companion_type, interests,
                               dietary_restrictions, accommodation_preference, extra_notes, updated_at
                        FROM user_preferences WHERE user_id = ?
                        """,
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(),
                userId);
    }

    public TravelPreference save(Long userId, TravelPreference preference) {
        jdbcTemplate.update("""
                        INSERT INTO user_preferences(
                            user_id, departure_city, budget_level, travel_pace, companion_type, interests,
                            dietary_restrictions, accommodation_preference, extra_notes, updated_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                        ON CONFLICT (user_id) DO UPDATE SET
                            departure_city = EXCLUDED.departure_city,
                            budget_level = EXCLUDED.budget_level,
                            travel_pace = EXCLUDED.travel_pace,
                            companion_type = EXCLUDED.companion_type,
                            interests = EXCLUDED.interests,
                            dietary_restrictions = EXCLUDED.dietary_restrictions,
                            accommodation_preference = EXCLUDED.accommodation_preference,
                            extra_notes = EXCLUDED.extra_notes,
                            updated_at = now()
                        """,
                userId,
                clean(preference.departureCity()),
                clean(preference.budgetLevel()),
                clean(preference.travelPace()),
                clean(preference.companionType()),
                clean(preference.interests()),
                clean(preference.dietaryRestrictions()),
                clean(preference.accommodationPreference()),
                clean(preference.extraNotes()));
        return findByUserId(userId).orElseThrow();
    }

    private TravelPreference map(ResultSet rs) throws SQLException {
        return new TravelPreference(
                rs.getString("departure_city"),
                rs.getString("budget_level"),
                rs.getString("travel_pace"),
                rs.getString("companion_type"),
                rs.getString("interests"),
                rs.getString("dietary_restrictions"),
                rs.getString("accommodation_preference"),
                rs.getString("extra_notes"),
                rs.getTimestamp("updated_at") == null ? Instant.now() : rs.getTimestamp("updated_at").toInstant()
        );
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
