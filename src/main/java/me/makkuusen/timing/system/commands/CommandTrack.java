package me.makkuusen.timing.system.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import com.sk89q.worldedit.math.BlockVector2;
import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.Database;
import me.makkuusen.timing.system.LeaderboardManager;
import me.makkuusen.timing.system.TPlayer;
import me.makkuusen.timing.system.TimingSystem;
import me.makkuusen.timing.system.TrackTagManager;
import me.makkuusen.timing.system.api.TimingSystemAPI;
import me.makkuusen.timing.system.gui.TrackGui;
import me.makkuusen.timing.system.text.Error;
import me.makkuusen.timing.system.text.Info;
import me.makkuusen.timing.system.text.Success;
import me.makkuusen.timing.system.text.TextUtilities;
import me.makkuusen.timing.system.theme.Theme;
import me.makkuusen.timing.system.timetrial.TimeTrialController;
import me.makkuusen.timing.system.timetrial.TimeTrialDateComparator;
import me.makkuusen.timing.system.timetrial.TimeTrialFinish;
import me.makkuusen.timing.system.timetrial.TimeTrialFinishComparator;
import me.makkuusen.timing.system.timetrial.TimeTrialSession;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.TrackDatabase;
import me.makkuusen.timing.system.track.TrackLocation;
import me.makkuusen.timing.system.track.TrackPolyRegion;
import me.makkuusen.timing.system.track.TrackRegion;
import me.makkuusen.timing.system.track.TrackTag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

@CommandAlias("track|t")
public class CommandTrack extends BaseCommand {
    public static TimingSystem plugin;

