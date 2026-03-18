package io.velo.was.servlet;

public interface SessionConflictResolver {

    SessionRecord resolve(SessionRecord current, SessionRecord candidate);
}
