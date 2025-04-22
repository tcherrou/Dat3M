package com.dat3m.dartagnan.configuration;

public enum IntervalOptions implements OptionInterface{
    NAIVE,PATTERSON;

    public static IntervalOptions getDefault() {
        return NAIVE;
    }

}
