package com.perpus.dao;

import com.fasterxml.jackson.databind.JsonNode;
import com.perpus.config.ApiClient;
import com.perpus.model.LibraryRules;

import java.util.Map;

public class LibraryRulesDAO {
    public LibraryRules find() {
        JsonNode data = ApiClient.get("rules");
        return new LibraryRules(
                data.path("denda_per_hari").asInt(),
                data.path("durasi_pinjam_hari").asInt());
    }

    public void save(LibraryRules rules) {
        ApiClient.put("rules", Map.of(
                "finePerDay", rules.finePerDay(),
                "loanDurationDays", rules.loanDurationDays()));
    }
}
