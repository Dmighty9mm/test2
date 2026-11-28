package net.plebmc.shop;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.context.ImmutableContextSet;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.DisplayNameNode;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.WeightNode;
import net.luckperms.api.track.Track;

/**
 * The panel's Ranks page, through LuckPerms on the proxy: reports every group
 * (prefix, weight, parents, permissions) and applies owners' changes. Only
 * loaded when LuckPerms is installed on the proxy, so PlebioAPI runs fine
 * without it. Changes are pushed to the other servers with LuckPerms'
 * messaging service when one is set up.
 */
final class LuckPermsBridge {

	private static final long WAIT = 10;
	private final LuckPerms lp;

	private LuckPermsBridge(LuckPerms lp) {
		this.lp = lp;
	}

	/** The bridge, or null when LuckPerms isn't running here. */
	static LuckPermsBridge find() {
		try {
			return new LuckPermsBridge(LuckPermsProvider.get());
		} catch (Throwable t) {
			return null;
		}
	}

	/** Every group and track, for the panel. */
	Map<String, Object> report() {
		Map<String, Object> m = new LinkedHashMap<>();
		List<Map<String, Object>> groups = new ArrayList<>();
		try {
			lp.getGroupManager().loadAllGroups().get(WAIT, TimeUnit.SECONDS);
		} catch (Exception e) {
			// use what is loaded
		}
		for (Group g : lp.getGroupManager().getLoadedGroups()) {
			Map<String, Object> gm = new LinkedHashMap<>();
			gm.put("name", g.getName());
			gm.put("display", g.getDisplayName() == null ? "" : g.getDisplayName());
			gm.put("weight", g.getWeight().orElse(0));
			gm.put("prefix", best(g.getNodes(NodeType.PREFIX)));
			gm.put("suffix", bestSuffix(g.getNodes(NodeType.SUFFIX)));
			List<String> parents = new ArrayList<>();
			for (InheritanceNode n : g.getNodes(NodeType.INHERITANCE)) {
				parents.add(n.getGroupName());
			}
			gm.put("parents", parents);
			List<Map<String, Object>> perms = new ArrayList<>();
			for (PermissionNode n : g.getNodes(NodeType.PERMISSION)) {
				if (perms.size() >= 1000) {
					break;
				}
				Map<String, Object> pm = new LinkedHashMap<>();
				pm.put("node", n.getPermission());
				pm.put("value", n.getValue());
				pm.put("server", first(n.getContexts(), "server"));
				pm.put("world", first(n.getContexts(), "world"));
				pm.put("expiry", n.getExpiry() == null ? 0 : n.getExpiry().getEpochSecond());
				perms.add(pm);
			}
			gm.put("perms", perms);
			groups.add(gm);
		}
		List<Map<String, Object>> tracks = new ArrayList<>();
		try {
			lp.getTrackManager().loadAllTracks().get(WAIT, TimeUnit.SECONDS);
			for (Track t : lp.getTrackManager().getLoadedTracks()) {
				Map<String, Object> tm = new LinkedHashMap<>();
				tm.put("name", t.getName());
				tm.put("groups", new ArrayList<>(t.getGroups()));
				tracks.add(tm);
			}
		} catch (Exception e) {
			// no tracks
		}
		m.put("ok", true);
		m.put("groups", groups);
		m.put("tracks", tracks);
		return m;
	}

	/** The primary group of an online player, or null. */
	String rankOf(UUID id) {
		User u = lp.getUserManager().getUser(id);
		return u == null ? null : u.getPrimaryGroup();
	}

	private static String best(Collection<PrefixNode> nodes) {
		PrefixNode b = null;
		for (PrefixNode n : nodes) {
			if (b == null || n.getPriority() > b.getPriority()) {
				b = n;
			}
		}
		return b == null ? "" : b.getMetaValue();
	}

	private static String bestSuffix(Collection<SuffixNode> nodes) {
		SuffixNode b = null;
		for (SuffixNode n : nodes) {
			if (b == null || n.getPriority() > b.getPriority()) {
				b = n;
			}
		}
		return b == null ? "" : b.getMetaValue();
	}

	private static String first(ImmutableContextSet c, String key) {
		Optional<String> v = c.getAnyValue(key);
		return v.orElse("");
	}

	// ==================== changes from the panel ====================

