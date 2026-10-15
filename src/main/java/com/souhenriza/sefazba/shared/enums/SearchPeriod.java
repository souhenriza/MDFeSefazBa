package com.souhenriza.sefazba.shared.enums;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public enum SearchPeriod {
    LAST_MONTH {
        @Override
        public LocalDate startDate() {
            return LocalDate.now().minusMonths(1).with(TemporalAdjusters.firstDayOfMonth());
        }

        @Override
        public LocalDate endDate() {
            return LocalDate.now().minusMonths(1).with(TemporalAdjusters.lastDayOfMonth());
        }
    },
    CURRENT_MONTH {
        @Override
        public LocalDate startDate() {
            return LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        }

        @Override
        public LocalDate endDate() {
            return LocalDate.now().minusDays(1);
        }
    },
    CUSTOM {
        @Override
        public LocalDate startDate() {
            throw new UnsupportedOperationException("Use DateRange.of(start, end) for custom periods");
        }

        @Override
        public LocalDate endDate() {
            throw new UnsupportedOperationException("Use DateRange.of(start, end) for custom periods");
        }
    };

    public abstract LocalDate startDate();

    public abstract LocalDate endDate();

    public DateRange toDateRange() {
        return new DateRange(startDate(), endDate());
    }

    public record DateRange(LocalDate start, LocalDate end) {
        public static DateRange of(LocalDate start, LocalDate end) {
            if (start.isAfter(end)) {
                throw new IllegalArgumentException("Start date must be before or equal to end date");
            }
            return new DateRange(start, end);
        }
    }
}
