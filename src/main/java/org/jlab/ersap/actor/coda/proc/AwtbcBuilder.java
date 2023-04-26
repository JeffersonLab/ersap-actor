package org.jlab.ersap.actor.coda.proc;

public class AwtbcBuilder {
    private int n;
    private double t;
    private boolean c;

    public AwtbcBuilder setN(int n) {
        this.n = n;
        return this;
    }

    public AwtbcBuilder setT(double t) {
        this.t = t;
        return this;
    }

    public AwtbcBuilder setC(boolean c) {
        this.c = c;
        return this;
    }

    public Awtbc createAwtbc() {
        return new Awtbc(n, t, c);
    }
}