    @Subcommand("move")
    @CommandCompletion("@track")
    @CommandPermission("track.admin")
    public static void onMove(Player player, Track track) {
        var moveTo = player.getLocation().toBlockLocation();
        var moveFrom = track.getSpawnLocation().toBlockLocation();
        World newWorld = moveTo.getWorld();
        track.setSpawnLocation(moveTo);
        var offset = getOffset(moveFrom, moveTo);

        var trackLocations = track.getTrackLocations();
        for (TrackLocation tl : trackLocations) {
            Location loc = tl.getLocation();
            var newLoc = getNewLocation(newWorld, loc, offset);
            track.updateTrackLocation(tl, newLoc);
        }

        var regions = track.getRegions();
        for (TrackRegion region : regions) {
            if (region.getSpawnLocation() != null) {
                region.setSpawn(getNewLocation(newWorld, region.getSpawnLocation(), offset));
            }

            if (region.getMaxP() != null) {
                region.setMaxP(getNewLocation(newWorld, region.getMaxP(), offset));
            }

            if (region.getMinP() != null) {
                region.setMinP(getNewLocation(newWorld, region.getMinP(), offset));
            }

            if (region instanceof TrackPolyRegion polyRegion) {
                var oldPoints = polyRegion.getPolygonal2DRegion().getPoints();
                List<BlockVector2> newPoints = new ArrayList<>();
                for (BlockVector2 b : oldPoints) {
                    newPoints.add(getNewBlockVector2(b, offset));
                }
                polyRegion.updateRegion(newPoints);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(TimingSystem.getPlugin(), LeaderboardManager::updateAllFastestTimeLeaderboard);
        plugin.sendMessage(player, Success.TRACK_MOVED, "to", ApiUtilities.niceLocation(moveTo), "from", ApiUtilities.niceLocation(moveFrom));
    }

    public static Vector getOffset(Location moveFrom, Location moveTo) {
        var vector = new Vector();
        vector.setX(moveFrom.getX() - moveTo.getX());
        vector.setY(moveFrom.getY() - moveTo.getY());
        vector.setZ(moveFrom.getZ() - moveTo.getZ());
        return vector;
    }

    public static Location getNewLocation(World newWorld, Location oldLocation, Vector offset) {
        var referenceNewWorld = new Location(newWorld, oldLocation.getX(), oldLocation.getY(), oldLocation.getZ(), oldLocation.getYaw(), oldLocation.getPitch());
        referenceNewWorld.subtract(offset);
        return referenceNewWorld;
    }

    public static BlockVector2 getNewBlockVector2(BlockVector2 old, Vector offset) {
        return BlockVector2.at(old.getX() - offset.getX(), old.getZ() - offset.getZ());
    }


    @Default
    @CommandPermission("track.admin")
    public static void onTrack(Player player) {
        new TrackGui(Database.getPlayer(player.getUniqueId()), 0).show(player);
    }

    @Subcommand("tp")
    @CommandPermission("track.admin")
    @CommandCompletion("@track @region")
    public static void onTrackTp(Player player, Track track, @Optional String region) {
        if (!track.getSpawnLocation().isWorldLoaded()) {
            plugin.sendMessage(player, Error.WORLD_NOT_LOADED);
            return;
        }


        if (region != null) {
            var rg = region.split("-");
            if (rg.length != 2) {
                plugin.sendMessage(player, Error.SYNTAX);
                return;
            }
            String name = rg[0];
            String index = rg[1];

            var trackRegion = getRegion(track, name, index);

            if (trackRegion != null) {
                player.teleport(trackRegion.getSpawnLocation());
                plugin.sendMessage(player, Success.TELEPORT_TO_TRACK, "%track%", trackRegion.getRegionType().name() + " : " + trackRegion.getRegionIndex());
                return;
            }

            var trackLocation = getTrackLocation(track, name, index);

            if (trackLocation != null) {
                player.teleport(trackLocation.getLocation());
                plugin.sendMessage(player, Success.TELEPORT_TO_TRACK, "%track%", trackLocation.getLocationType().name() + " : " + trackLocation.getIndex());
                return;
            }
            plugin.sendMessage(player, Error.FAILED_TELEPORT);
        } else {
            player.teleport(track.getSpawnLocation());
            plugin.sendMessage(player, Success.TELEPORT_TO_TRACK, "%track%", track.getDisplayName());
        }
    }

    private static TrackRegion getRegion(Track track, String name, String index) {
        try {
            var regionType = TrackRegion.RegionType.valueOf(name);
            var regionIndex = Integer.parseInt(index);

            var trackRegion = track.getRegion(regionType, regionIndex);
            return trackRegion.orElse(null);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static TrackLocation getTrackLocation(Track track, String name, String index) {
        try {
            var locationType = TrackLocation.Type.valueOf(name);
            var regionIndex = Integer.parseInt(index);

            var trackLocation = track.getTrackLocation(locationType, regionIndex);
            return trackLocation.orElse(null);

        } catch (IllegalArgumentException e) {
            return null;
        }
    }


    @Subcommand("create")
    @CommandCompletion("@trackType name")
    @CommandPermission("track.admin")
    public static void onCreate(Player player, Track.TrackType trackType, String name) {
        int maxLength = 25;
        if (name.length() > maxLength) {
            plugin.sendMessage(player, Error.LENGTH_EXCEEDED, "%length%", String.valueOf(maxLength));
            return;
        }

        if (!name.matches("[A-Za-zÅÄÖåäöØÆøæ0-9 ]+")) {
            plugin.sendMessage(player, Error.NAME_FORMAT);
            return;
        }

        if (ApiUtilities.checkTrackName(name)) {
            TimingSystem.getPlugin().sendMessage(player, Error.INVALID_TRACK_NAME);
            return;
        }

        if (!TrackDatabase.trackNameAvailable(name)) {
            plugin.sendMessage(player, Error.TRACK_EXISTS);
            return;
        }

        if (player.getInventory().getItemInMainHand().getItemMeta() == null) {
            plugin.sendMessage(player, Error.ITEM_NOT_FOUND);
            return;
        }

        Track track = TrackDatabase.trackNew(name, player.getUniqueId(), player.getLocation(), trackType, player.getInventory().getItemInMainHand());
        if (track == null) {
            plugin.sendMessage(player, Error.GENERIC);
            return;
        }
        track.setOptions("b");

        plugin.sendMessage(player, Success.CREATED_TRACK, "%track%", name);
        LeaderboardManager.updateFastestTimeLeaderboard(track);
    }

    @Subcommand("info")
    @CommandCompletion("@track @players")
    public static void onInfo(CommandSender commandSender, Track track, @Optional String name) {

        TPlayer tPlayer;
        if (name != null && commandSender.isOp()) {
            tPlayer = Database.getPlayer(name);
            if (tPlayer == null) {
                plugin.sendMessage(commandSender, Error.PLAYER_NOT_FOUND);
                return;
            }
            sendPlayerStatsInfo(commandSender, tPlayer, track);
            return;
        }
        sendTrackInfo(commandSender, track);

    }

    private static void sendPlayerStatsInfo(CommandSender commandSender, TPlayer tPlayer, Track track) {
        commandSender.sendMessage(Component.empty());
        plugin.sendMessage(commandSender, Info.PLAYER_STATS_TITLE, "%player%", tPlayer.getName(), "%track%", track.getDisplayName());
        plugin.sendMessage(commandSender, Info.PLAYER_STATS_POSITION, "%pos%", (track.getPlayerTopListPosition(tPlayer) == -1 ? "(-)" : String.valueOf(track.getPlayerTopListPosition(tPlayer))));
        if (track.getBestFinish(tPlayer) == null) {
            plugin.sendMessage(commandSender, Info.PLAYER_STATS_BEST_LAP, "%time%", "(-)");
        } else {
            plugin.sendMessage(commandSender, Info.PLAYER_STATS_BEST_LAP, "%time%", ApiUtilities.formatAsTime(track.getBestFinish(tPlayer).getTime()));
        }
        plugin.sendMessage(commandSender, Info.PLAYER_STATS_FINISHES, "%size%", String.valueOf(track.getPlayerTotalFinishes(tPlayer)));
        plugin.sendMessage(commandSender, Info.PLAYER_STATS_ATTEMPTS, "%size%", String.valueOf(track.getPlayerTotalFinishes(tPlayer) + track.getPlayerTotalAttempts(tPlayer)));
        plugin.sendMessage(commandSender, Info.PLAYER_STATS_TIME_SPENT, "%size%", ApiUtilities.formatAsTimeSpent(track.getPlayerTotalTimeSpent(tPlayer)));
    }

    private static void sendTrackInfo(CommandSender commandSender, Track track) {
        commandSender.sendMessage(Component.empty());
        plugin.sendMessage(commandSender, Info.TRACK_TITLE, "%name%", track.getDisplayName(), "%id%", String.valueOf(track.getId()));
        if (track.isOpen()) {
            plugin.sendMessage(commandSender, Info.TRACK_OPEN);
        } else {
            plugin.sendMessage(commandSender, Info.TRACK_CLOSED);
        }
        plugin.sendMessage(commandSender, Info.TRACK_TYPE, "%type%", track.getTypeAsString());
        plugin.sendMessage(commandSender, Info.TRACK_DATE_CREATED, "%date%", ApiUtilities.niceDate(track.getDateCreated()), "%owner%", track.getOwner().getName());
        plugin.sendMessage(commandSender, Info.TRACK_OPTIONS, "%options%", ApiUtilities.formatPermissions(track.getOptions()));
        plugin.sendMessage(commandSender, Info.TRACK_MODE, "%mode%", track.getModeAsString());
        plugin.sendMessage(commandSender, Info.TRACK_CHECKPOINTS, "%size%", String.valueOf(track.getRegions(TrackRegion.RegionType.CHECKPOINT).size()));
        if (track.getTrackLocations(TrackLocation.Type.GRID).size() != 0) {
            plugin.sendMessage(commandSender, Info.TRACK_GRIDS, "%size%", String.valueOf(track.getTrackLocations(TrackLocation.Type.GRID).size()));
        }
        if (track.getTrackLocations(TrackLocation.Type.QUALYGRID).size() != 0) {
            plugin.sendMessage(commandSender, Info.TRACK_QUALIFICATION_GRIDS, "%size%", String.valueOf(track.getTrackLocations(TrackLocation.Type.GRID).size()));
        }
        plugin.sendMessage(commandSender, Info.TRACK_RESET_REGIONS, "%size%", String.valueOf(track.getRegions(TrackRegion.RegionType.RESET).size()));
        plugin.sendMessage(commandSender, Info.TRACK_SPAWN_LOCATION, "%location%", ApiUtilities.niceLocation(track.getSpawnLocation()));

        plugin.sendMessage(commandSender, Info.TRACK_WEIGHT, "%size%", String.valueOf(track.getWeight()));
        List<String> tagList = new ArrayList<>();
        for (TrackTag tag : track.getTags()) {
            tagList.add(tag.getValue());
        }
        String tags = String.join(", ", tagList);
        plugin.sendMessage(commandSender, Info.TRACK_TAGS, "%tags%", tags);
    }

    @Subcommand("regions")
    @CommandCompletion("@track")
    @CommandPermission("track.admin")
    public static void onRegions(CommandSender sender, Track track) {
        plugin.sendMessage(sender, Info.REGIONS_TITLE, "%track%", track.getDisplayName());

        for (var regionType : TrackRegion.RegionType.values()) {
            for (TrackRegion trackRegion : track.getRegions(regionType)) {

                String regionText = trackRegion.getRegionType().name() + "-" + trackRegion.getRegionIndex();
                sender.sendMessage(TextUtilities.arrow(TextUtilities.getTheme(sender)).append(Component.text(regionText).clickEvent(ClickEvent.runCommand("/t tp " + track.getCommandName() + " " + regionText))));
            }
        }
    }

    @Subcommand("locations")
    @CommandCompletion("@track")
    @CommandPermission("track.admin")
    public static void onLocations(CommandSender sender, Track track) {
        plugin.sendMessage(sender, Info.LOCATIONS_TITLE, "%track%", track.getDisplayName());

        for (var locationType : TrackLocation.Type.values()) {
            for (TrackLocation trackLocation : track.getTrackLocations(locationType)) {
                String locationText = trackLocation.getLocationType().name() + "-" + trackLocation.getIndex();
                sender.sendMessage(TextUtilities.arrow(TextUtilities.getTheme(sender)).append(Component.text(locationText).clickEvent(ClickEvent.runCommand("/t tp " + track.getCommandName() + " " + locationText))));
            }
        }
    }

    @Subcommand("here")
    public static void onHere(Player player) {
        boolean inRegion = false;
        for (Track track : TrackDatabase.getTracks()) {
            for (TrackRegion region : track.getRegions()) {
                if (region.contains(player.getLocation())) {
                    inRegion = true;
                    player.sendMessage(Component.text(track.getDisplayName() + " - " + region.getRegionType() + " : " + region.getRegionIndex()).color(TextUtilities.getTheme(player).getSecondary()));
                }
            }
        }

        if (!inRegion) {
            plugin.sendMessage(player, Error.REGIONS_NOT_FOUND);
        }
    }

    @Subcommand("session")
    @CommandCompletion("@track")
    public static void toggleSession(Player player, @Optional Track track) {

        var maybeDriver = TimingSystemAPI.getDriverFromRunningHeat(player.getUniqueId());
        if (maybeDriver.isPresent()) {
            if (maybeDriver.get().isRunning()) {
                plugin.sendMessage(player, Error.NOT_NOW);
                return;
            }
        }

        if (TimeTrialController.timeTrialSessions.containsKey(player.getUniqueId())) {
            var ttSession = TimeTrialController.timeTrialSessions.get(player.getUniqueId());
            ttSession.clearScoreboard();
            TimeTrialController.timeTrialSessions.remove(player.getUniqueId());
            if (track == null) {
                plugin.sendMessage(player, Success.SESSION_ENDED);
                return;
            }
            if (!track.getSpawnLocation().isWorldLoaded()) {
                plugin.sendMessage(player, Error.WORLD_NOT_LOADED);
                return;
            }

            if (!track.isOpen() && !(player.isOp() || player.hasPermission("track.admin"))) {
                plugin.sendMessage(player, Error.TRACK_IS_CLOSED);
                return;
            }

            if (track.getId() != ttSession.getTrack().getId()) {
                var newSession = new TimeTrialSession(Database.getPlayer(player.getUniqueId()), track);
                newSession.updateScoreboard();
                TimeTrialController.timeTrialSessions.put(player.getUniqueId(), newSession);
                plugin.sendMessage(player, Success.SESSION_STARTED, "%track%", track.getDisplayName());
                ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
                return;
            }
            plugin.sendMessage(player, Success.SESSION_ENDED);
            return;
        }

        if (track == null) {
            plugin.sendMessage(player, Error.NOT_NOW);
            return;
        }

        TimeTrialSession ttSession = new TimeTrialSession(Database.getPlayer(player.getUniqueId()), track);
        ttSession.updateScoreboard();
        TimeTrialController.timeTrialSessions.put(player.getUniqueId(), ttSession);
        plugin.sendMessage(player, Success.SESSION_STARTED, "%track%", track.getDisplayName());
        ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());

    }

    @Subcommand("delete")
    @CommandCompletion("@track")
    @CommandPermission("track.admin")
    public static void onDelete(Player player, Track track) {
        TrackDatabase.removeTrack(track);
        plugin.sendMessage(player, Success.REMOVED_TRACK, "%track%", track.getDisplayName());
    }


    @Subcommand("times")
    @CommandCompletion("@track <page>")
    public static void onTimes(CommandSender commandSender, Track track, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }
        Theme theme = TextUtilities.getTheme(commandSender);
        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= track.getTopList().size()) {
            plugin.sendMessage(commandSender, Error.PAGE_NOT_FOUND);
            return;
        }

        plugin.sendMessage(commandSender, Info.TRACK_TIMES_TITLE, "%track%", track.getDisplayName());
        for (int i = start; i < stop; i++) {
            if (i == track.getTopList().size()) {
                break;
            }
            TimeTrialFinish finish = track.getTopList().get(i);
            commandSender.sendMessage(TextUtilities.getTimesRow(String.valueOf(i + 1), finish.getPlayer().getName(), ApiUtilities.formatAsTime(finish.getTime()), theme));
        }

        int pageEnd = (int) Math.ceil(((double) track.getTopList().size()) / ((double) itemsPerPage));
        commandSender.sendMessage(TextUtilities.getPageSelector(commandSender, theme, pageStart, pageEnd, "/t times " + track.getCommandName()));
    }

    @Subcommand("mytimes")
    @CommandCompletion("@track <page>")
    public static void onMyTimes(Player player, Track track, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }

        var tPlayer = Database.getPlayer(player.getUniqueId());
        Theme theme = tPlayer.getTheme();
        List<TimeTrialFinish> allTimes = new ArrayList<>();
        if (track.getTimeTrialFinishes().containsKey(tPlayer)) {
            allTimes.addAll(track.getTimeTrialFinishes().get(tPlayer));
            allTimes.sort(new TimeTrialFinishComparator());
        }

        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= allTimes.size()) {
            plugin.sendMessage(player, Error.PAGE_NOT_FOUND);
            return;
        }

