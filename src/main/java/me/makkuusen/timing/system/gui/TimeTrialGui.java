package me.makkuusen.timing.system.gui;

import me.makkuusen.timing.system.ApiUtilities;
import me.makkuusen.timing.system.TPlayer;
import me.makkuusen.timing.system.theme.Text;
import me.makkuusen.timing.system.theme.messages.Error;
import me.makkuusen.timing.system.theme.messages.Gui;
import me.makkuusen.timing.system.track.Track;
import me.makkuusen.timing.system.track.TrackDatabase;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

public class TimeTrialGui extends TrackPageGui {

    public TimeTrialGui(TPlayer tPlayer) {
        super(tPlayer, Text.get(tPlayer.getPlayer(), Gui.TRACKS_TITLE));
    }

    public TimeTrialGui(TPlayer tPlayer, Component title) {
        super(tPlayer, title);
    }

    public List<Track> getTracks() {
        return TrackDatabase.getOpenTracks();
    }

    @Override
    public GuiButton getTrackButton(Player player, Track track) {
        var button = new GuiButton(track.getGuiItem(player.getUniqueId()));
        button.setAction(() -> {
            if (!track.getSpawnLocation().isWorldLoaded()) {
                Text.send(player, Error.WORLD_NOT_LOADED);
                return;
            }
            ApiUtilities.teleportPlayerAndSpawnBoat(player, track, track.getSpawnLocation());
            player.closeInventory();
        });
        return button;
    }
}
