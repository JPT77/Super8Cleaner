package de.jpt.super8;
public class Filter {

    private final String name;

    public Filter(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}