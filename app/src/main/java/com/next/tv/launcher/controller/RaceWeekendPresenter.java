package com.next.tv.launcher.controller;

import android.content.Context;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import com.next.tv.launcher.R;
import com.next.tv.launcher.calendar.RaceWeekend;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Presenter for the race-weekend status panel (F1 / motorsport calendar).
 * Updated once a minute with the clock.
 */
public final class RaceWeekendPresenter {

    private static final int RACE_RED = 0xFFFF3B2F;

    private final Context context;
    private final View racePanel;
    private final TextView raceKicker;
    private final TextView raceHeadline;
    private final TextView raceSessions;
    private List<RaceWeekend.Race> raceCalendar;

    public RaceWeekendPresenter(Context context,
                                View racePanel,
                                TextView raceKicker,
                                TextView raceHeadline,
                                TextView raceSessions,
                                List<RaceWeekend.Race> raceCalendar) {
        this.context = context;
        this.racePanel = racePanel;
        this.raceKicker = raceKicker;
        this.raceHeadline = raceHeadline;
        this.raceSessions = raceSessions;
        this.raceCalendar = raceCalendar != null ? raceCalendar : Collections.emptyList();
    }

    public void setRaceCalendar(List<RaceWeekend.Race> raceCalendar) {
        this.raceCalendar = raceCalendar != null ? raceCalendar : Collections.emptyList();
    }

    public void updateRace() {
        if (raceCalendar.isEmpty() || racePanel == null) return;
        RaceWeekend.State s = RaceWeekend.at(raceCalendar, Instant.now());
        if (s == null) {
            racePanel.setVisibility(View.GONE);
            return;
        }
        RaceWeekend.Race race = s.race;
        if (raceKicker != null) {
            raceKicker.setText(race.place.isEmpty()
                    ? context.getString(R.string.race_kicker_no_place, race.round, race.shortName())
                    : context.getString(R.string.race_kicker, race.round, race.shortName(), race.place));
        }

        if (raceHeadline != null) {
            if (s.live != null) {
                SpannableString live = new SpannableString("\u25CF  " + context.getString(R.string.race_live, s.live.title(context.getResources())));
                live.setSpan(new ForegroundColorSpan(RACE_RED), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                raceHeadline.setText(live);
            } else if (s.next != null) {
                String in = RaceWeekend.countdown(context.getResources(), s.untilNext);
                raceHeadline.setText(s.next.isRace() ? context.getString(R.string.race_lights_out, in)
                        : context.getString(R.string.race_next, s.next.title(context.getResources()), in));
            } else {
                raceHeadline.setText(R.string.race_finished);
            }
        }

        if (raceSessions != null) {
            DateTimeFormatter when = DateTimeFormatter.ofPattern(
                    DateFormat.is24HourFormat(context) ? "EEE HH:mm" : "EEE h:mm a", Locale.getDefault())
                    .withZone(ZoneId.systemDefault());
            SpannableStringBuilder row = new SpannableStringBuilder();
            for (int i = 0; i < race.sessions.size(); i++) {
                RaceWeekend.Session session = race.sessions.get(i);
                if (i > 0) row.append("     ");
                int start = row.length();
                row.append(session.shortName(context.getResources())).append("  ").append(when.format(session.start).toUpperCase(Locale.getDefault()));
                int color;
                switch (s.statuses.get(i)) {
                    case DONE: color = 0x47FFFFFF; break;
                    case LIVE: color = RACE_RED; break;
                    case NEXT: color = 0xFFFFFFFF; break;
                    default: color = 0x99FFFFFF;
                }
                row.setSpan(new ForegroundColorSpan(color), start, row.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            raceSessions.setText(row);
        }
        racePanel.setVisibility(View.VISIBLE);
    }
}
