package br.ufes.soe.service.stream;

import java.time.Instant;

public enum AllenIntervalRelation {
    PRECEDES, PRECEDED_BY,
    MEETS, MET_BY,
    OVERLAPS, OVERLAPPED_BY,
    STARTS, STARTED_BY,
    DURING, CONTAINS,
    FINISHES, FINISHED_BY,
    EQUALS;

    // classifica o intervalo do voo (s1,e1) em relacao ao intervalo da tempestade (s2,e2)
    public static AllenIntervalRelation classify(Instant s1, Instant e1, Instant s2, Instant e2) {
        if (e1.isBefore(s2)) return PRECEDES;
        if (e2.isBefore(s1)) return PRECEDED_BY;
        if (e1.equals(s2)) return MEETS;
        if (e2.equals(s1)) return MET_BY;
        if (s1.equals(s2) && e1.equals(e2)) return EQUALS;
        if (s1.equals(s2)) return e1.isBefore(e2) ? STARTS : STARTED_BY;
        if (e1.equals(e2)) return s1.isAfter(s2) ? FINISHES : FINISHED_BY;
        if (s1.isAfter(s2) && e1.isBefore(e2)) return DURING;
        if (s1.isBefore(s2) && e1.isAfter(e2)) return CONTAINS;
        if (s1.isBefore(s2)) return OVERLAPS;
        return OVERLAPPED_BY;
    }

    public boolean isRisky() {
        return this != PRECEDES && this != PRECEDED_BY;
    }
}
