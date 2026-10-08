package dev.skypilot.mod.core;

import java.util.*;

/** Per-window memory of observations, never guesses an unseen card. */
public final class ExperimentMemory {
    private final Map<Integer,String> cards = new LinkedHashMap<>();
    private final Map<Integer,Integer> numbers = new HashMap<>();
    private final List<Integer> sequence = new ArrayList<>();
    private int lit = -1;
    public void reveal(int slot, String identity) { if (!identity.isBlank()) cards.put(slot, identity); }
    public String card(int slot) { return cards.get(slot); }
    public int pair(int slot) {
        String key=cards.get(slot); if(key==null)return -1;
        return cards.entrySet().stream().filter(e->e.getKey()!=slot && e.getValue().equals(key)).mapToInt(Map.Entry::getKey).findFirst().orElse(-1);
    }
    public void number(int slot,int number) { if(number>0&&number<=49)numbers.put(slot,number); }
    public Integer number(int slot) {return numbers.get(slot);}
    public void startRound(){numbers.clear();sequence.clear();lit=-1;}
    public void flash(int slot){if(slot>=0&&slot!=lit)sequence.add(slot);lit=slot;}
    public List<Integer> sequence(){return List.copyOf(sequence);}
    public int known(){return cards.size();}
}
