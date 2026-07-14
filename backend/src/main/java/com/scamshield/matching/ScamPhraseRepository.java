package com.scamshield.matching;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Reads the scam-phrase registry used to build the Aho-Corasick automaton. */
@Repository
public class ScamPhraseRepository {

    private final JdbcTemplate jdbc;

    public ScamPhraseRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScamPhrase> findAll() {
        return jdbc.query(
                "SELECT id, phrase, weight, category FROM scam_phrases",
                (rs, i) -> new ScamPhrase(rs.getLong("id"), rs.getString("phrase"),
                        rs.getDouble("weight"), rs.getString("category")));
    }
}