        plugin.sendMessage(player, Info.PLAYER_BEST_TIMES_TITLE, "%player%", player.getName(), "%track%", track.getDisplayName());

        for (int i = start; i < stop; i++) {
            if (i == allTimes.size()) {
                break;
            }
            TimeTrialFinish finish = allTimes.get(i);
            player.sendMessage(TextUtilities.getTimesRow(String.valueOf(i + 1), ApiUtilities.formatAsTime(finish.getTime()), ApiUtilities.niceDate(finish.getDate()), theme));
        }

        int pageEnd = (int) Math.ceil(((double) allTimes.size()) / ((double) itemsPerPage));
        player.sendMessage(TextUtilities.getPageSelector(player,theme, pageStart, pageEnd, "/t mytimes " + track.getCommandName()));
    }

    @Subcommand("alltimes")
    @CommandCompletion("@players <page>")
    public static void onAllTimes(Player player, @Optional String name, @Optional Integer pageStart) {
        if (pageStart == null) {
            pageStart = 1;
        }
        TPlayer tPlayer;
        if (name != null) {
            tPlayer = Database.getPlayer(name);
            if (tPlayer == null) {
                plugin.sendMessage(player, Error.PLAYER_NOT_FOUND);
                return;
            }
        } else {
            tPlayer = Database.getPlayer(player.getUniqueId());
        }

        List<TimeTrialFinish> allTimes = new ArrayList<>();
        var tracks = TrackDatabase.getOpenTracks();
        for (Track t : tracks) {
            if (t.getTimeTrialFinishes().containsKey(tPlayer)) {
                allTimes.addAll(t.getTimeTrialFinishes().get(tPlayer));
            }
        }
        allTimes.sort(new TimeTrialDateComparator());

        Theme theme = Database.getPlayer(player).getTheme();
        int itemsPerPage = TimingSystem.configuration.getTimesPageSize();
        int start = (pageStart * itemsPerPage) - itemsPerPage;
        int stop = pageStart * itemsPerPage;

        if (start >= allTimes.size()) {
            plugin.sendMessage(player, Error.PAGE_NOT_FOUND);
            return;
        }

        plugin.sendMessage(player, Info.PLAYER_RECENT_TIMES_TITLE, "%player%", tPlayer.getName());

        for (int i = start; i < stop; i++) {
            if (i == allTimes.size()) {
                break;
            }
            TimeTrialFinish finish = allTimes.get(i);
            player.sendMessage(TextUtilities.getTimesRow(String.valueOf(i + 1), ApiUtilities.formatAsTime(finish.getTime()), TrackDatabase.getTrackById(finish.getTrack()).get().getDisplayName(), ApiUtilities.niceDate(finish.getDate()), theme));
        }
        int pageEnd = (int) Math.ceil(((double) allTimes.size()) / ((double) itemsPerPage));
        player.sendMessage(TextUtilities.getPageSelector(player,theme, pageStart, pageEnd, "/t alltimes " + tPlayer.getName()));
    }

    @Subcommand("edit")
    @CommandCompletion("@track")
    @CommandPermission("track.admin")
    public static void onEdit(Player player, @Optional Track track) {
        if (track == null) {
            TimingSystem.playerEditingSession.remove(player.getUniqueId());
            plugin.sendMessage(player, Success.SESSION_ENDED);
            return;
        }
        TimingSystem.playerEditingSession.put(player.getUniqueId(), track);
        plugin.sendMessage(player, Success.SAVED);
    }

    @Subcommand("options")
    @CommandCompletion("@track options")
    @CommandPermission("track.admin")
    public static void onOptions(CommandSender commandSender, Track track, String options) {
        String newOptions = ApiUtilities.parseFlagChange(track.getOptions(), options);
        if (newOptions == null) {
            plugin.sendMessage(commandSender, Success.SAVED);
            return;
        }

        if (newOptions.length() == 0) {
            plugin.sendMessage(commandSender, Success.TRACK_OPTIONS_CLEARED);
        } else {
            plugin.sendMessage(commandSender, Success.TRACK_OPTIONS_NEW, "%options%", ApiUtilities.formatPermissions(newOptions.toCharArray()));
        }
        track.setOptions(newOptions);
    }

    @Subcommand("reload")
    @CommandPermission("track.admin")
    public static void onReload(CommandSender commandSender) {
        commandSender.sendMessage("§cYou are doing this on your own risk, everything might break!");
        Database.reload();
    }

    @Subcommand("deletebesttime")
    @CommandPermission("track.admin")
    @CommandCompletion("@track <playername>")
    public static void onDeleteBestTime(CommandSender commandSender, Track track, String name) {
        TPlayer TPlayer = Database.getPlayer(name);
        if (TPlayer == null) {
            plugin.sendMessage(commandSender, Error.PLAYER_NOT_FOUND);
            return;
        }

        TimeTrialFinish bestFinish = track.getBestFinish(TPlayer);
        if (bestFinish == null) {
            plugin.sendMessage(commandSender, Error.NOTHING_TO_REMOVE);
            return;
        }
        track.deleteBestFinish(TPlayer, bestFinish);
        plugin.sendMessage(commandSender, Success.REMOVED_BEST_FINISH, "%player%", TPlayer.getName(), "%track%", track.getDisplayName());
        LeaderboardManager.updateFastestTimeLeaderboard(track);
    }

    @Subcommand("deletealltimes")
    @CommandPermission("track.admin")
    @CommandCompletion("@track <player>")
    public static void onDeleteAllTimes(CommandSender commandSender, Track track, @Optional String playerName) {
        if (playerName != null) {
            TPlayer tPlayer = Database.getPlayer(playerName);
            if (tPlayer == null) {
                plugin.sendMessage(commandSender, Error.PLAYER_NOT_FOUND);
                return;
            }
            var message = plugin.getText(commandSender, Success.REMOVED_ALL_FINISHES).append(TextUtilities.success(" -> " + tPlayer.getNameDisplay(), TextUtilities.getTheme(commandSender)));
            commandSender.sendMessage(message);
            track.deleteAllFinishes(tPlayer);
            LeaderboardManager.updateFastestTimeLeaderboard(track);
            return;
        }
        track.deleteAllFinishes();
        plugin.sendMessage(commandSender, Success.REMOVED_ALL_FINISHES);
        LeaderboardManager.updateFastestTimeLeaderboard(track);

    }

    @Subcommand("updateleaderboards")
    @CommandPermission("track.admin")
    public static void onUpdateLeaderboards(Player player) {
        Bukkit.getScheduler().runTaskAsynchronously(TimingSystem.getPlugin(), LeaderboardManager::updateAllFastestTimeLeaderboard);
        plugin.sendMessage(player, Info.UPDATING_LEADERBOARDS);
    }

    @Subcommand("set")
    @CommandPermission("track.admin")
    public class Set extends BaseCommand {

        public static List<TrackRegion> restoreRegions = new ArrayList<>();
        public static List<TrackLocation> restoreLocations = new ArrayList<>();
        public static Track restoreTrack = null;

        @Subcommand("open")
        @CommandCompletion("true|false @track")
        public static void onOpen(Player player, boolean open, Track track) {
            track.setOpen(open);
            if (track.isOpen()) {
                plugin.sendMessage(player, Success.TRACK_NOW_OPEN);
            } else {
                plugin.sendMessage(player, Success.TRACK_NOW_CLOSED);
            }
        }

        @Subcommand("weight")
        @CommandCompletion("<value> @track")
        public static void onWeight(Player player, int weight, Track track) {
            track.setWeight(weight);
            plugin.sendMessage(player, Success.SAVED);
        }

        @Subcommand("tag")
        @CommandCompletion("@track +/- @trackTag")
        public static void onTag(CommandSender sender, Track track, String plusOrMinus, TrackTag tag) {

            if (!TrackTagManager.hasTag(tag)) {
                plugin.sendMessage(sender, Error.TAG_NOT_FOUND);
                return;
            }
            if (plusOrMinus.equalsIgnoreCase("-")) {
                if (track.removeTag(tag)) {
                    plugin.sendMessage(sender, Success.REMOVED);
                    return;
                }
                plugin.sendMessage(sender, Error.FAILED_TO_REMOVE_TAG);
                return;
            }

            if (track.createTag(tag)) {
                plugin.sendMessage(sender, Success.ADDED_TAG, "%tag%", tag.getValue());
                return;
            }

            plugin.sendMessage(sender, Error.FAILED_TO_ADD_TAG);
        }

        @Subcommand("type")
        @CommandCompletion("@trackType @track")
        public static void onType(Player player, Track.TrackType type, Track track) {
            track.setTrackType(type);
            plugin.sendMessage(player, Success.SAVED);
        }

        @Subcommand("mode")
        @CommandCompletion("@trackMode @track")
        public static void onMode(Player player, Track.TrackMode mode, Track track) {
            track.setMode(mode);
            plugin.sendMessage(player, Success.SAVED);
        }

        @Subcommand("spawn")
        @CommandCompletion("@track @region")
        public static void onSpawn(Player player, Track track, @Optional TrackRegion region) {
            if (region != null) {
                region.setSpawn(player.getLocation());
                plugin.sendMessage(player, Success.SAVED);
                return;
            }
            track.setSpawnLocation(player.getLocation());
            plugin.sendMessage(player, Success.SAVED);
        }

        @Subcommand("leaderboard")
        @CommandCompletion("@track <index>")
        public static void onLeaderboard(Player player, Track track, @Optional String index) {
            Location loc = player.getLocation();
            loc.setY(loc.getY() + 3);
            createOrUpdateTrackIndexLocation(track, TrackLocation.Type.LEADERBOARD, index, player, loc);
        }

        @Subcommand("name")
        @CommandCompletion("@track name")
        public static void onName(CommandSender commandSender, Track track, String name) {
            int maxLength = 25;
            if (name.length() > maxLength) {
                plugin.sendMessage(commandSender, Error.LENGTH_EXCEEDED, "%length%", String.valueOf(maxLength));
                return;
            }

            if (ApiUtilities.checkTrackName(name)) {
                plugin.sendMessage(commandSender, Error.TRACK_EXISTS);
                return;
            }

            if (!name.matches("[A-Za-zÅÄÖåäöØÆøæ0-9 ]+")) {
                plugin.sendMessage(commandSender, Error.NAME_FORMAT);
                return;
            }

            if (!TrackDatabase.trackNameAvailable(name)) {
                plugin.sendMessage(commandSender, Error.TRACK_EXISTS);
                return;
            }
            track.setName(name);
            plugin.sendMessage(commandSender, Success.SAVED);
            LeaderboardManager.updateFastestTimeLeaderboard(track);

        }

        @Subcommand("gui")
        @CommandCompletion("@track")
        public static void onGui(Player player, Track track) {
            var item = player.getInventory().getItemInMainHand();
            if (item.getItemMeta() == null) {
                plugin.sendMessage(player, Error.ITEM_NOT_FOUND);
                return;
            }
            track.setGuiItem(item);
            plugin.sendMessage(player, Success.SAVED);
        }

        @Subcommand("owner")
        @CommandCompletion("@track <player>")
        public static void onOwner(CommandSender commandSender, Track track, String name) {
            TPlayer TPlayer = Database.getPlayer(name);
            if (TPlayer == null) {
                plugin.sendMessage(commandSender, Error.PLAYER_NOT_FOUND);
                return;
            }
            track.setOwner(TPlayer);
            plugin.sendMessage(commandSender, Success.SAVED);
        }

        @Subcommand("startregion")
        @CommandCompletion("@track <index>")
        public static void onStartRegion(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.START, index, player);
        }

        @Subcommand("endregion")
        @CommandCompletion("@track <index>")
        public static void onEndRegion(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.END, index, player);
        }

        @Subcommand("pitregion")
        @CommandCompletion("@track <index>")
        public static void onPitRegion(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.PIT, index, player);
        }

        @Subcommand("resetregion")
        @CommandCompletion("@track <index>")
        public static void onResetRegion(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.RESET, index, player);
        }

        @Subcommand("inpit")
        @CommandCompletion("@track <index>")
        public static void onInPit(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.INPIT, index, player);
        }

        @Subcommand("lagstart")
        @CommandCompletion("@track <->")
        public static void onLagStart(Player player, Track track, @Optional String remove) {
            boolean toRemove = false;
            if (remove != null) {
                toRemove = getParsedRemoveFlag(remove);
            }

            if (toRemove) {
                var maybeRegion = track.getRegion(TrackRegion.RegionType.LAGSTART);
                if (maybeRegion.isPresent()) {
                    if (track.removeRegion(maybeRegion.get())) {
                        plugin.sendMessage(player, Success.REMOVED);
                    } else {
                        plugin.sendMessage(player, Error.NOTHING_TO_REMOVE);
                    }
                } else {
                    plugin.sendMessage(player, Error.FAILED_TO_REMOVE);
                }
                return;
            }
            if (createOrUpdateRegion(track, TrackRegion.RegionType.LAGSTART, player)) {
                plugin.sendMessage(player, Success.CREATED);
            }
        }

        @Subcommand("lagend")
        @CommandCompletion("@track <->")
        public static void onLagEnd(Player player, Track track, @Optional String remove) {
            boolean toRemove = false;
            if (remove != null) {
                toRemove = getParsedRemoveFlag(remove);
            }

            if (toRemove) {
                var maybeRegion = track.getRegion(TrackRegion.RegionType.LAGEND);
                if (maybeRegion.isPresent()) {
                    if (track.removeRegion(maybeRegion.get())) {
                        plugin.sendMessage(player, Success.REMOVED);
                    } else {
                        plugin.sendMessage(player, Error.NOTHING_TO_REMOVE);
                    }
                } else {
                    plugin.sendMessage(player, Error.FAILED_TO_REMOVE);
                }
                return;
            }
            if (createOrUpdateRegion(track, TrackRegion.RegionType.LAGEND, player)) {
                plugin.sendMessage(player, Success.CREATED);
            }
        }

        @Subcommand("grid")
        @CommandCompletion("@track <index>")
        public static void onGridLocation(Player player, Track track, @Optional String index) {
            createOrUpdateTrackIndexLocation(track, TrackLocation.Type.GRID, index, player, player.getLocation());
        }

        @Subcommand("qualygrid")
        @CommandCompletion("@track <index>")
        public static void onQualificationGridLocation(Player player, Track track, @Optional String index) {
            createOrUpdateTrackIndexLocation(track, TrackLocation.Type.QUALYGRID, index, player, player.getLocation());
        }

        @Subcommand("checkpoint")
        @CommandCompletion("@track <index>")
        public static void onCheckpoint(Player player, Track track, @Optional String index) {
            createOrUpdateIndexRegion(track, TrackRegion.RegionType.CHECKPOINT, index, player);
        }

        private static boolean createOrUpdateRegion(Track track, TrackRegion.RegionType regionType, Player player) {
            var maybeSelection = ApiUtilities.getSelection(player);
            if (maybeSelection.isEmpty()) {
                plugin.sendMessage(player, Error.SELECTION);
                return false;
            }
            var selection = maybeSelection.get();

            if (track.hasRegion(regionType)) {
                return track.updateRegion(regionType, selection, player.getLocation());
            } else {
                return track.createRegion(regionType, selection, player.getLocation());
            }
        }

        private static boolean createOrUpdateRegion(Track track, TrackRegion.RegionType regionType, int index, Player player) {
            var maybeSelection = ApiUtilities.getSelection(player);
            if (maybeSelection.isEmpty()) {
                plugin.sendMessage(player, Error.SELECTION);
                return false;
            }
            var selection = maybeSelection.get();

            if (track.hasRegion(regionType, index)) {
                return track.updateRegion(track.getRegion(regionType, index).get(), selection, player.getLocation());
            } else {
                return track.createRegion(regionType, index, selection, player.getLocation());
            }
        }

        private static void createOrUpdateTrackLocation(Track track, TrackLocation.Type type, int index, Location location) {
            if (track.hasTrackLocation(type, index)) {
                track.updateTrackLocation(track.getTrackLocation(type, index).get(), location);
            } else {
                track.createTrackLocation(type, index, location);
            }
        }

        private static void createOrUpdateTrackIndexLocation(Track track, TrackLocation.Type type, String index, Player player, Location location) {
            int locationIndex;
            boolean remove = false;
            if (index != null) {

                if (index.equalsIgnoreCase("-all")) {
                    var trackLocations = track.getTrackLocations(type);
                    restoreLocations = trackLocations;
                    trackLocations.forEach(track::removeTrackLocation);
                    restoreTrack = track;
                    plugin.sendMessage(player, Success.REMOVED_LOCATIONS, "%type%", type.name());
                    return;
                }

                if (index.equalsIgnoreCase("+all")) {
                    if (restoreLocations.isEmpty()) {
                        plugin.sendMessage(player, Error.NOTHING_TO_RESTORE);
                        return;
                    }

                    if (restoreTrack != track) {
                        plugin.sendMessage(player, Error.NOTHING_TO_RESTORE);
                        return;
                    }

                    for (TrackLocation restoreLocation : restoreLocations) {
                        var maybeLocation = track.getTrackLocation(restoreLocation.getLocationType(), restoreLocation.getIndex());
                        if (maybeLocation.isPresent()) {
                            continue;
                        }
                        track.createTrackLocation(restoreLocation.getLocationType(), restoreLocation.getIndex(), restoreLocation.getLocation());
                    }
                    plugin.sendMessage(player, Success.RESTORED_LOCATIONS);
                    return;
                }

                remove = getParsedRemoveFlag(index);
                if (getParsedIndex(index) == null) {
                    plugin.sendMessage(player, Error.NUMBER_FORMAT);
                    return;
                }
                locationIndex = getParsedIndex(index);

                if (locationIndex == 0) {
                    plugin.sendMessage(player, Error.NO_ZERO_INDEX);
                    return;
                }
            } else {
                if (type == TrackLocation.Type.GRID || type == TrackLocation.Type.QUALYGRID) {
                    locationIndex = track.getTrackLocations(type).size() + 1;
                } else {
                    locationIndex = 1;
                }
            }
            if (remove) {
                var maybeLocation = track.getTrackLocation(type, locationIndex);
                if (maybeLocation.isPresent()) {
                    if (track.removeTrackLocation(maybeLocation.get())) {
                        plugin.sendMessage(player, Success.REMOVED);
                    } else {
                        plugin.sendMessage(player, Error.FAILED_TO_REMOVE);
                    }
                } else {
                    plugin.sendMessage(player, Error.NOTHING_TO_REMOVE);
                }
                return;
            }
            createOrUpdateTrackLocation(track, type, locationIndex, location);
            plugin.sendMessage(player, Success.SAVED);

        }

        private static void createOrUpdateIndexRegion(Track track, TrackRegion.RegionType regionType, String index, Player player) {
            int regionIndex;
            boolean remove = false;
            if (index != null) {
                if (index.equalsIgnoreCase("-all")) {
                    var regions = track.getRegions(regionType);
                    regions.forEach(track::removeRegion);
                    plugin.sendMessage(player, Success.REMOVED_REGIONS, "%type%", regionType.name());
                    return;
                }

                remove = getParsedRemoveFlag(index);
                if (getParsedIndex(index) == null) {
                    plugin.sendMessage(player, Error.NUMBER_FORMAT);
                    return;
                }
                regionIndex = getParsedIndex(index);

                if (regionIndex == 0) {
                    plugin.sendMessage(player, Error.NO_ZERO_INDEX);
                    return;
                }
            } else {
                if (regionType == TrackRegion.RegionType.START || regionType == TrackRegion.RegionType.END || regionType == TrackRegion.RegionType.PIT) {
                    regionIndex = 1;
                } else {
                    regionIndex = track.getRegions(regionType).size() + 1;
                }
            }
            if (remove) {
                var maybeRegion = track.getRegion(regionType, regionIndex);
                if (maybeRegion.isPresent()) {
                    if (track.removeRegion(maybeRegion.get())) {
                        plugin.sendMessage(player, Success.REMOVED);
                    } else {
                        plugin.sendMessage(player, Error.FAILED_TO_REMOVE);
                    }
                } else {
                    plugin.sendMessage(player, Error.NOTHING_TO_REMOVE);
                }
                return;
            }
            if (createOrUpdateRegion(track, regionType, regionIndex, player)) {
                plugin.sendMessage(player, Success.SAVED);
            }
        }

        private static boolean getParsedRemoveFlag(String index) {
            return index.startsWith("-");
        }

        private static Integer getParsedIndex(String index) {
            if (index.startsWith("-")) {
                index = index.substring(1);
            } else if (index.startsWith("+")) {
                index = index.substring(1);
            }
            try {
                return Integer.parseInt(index);
            } catch (NumberFormatException exception) {
                return null;
            }
        }
    }
}