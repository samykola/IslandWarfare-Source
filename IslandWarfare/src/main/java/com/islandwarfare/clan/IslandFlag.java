package com.islandwarfare.clan;

/**
 * Fine grained permission flags that can be granted to an individual clan
 * member on top of their {@link ClanRole}. Leaders/Officers configure these
 * per-player through /clan permissions.
 */
public enum IslandFlag {
    BUILD,      // place blocks
    BREAK,      // break blocks
    CONTAINER,  // open chests / containers
    PICKUP,     // pick up dropped items
    USE,        // use doors, trapdoors, buttons, levers
    INTERACT    // general right-click interactions
}
