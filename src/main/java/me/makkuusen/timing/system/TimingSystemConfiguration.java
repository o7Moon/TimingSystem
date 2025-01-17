package me.makkuusen.timing.system;

import lombok.Getter;

import java.util.List;

@Getter
public class TimingSystemConfiguration {
    private final int leaderboardsUpdateTick;
    private final List<String> leaderboardsFastestTimeLines;
    private final int timesPageSize;
    private final int laps;
    private final int pits;
    private final Integer timeLimit;
    private final Integer qualyStartDelayInMS;
    private final Integer finalStartDelayInMS;
    private final String sqlHost;
    private final int sqlPort;
    private final String sqlDatabase;
    private final String sqlUsername;
    private final String sqlPassword;
    private int scoreboardMaxRows;
    private Integer scoreboardInterval;

    TimingSystemConfiguration(TimingSystem plugin) {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        leaderboardsUpdateTick = plugin.getConfig().getInt("leaderboards.updateticks");
        leaderboardsFastestTimeLines = plugin.getConfig().getStringList("leaderboards.fastesttime.lines");
        timesPageSize = plugin.getConfig().getInt("tracks.timesPageSize");

        laps = plugin.getConfig().getInt("finals.laps");
        pits = plugin.getConfig().getInt("finals.pits");
        timeLimit = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("qualifying.timeLimit"));

        qualyStartDelayInMS = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("qualifying.startDelay"));
        finalStartDelayInMS = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("finals.startDelay"));

        sqlHost = plugin.getConfig().getString("sql.host");
        sqlPort = plugin.getConfig().getInt("sql.port");
        sqlDatabase = plugin.getConfig().getString("sql.database");
        sqlUsername = plugin.getConfig().getString("sql.username");
        sqlPassword = plugin.getConfig().getString("sql.password");

        if (plugin.getConfig().getString("scoreboard.maxRows") != null) {
            scoreboardMaxRows = plugin.getConfig().getInt("scoreboard.maxRows");
        } else {
            scoreboardMaxRows = 15;
        }

        if (plugin.getConfig().getString("scoreboard.interval") != null) {
            scoreboardInterval = ApiUtilities.parseDurationToMillis(plugin.getConfig().getString("scoreboard.interval"));
        } else {
            scoreboardInterval = 1000;
        }
    }


    public void setScoreboardMaxRows(int rows) {
        scoreboardMaxRows = rows;
    }

    public void setScoreboardInterval(String value) {
        scoreboardInterval =  ApiUtilities.parseDurationToMillis(value);
    }
}
