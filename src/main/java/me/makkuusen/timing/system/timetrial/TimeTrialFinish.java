package me.makkuusen.timing.system.timetrial;


import co.aikar.idb.DB;
import co.aikar.idb.DbRow;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.Database;
import me.makkuusen.timing.system.TPlayer;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.track.TrackDatabase;
import net.kyori.adventure.text.Component;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class TimeTrialFinish implements Comparator<TimeTrialFinish> {

    private final int id;
    private final int trackId;
    private final UUID uuid;
    private final long date;
    private final long time;
    private Map<Integer, Long> checkpointTimes = new HashMap<>();

    public TimeTrialFinish(DbRow data) {
        this.id = data.getInt("id");
        this.trackId = data.getInt("trackId");
        this.uuid = data.getString("uuid") == null ? null : UUID.fromString(data.getString("uuid"));
        this.date = data.getInt("date");
        this.time = data.getInt("time");
    }

    public void updateCheckpointTimes(Map<Integer, Long> checkpointTimes) {
        this.checkpointTimes = checkpointTimes;
    }

    public void insertCheckpoints(Map<Integer, Long> checkpointTimes) {
        this.checkpointTimes = checkpointTimes;
        for (Integer key: checkpointTimes.keySet()) {
            DB.executeUpdateAsync("INSERT INTO `ts_finishes_checkpoints`(" +
                    "`finishId`, " +
                    "`checkpointIndex`, " +
                    "`time`) " +
                    "VALUES (" +
                    getId() + "," +
                    key + "," +
                    checkpointTimes.get(key) + ")"
            );
        }
    }

    public @Nullable Long getCheckpointTime(int checkpoint) {
        return checkpointTimes.get(checkpoint);
    }

    public Set<Integer> getCheckpointKeys() {
        return checkpointTimes.keySet();
    }

    public boolean hasCheckpointTimes() {
        return !checkpointTimes.isEmpty();
    }

    public int getId() {
        return id;
    }

    public TPlayer getPlayer() {
        return Database.getPlayer(uuid);
    }

    public long getTime() {
        return time;
    }

    public long getDate() {
        return date;
    }

    public int getTrack() {
        return trackId;
    }

    public Component getDeltaToOther(TimeTrialFinish other, Theme theme, int latestCheckpoint) {
        if (latestCheckpoint > 0) {
            if (other.hasCheckpointTimes() && other.getCheckpointTime(latestCheckpoint) != null) {
                if (other.getDate() > TrackDatabase.getTrackById(getTrack()).get().getDateChanged()) {
                    var otherCheckpoint = other.getCheckpointTime(latestCheckpoint);
                    var currentCheckpoint = getCheckpointTime(latestCheckpoint);
                    if (ApiUtilities.getRoundedToTick(otherCheckpoint) < ApiUtilities.getRoundedToTick(currentCheckpoint)) {
                        return Component.text(" +" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - otherCheckpoint)).color(theme.getError());
                    } else if (ApiUtilities.getRoundedToTick(otherCheckpoint) == ApiUtilities.getRoundedToTick(currentCheckpoint)) {
                        return Component.text(" -" + ApiUtilities.formatAsPersonalGap(currentCheckpoint - otherCheckpoint)).color(theme.getWarning());
                    }else {
                        return Component.text(" -" + ApiUtilities.formatAsPersonalGap(otherCheckpoint - currentCheckpoint)).color(theme.getSuccess());
                    }
                }
            }
        }
        return Component.empty();
    }


    @Override
    public int compare(TimeTrialFinish f1, TimeTrialFinish f2) {
        int result = Long.compare(f1.getTime(), f2.getTime());
        if (result == 0) {
            return Long.compare(f1.getDate(), f2.getDate());
        }
        return result;
    }

    public int compareTo(TimeTrialFinish rf) {
        int result = Long.compare(getTime(), rf.getTime());
        if (result == 0) {
            return Long.compare(getDate(), rf.getDate());
        }
        return result;
    }

    public int compareDate(TimeTrialFinish rf) {
        return Long.compare(getDate(), rf.getDate());
    }

    @Override
    public boolean equals(Object rf) {
        if (rf instanceof TimeTrialFinish timeTrialFinish) {
            return timeTrialFinish.getDate() == getDate() && timeTrialFinish.getPlayer() == getPlayer();
        }
        return false;
    }
}
