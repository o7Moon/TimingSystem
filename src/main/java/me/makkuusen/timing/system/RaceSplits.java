package me.makkuusen.timing.system;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;

public class RaceSplits implements Comparable<RaceSplits>{
    private Instant[][] splits;
    private RaceDriver raceDriver;

    public RaceSplits(RaceDriver rd, int laps, int checkpoints)
    {
        splits = new Instant[laps+2][checkpoints+1];
        raceDriver = rd;
    }

    public long getLaptime(int lap){
        if (lap < 1)
        {
            return 0;
        }
        return Duration.between(splits[lap - 1][0], splits[lap][0]).toMillis();
    }

    public void setLapTimeStamp(int lap, Instant timeStamp){
        splits[lap][0] = timeStamp;
    }

    public void setCheckpointTimeStamp(int lap, int checkpoint, Instant timeStamp)
    {
        splits[lap][checkpoint] = timeStamp;
    }

    public RaceDriver getRaceDriver() {
        return raceDriver;
    }

    @Override
    public int compareTo(@NotNull RaceSplits o) {
        if (raceDriver.getLaps() > o.raceDriver.getLaps())
        {
            return -1;
        }
        else if (raceDriver.getLaps() < o.raceDriver.getLaps())
        {
            return 1;
        }

        if (raceDriver.getLatestCheckpoint() > o.raceDriver.getLatestCheckpoint())
        {
            return -1;
        }
        else if (raceDriver.getLatestCheckpoint() < o.raceDriver.getLatestCheckpoint())
        {
            return 1;
        }

        if(o.splits[o.raceDriver.getLaps()][o.raceDriver.getLatestCheckpoint()] != null){
            return splits[raceDriver.getLaps()][raceDriver.getLatestCheckpoint()].compareTo(o.splits[o.raceDriver.getLaps()][o.raceDriver.getLatestCheckpoint()]);
        }
        return -1;
    }
}