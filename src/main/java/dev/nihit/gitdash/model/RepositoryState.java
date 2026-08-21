package dev.nihit.gitdash.model;

public enum RepositoryState {
    CLEAN, DIRTY, CONFLICT, INVALID;

    public String display() { return name().toLowerCase(); }
}