	/**
	 * Applies one change: target is the group (or the player for "player"),
	 * args as the panel queued them. Returns what happened, "!" first on failure.
	 */
	String apply(String target, List<String> args) throws Exception {
		if (args.isEmpty()) {
			return "!nothing to do";
		}
		String op = args.get(0);
		if ("player".equals(op)) {
			return player(target, args.size() > 1 ? args.get(1) : "set", args.size() > 2 ? args.get(2) : "");
		}
		String name = target.toLowerCase();
		if ("create".equals(op)) {
			if (lp.getGroupManager().loadGroup(name).get(WAIT, TimeUnit.SECONDS).isPresent()) {
				return "!" + name + " already exists";
			}
			lp.getGroupManager().createAndLoadGroup(name).get(WAIT, TimeUnit.SECONDS);
			push();
			return "created " + name;
		}
		Optional<Group> og = lp.getGroupManager().loadGroup(name).get(WAIT, TimeUnit.SECONDS);
		if (!og.isPresent()) {
			return "!no rank called " + name;
		}
		Group g = og.get();
		if ("delete".equals(op)) {
			lp.getGroupManager().deleteGroup(g).get(WAIT, TimeUnit.SECONDS);
			push();
			return "deleted " + name;
		}
		String a1 = args.size() > 1 ? args.get(1) : "";
		String out;
		switch (op) {
		case "prefix": {
			int pri = g.getWeight().orElse(0);
			g.data().clear(NodeType.PREFIX::matches);
			if (!a1.isEmpty()) {
				g.data().add(PrefixNode.builder(a1, pri).build());
			}
			out = a1.isEmpty() ? "prefix removed" : "prefix set";
			break;
		}
		case "suffix": {
			int pri = g.getWeight().orElse(0);
			g.data().clear(NodeType.SUFFIX::matches);
			if (!a1.isEmpty()) {
				g.data().add(SuffixNode.builder(a1, pri).build());
			}
			out = a1.isEmpty() ? "suffix removed" : "suffix set";
			break;
		}
		case "display":
			g.data().clear(NodeType.DISPLAY_NAME::matches);
			if (!a1.isEmpty()) {
				g.data().add(DisplayNameNode.builder(a1).build());
			}
			out = "display name set";
			break;
		case "weight": {
			int w = Integer.parseInt(a1);
			g.data().clear(NodeType.WEIGHT::matches);
			g.data().add(WeightNode.builder(w).build());
			out = "weight " + w;
			break;
		}
		case "perm":
		case "unperm": {
			String server = args.size() > 3 ? args.get(3) : "";
			if ("perm".equals(op)) {
				boolean value = !(args.size() > 2 && "false".equalsIgnoreCase(args.get(2)));
				g.data().clear(n -> n instanceof PermissionNode && ((PermissionNode) n).getPermission().equalsIgnoreCase(a1)
						&& first(n.getContexts(), "server").equalsIgnoreCase(server));
				PermissionNode.Builder b = PermissionNode.builder(a1).value(value);
				if (!server.isEmpty()) {
					b.withContext("server", server);
				}
				g.data().add(b.build());
				out = (value ? "allowed " : "denied ") + a1 + (server.isEmpty() ? "" : " on " + server);
			} else {
				g.data().clear(n -> n instanceof PermissionNode && ((PermissionNode) n).getPermission().equalsIgnoreCase(a1)
						&& first(n.getContexts(), "server").equalsIgnoreCase(server));
				out = "removed " + a1;
			}
			break;
		}
		case "parent":
			if (!lp.getGroupManager().loadGroup(a1).get(WAIT, TimeUnit.SECONDS).isPresent()) {
				return "!no rank called " + a1;
			}
			g.data().add(InheritanceNode.builder(a1).build());
			out = "inherits " + a1;
			break;
		case "unparent":
			g.data().remove(InheritanceNode.builder(a1).build());
			out = "no longer inherits " + a1;
			break;
		default:
			return "!unknown rank change " + op;
		}
		lp.getGroupManager().saveGroup(g).get(WAIT, TimeUnit.SECONDS);
		push();
		return out;
	}

	private String player(String name, String mode, String group) throws Exception {
		UUID id = lp.getUserManager().lookupUniqueId(name).get(WAIT, TimeUnit.SECONDS);
		if (id == null) {
			return "!LuckPerms doesn't know " + name + " (they need to join once)";
		}
		if (!lp.getGroupManager().loadGroup(group).get(WAIT, TimeUnit.SECONDS).isPresent()) {
			return "!no rank called " + group;
		}
		User u = lp.getUserManager().loadUser(id).get(WAIT, TimeUnit.SECONDS);
		Node node = InheritanceNode.builder(group).build();
		String out;
		if ("set".equals(mode)) {
			u.data().clear(NodeType.INHERITANCE::matches);
			u.data().add(node);
			u.setPrimaryGroup(group);
			out = name + " is now " + group;
		} else if ("add".equals(mode)) {
			u.data().add(node);
			out = name + " got " + group;
		} else {
			u.data().remove(node);
			out = name + " lost " + group;
		}
		lp.getUserManager().saveUser(u).get(WAIT, TimeUnit.SECONDS);
		lp.getMessagingService().ifPresent(m -> m.pushUserUpdate(u));
		return out;
	}

	/** Carries a player's ranks and permissions over to their new name's player. */
	String move(UUID from, UUID to, String toName) throws Exception {
		User a = lp.getUserManager().loadUser(from).get(WAIT, TimeUnit.SECONDS);
		User b = lp.getUserManager().loadUser(to, toName).get(WAIT, TimeUnit.SECONDS);
		int n = 0;
		for (Node node : a.data().toCollection()) {
			b.data().add(node);
			n++;
		}
		String primary = a.getPrimaryGroup();
		if (primary != null && !primary.isEmpty()) {
			b.setPrimaryGroup(primary);
		}
		a.data().clear();
		lp.getUserManager().saveUser(b).get(WAIT, TimeUnit.SECONDS);
		lp.getUserManager().saveUser(a).get(WAIT, TimeUnit.SECONDS);
		lp.getMessagingService().ifPresent(m -> {
			m.pushUserUpdate(a);
			m.pushUserUpdate(b);
		});
		return n + " rank and permission entries";
	}

	/** Tells the other servers (through LuckPerms' messaging service) to reload. */
	private void push() {
		lp.getMessagingService().ifPresent(m -> m.pushUpdate());
	}
}
