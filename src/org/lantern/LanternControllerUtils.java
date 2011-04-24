package org.lantern;

/**
 * Utility methods for the controller.
 */
public class LanternControllerUtils {

    /**
     * Returns whether or not the given ID is a lantern ID.
     * 
     * @param id The ID to check.
     * @return <code>true</code> if it's a Lantern ID, otherwise 
     * <code>false</code>.
     */
    public static boolean isLantern(final String id) {
        return id.contains("@gmail.com/-la-");
    }

}
