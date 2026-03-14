package io.velo.was.jsp.reload;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JspReloadManagerTest {

    @Test
    void developmentModeEnabled() {
        JspReloadManager manager = new JspReloadManager(true);
        assertTrue(manager.isEnabled());
    }

    @Test
    void productionModeDisabled() {
        JspReloadManager manager = new JspReloadManager(false);
        assertFalse(manager.isEnabled());
    }

    @Test
    void needsRecompileWhenNotTracked() {
        JspReloadManager manager = new JspReloadManager(true);
        assertTrue(manager.needsRecompile("/test.jsp", 1000L));
    }

    @Test
    void noRecompileWhenUnchanged() {
        JspReloadManager manager = new JspReloadManager(true);
        manager.recordModificationTime("/test.jsp", 1000L);
        assertFalse(manager.needsRecompile("/test.jsp", 1000L));
    }

    @Test
    void recompileWhenModified() {
        JspReloadManager manager = new JspReloadManager(true);
        manager.recordModificationTime("/test.jsp", 1000L);
        assertTrue(manager.needsRecompile("/test.jsp", 2000L));
    }

    @Test
    void noRecompileInProductionMode() {
        JspReloadManager manager = new JspReloadManager(false);
        assertFalse(manager.needsRecompile("/test.jsp", 2000L));
    }

    @Test
    void removeTracking() {
        JspReloadManager manager = new JspReloadManager(true);
        manager.recordModificationTime("/test.jsp", 1000L);
        assertEquals(1, manager.trackedCount());
        manager.remove("/test.jsp");
        assertEquals(0, manager.trackedCount());
    }

    @Test
    void clearAll() {
        JspReloadManager manager = new JspReloadManager(true);
        manager.recordModificationTime("/a.jsp", 1000L);
        manager.recordModificationTime("/b.jsp", 2000L);
        assertEquals(2, manager.trackedCount());
        manager.clear();
        assertEquals(0, manager.trackedCount());
    }
}
