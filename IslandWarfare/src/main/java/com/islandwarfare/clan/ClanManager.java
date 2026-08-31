package com.islandwarfare.clan;

import com.islandwarfare.IslandWarfare;
import com.islandwarfare.config.ConfigManager;

import java.util.*;

public class ClanManager {

    private final IslandWarfare plugin;
    private final ConfigManager cfg;

    private final Map<UUID, Clan> clansById = new HashMap<>();
    private final Map<UUID, UUID> playerToClan = new HashMap<>();
    // player -> (clanId -> invite)
    private final Map<UUID, Map<UUID, ClanInvite>> pendingInvites = new HashMap<>();

    public ClanManager(IslandWarfare plugin) {
        this.plugin = plugin;
        this.cfg = plugin.getConfigManager();
    }

    public Collection<Clan> getClans() {
        return clansById.values();
    }

    public Clan getClanById(UUID id) {
        return clansById.get(id);
    }

    public Clan getClanByPlayer(UUID playerId) {
        UUID clanId = playerToClan.get(playerId);
        return clanId == null ? null : clansById.get(clanId);
    }

    public Clan getClanByName(String name) {
        for (Clan clan : clansById.values()) {
            if (clan.getName().equalsIgnoreCase(name)) return clan;
        }
        return null;
    }

    public Clan getClanByTag(String tag) {
        for (Clan clan : clansById.values()) {
            if (clan.getTag().equalsIgnoreCase(tag)) return clan;
        }
        return null;
    }

    public boolean isNameTaken(String name) {
        return getClanByName(name) != null;
    }

    public boolean isTagTaken(String tag) {
        return getClanByTag(tag) != null;
    }

    /**
     * Registers a brand-new clan in memory (island assignment happens separately
     * in IslandManager, then linked via clan.setIslandId).
     */
    public Clan createClan(UUID id, String name, String tag, UUID leaderUuid, String leaderName) {
        Clan clan = new Clan(id, name, tag, leaderUuid);
        clan.setBalance(cfg.getStartingBalance());
        ClanMember leaderMember = new ClanMember(leaderUuid, leaderName, ClanRole.LEADER);
        applyDefaultPermissions(leaderMember);
        clan.addMember(leaderMember);
        clansById.put(id, clan);
        playerToClan.put(leaderUuid, id);
        return clan;
    }

    /** Used by storage loader to re-register a fully built clan on startup. */
    public void registerLoadedClan(Clan clan) {
        clansById.put(clan.getId(), clan);
        for (UUID member : clan.getMembers().keySet()) {
            playerToClan.put(member, clan.getId());
        }
    }

    public void disbandClan(UUID clanId) {
        Clan clan = clansById.remove(clanId);
        if (clan == null) return;
        for (UUID member : clan.getMembers().keySet()) {
            playerToClan.remove(member);
        }
    }

    public void addMember(Clan clan, UUID uuid, String name) {
        ClanMember member = new ClanMember(uuid, name, ClanRole.MEMBER);
        applyDefaultPermissions(member);
        clan.addMember(member);
        playerToClan.put(uuid, clan.getId());
    }

    public void removeMember(Clan clan, UUID uuid) {
        clan.removeMember(uuid);
        playerToClan.remove(uuid);
    }

    /**
     * Changes a member's role and resets their island permissions to that
     * role's configured defaults (clan.default-permissions.<ROLE> in
     * config.yml). This is the real, wired consumer of that config section -
     * promoting/demoting a member actually changes what they can do on the
     * island, not just their displayed role label.
     */
    public void setRole(ClanMember member, ClanRole newRole) {
        member.setRole(newRole);
        applyDefaultPermissions(member);
    }

    private void applyDefaultPermissions(ClanMember member) {
        member.getPermissions().clear();
        for (String flagName : cfg.getDefaultPermissions(member.getRole().name())) {
            try {
                member.grant(IslandFlag.valueOf(flagName));
            } catch (IllegalArgumentException ignored) {
                // Invalid flag name in config - skip it rather than fail the whole clan/member creation.
            }
        }
    }

    // ------------- invites -------------

    public void invite(Clan clan, UUID target) {
        pendingInvites.computeIfAbsent(target, k -> new HashMap<>()).put(clan.getId(), new ClanInvite(clan.getId()));
    }

    public boolean hasInvite(UUID target, UUID clanId) {
        Map<UUID, ClanInvite> map = pendingInvites.get(target);
        return map != null && map.containsKey(clanId);
    }

    public void clearInvite(UUID target, UUID clanId) {
        Map<UUID, ClanInvite> map = pendingInvites.get(target);
        if (map != null) map.remove(clanId);
    }

    public void clearAllInvites(UUID target) {
        pendingInvites.remove(target);
    }

    public Set<UUID> getPlayers() {
        return playerToClan.keySet();
    }

    public int getClanCount() {
        return clansById.size();
    }
}
