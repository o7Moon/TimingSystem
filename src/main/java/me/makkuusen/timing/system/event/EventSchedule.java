package me.makkuusen.timing.system.event;

import lombok.Getter;
import me.makkuusen.timing.system.round.Round;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Getter
public class EventSchedule {
    private List<Round> rounds = new ArrayList<>();
    private Integer currentRound = null;

    public EventSchedule() {

    }

    public boolean start(Event event){
        if (rounds.size() > 0){
            event.setState(Event.EventState.RUNNING);
            currentRound = 1;
            return true;
        }
        return false;
    }

    public void addRound(Round round){
        rounds.add(round);
    }

    public void removeRound(Round round) {
        rounds.remove(round);
    }

    public Integer getCurrentRound(){
        if (currentRound != null) {
            return currentRound;
        }
        return null;
    }

    public void nextRound(){
        currentRound++;
    }

    public boolean isLastRound(){
        return currentRound >= rounds.size();
    }

    public Optional<Round> getRound(int index){
        if (index > 0 && index <= rounds.size()) {
            return Optional.of(rounds.get(index - 1));
        }
        return Optional.empty();
    }

    public Optional<Round> getRound(){
        return Optional.of(rounds.get(currentRound));
    }

    public Optional<Round> getNextRound(){
        return Optional.of(rounds.get(currentRound++));
    }


    public List<String> getHeatList(Event event) {
        List<String> message = new ArrayList<>();
        message.add("§2--- Heats for §a" + event.getDisplayName() + " §2---");
        message.addAll(listHeats());
        return message;
    }

    public List<String> listHeats() {
        List<String> message = new ArrayList<>();

        for (Round round : rounds) {
            round.getHeats().stream().forEach(heat -> message.add("§a - " + heat.getName()));
        }
        return message;
    }


}
