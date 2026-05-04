package com.nowini.ai.domain;

import java.util.List;

public record TimingVerdict(
        Verdict verdict,
        int score,
        List<TimingFactor> factorsMet,
        List<TimingFactor> factorsUnmet,
        String summaryKo,
        String disclaimerKo
) {
    public enum Verdict { NOW, NOT_YET, UNCERTAIN }
}
