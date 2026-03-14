package io.velo.was.jsp.reload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks JSP file modification times and determines when recompilation is needed.
 * Active only in development mode.
 */
public class JspReloadManager {

    private static final Logger log = LoggerFactory.getLogger(JspReloadManager.class);

    private final boolean enabled;
    private final Map<String, Long> lastModifiedMap = new ConcurrentHashMap<>();

    public JspReloadManager(boolean developmentMode) {
        this.enabled = developmentMode;
    }

    /**
     * Records the last known modification time for a JSP file.
     */
    public void recordModificationTime(String jspPath, long lastModified) {
        lastModifiedMap.put(jspPath, lastModified);
    }

    /**
     * Checks if the given JSP has been modified since last compilation.
     * Returns true if recompilation is needed.
     */
    public boolean needsRecompile(String jspPath, long currentLastModified) {
        if (!enabled) return false;

        Long recorded = lastModifiedMap.get(jspPath);
        if (recorded == null) {
            return true; // Not yet compiled
        }
        boolean modified = currentLastModified > recorded;
        if (modified) {
            log.debug("JSP change detected: {} (recorded={}, current={})", jspPath, recorded, currentLastModified);
        }
        return modified;
    }

    /**
     * Removes tracking for a specific JSP.
     */
    public void remove(String jspPath) {
        lastModifiedMap.remove(jspPath);
    }

    /**
     * Clears all tracked modification times.
     */
    public void clear() {
        lastModifiedMap.clear();
    }

    /**
     * Returns number of tracked JSP files.
     */
    public int trackedCount() {
        return lastModifiedMap.size();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
