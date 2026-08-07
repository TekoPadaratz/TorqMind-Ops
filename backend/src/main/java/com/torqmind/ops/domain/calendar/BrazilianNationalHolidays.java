package com.torqmind.ops.domain.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.HashSet;
import java.util.Set;

/**
 * Feriados nacionais brasileiros (fixos + móveis via Páscoa).
 * Sem feriados estaduais/municipais — mantém o cadastro simples.
 */
public final class BrazilianNationalHolidays {

    private BrazilianNationalHolidays() {
    }

    public static boolean isHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }
        return holidaysFor(date.getYear()).contains(date);
    }

    public static boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    /** Dia útil = não é fim de semana nem feriado nacional. */
    public static boolean isBusinessDay(LocalDate date) {
        return !isWeekend(date) && !isHoliday(date);
    }

    /** Avança até o próximo dia útil (inclui o próprio dia se já for útil). */
    public static LocalDate nextBusinessDay(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }

    static Set<LocalDate> holidaysFor(int year) {
        Set<LocalDate> set = new HashSet<>();
        set.add(LocalDate.of(year, Month.JANUARY, 1));   // Confraternização Universal
        set.add(LocalDate.of(year, Month.APRIL, 21));    // Tiradentes
        set.add(LocalDate.of(year, Month.MAY, 1));       // Dia do Trabalho
        set.add(LocalDate.of(year, Month.SEPTEMBER, 7)); // Independência
        set.add(LocalDate.of(year, Month.OCTOBER, 12));  // Nossa Senhora Aparecida
        set.add(LocalDate.of(year, Month.NOVEMBER, 2));  // Finados
        set.add(LocalDate.of(year, Month.NOVEMBER, 15)); // Proclamação da República
        set.add(LocalDate.of(year, Month.NOVEMBER, 20)); // Consciência Negra
        set.add(LocalDate.of(year, Month.DECEMBER, 25)); // Natal

        LocalDate easter = easterSunday(year);
        set.add(easter.minusDays(48)); // Carnaval (segunda)
        set.add(easter.minusDays(47)); // Carnaval (terça)
        set.add(easter.minusDays(2));  // Sexta-feira Santa
        set.add(easter.plusDays(60));  // Corpus Christi
        return set;
    }

    /** Algoritmo de Meeus/Jones/Butcher. */
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = ((h + l - 7 * m + 114) % 31) + 1;
        return LocalDate.of(year, month, day);
    }
}
