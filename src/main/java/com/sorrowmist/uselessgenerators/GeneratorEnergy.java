package com.sorrowmist.uselessgenerators;

import com.sorrowmist.uselesscore.api.logistics.LongEnergyHandler;

/** Long storage with one shared per-tick output budget for every face and caller. */
public final class GeneratorEnergy implements LongEnergyHandler {
    private final long rate;
    private final long capacity;
    private long stored;
    private long budget;
    private long inputBudget;
    public GeneratorEnergy(GeneratorTier tier) { this(tier.perTick(), tier.capacity()); }
    public GeneratorEnergy(long rate, long capacity) {
        if (rate <= 0 || capacity < rate) throw new IllegalArgumentException("Invalid generator capacity/rate");
        this.rate = rate;
        this.capacity = capacity;
    }
    public long rate() { return rate; }
    public void tick(long generated) {
        stored += Math.min(Math.max(0, generated), Math.min(rate, capacity() - stored));
        budget = rate;
        inputBudget = rate;
    }
    public long generationRoom() { return Math.min(rate, capacity() - stored); }
    public void restore(long amount) {
        stored = Math.max(0, Math.min(amount, capacity()));
        budget = 0;
        inputBudget = 0;
    }
    @Override public long extract(long amount, boolean simulate) {
        long extracted = Math.min(Math.max(0, amount), Math.min(stored, budget));
        if (!simulate) { stored -= extracted; budget -= extracted; }
        return extracted;
    }
    @Override public long receive(long amount, boolean simulate) {
        long accepted = Math.min(Math.max(0, amount), Math.min(inputBudget, capacity() - stored));
        if (!simulate) { stored += accepted; inputBudget -= accepted; }
        return accepted;
    }
    @Override public long stored() { return stored; }
    @Override public long capacity() { return capacity; }
}
