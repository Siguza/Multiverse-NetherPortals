package com.onarandombox.MultiverseNetherPortals.listeners;

import com.onarandombox.MultiverseCore.MultiverseCore;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.utils.PermissionTools;
import com.onarandombox.MultiverseNetherPortals.MultiverseNetherPortals;
import com.onarandombox.MultiverseNetherPortals.enums.PortalType;
import com.onarandombox.MultiverseNetherPortals.utils.MVLinkChecker;
import com.onarandombox.MultiverseNetherPortals.utils.MVNameChecker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.util.Vector;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

/**
 * Multiverse 2
 *
 * @author fernferret
 */
public class MVNPEntityListener extends EntityListener {

    private MultiverseNetherPortals plugin;
    private MVNameChecker nameChecker;
    private MVLinkChecker linkChecker;
    private MVWorldManager worldManager;
    private PermissionTools pt;
    private int cooldown = 250;
    private Map<String, Date> playerErrors;

    public MVNPEntityListener(MultiverseNetherPortals plugin) {
        this.plugin = plugin;
        this.nameChecker = new MVNameChecker(this.plugin);
        this.linkChecker = new MVLinkChecker(this.plugin);
        this.worldManager = this.plugin.getCore().getMVWorldManager();
        this.pt = new PermissionTools(this.plugin.getCore());
        this.playerErrors = new HashMap<String, Date>();
    }

    protected void shootPlayer(Player p, Block block, PortalType type) {
        this.playerErrors.put(p.getName(), new Date());
        double myconst = 2;
        double newVecX = 0;
        double newVecZ = 0;
        // Determine portal axis:
        BlockFace face = p.getLocation().getBlock().getFace(block);
        if (block.getRelative(BlockFace.EAST).getType() == Material.PORTAL || block.getRelative(BlockFace.WEST).getType() == Material.PORTAL) {
            System.out.println("East/West");
            if (p.getLocation().getX() < block.getLocation().getX()) {
                newVecX = -1 * myconst;
            } else {
                newVecX = 1 * myconst;
            }
        } else {
            //NOrth/South
            System.out.println("N/S");
            if (p.getLocation().getZ() < block.getLocation().getZ()) {
                newVecZ = -1 * myconst;
            } else {
                newVecZ = 1 * myconst;
            }
        }
        p.teleport(p.getLocation().clone().add(newVecX,.2,newVecZ));
        System.out.println(new Vector(newVecX, .6, newVecZ));
        p.setVelocity(new Vector(newVecX, .6, newVecZ));
    }

    @Override
    public void onEntityPortalEnter(EntityPortalEnterEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        Player p = (Player) event.getEntity();
        if (this.playerErrors.containsKey(p.getName())) {
            Date lastTry = this.playerErrors.get(p.getName());
            if (lastTry.getTime() + this.cooldown > new Date().getTime()) {
                return;
            }
            this.playerErrors.remove(p.getName());
        }

        PortalType type = PortalType.END; //we are too lazy to check if it's this one
        if (event.getLocation().getBlock().getType() == Material.PORTAL) {
            type = PortalType.NETHER;
        }

        String currentWorld = event.getLocation().getWorld().getName();
        String linkedWorld = this.plugin.getWorldLink(event.getLocation().getWorld().getName(), type);
        Location currentLocation = event.getLocation();
        Location eventLocation = event.getLocation().clone();
        Location toLocation = null;

        if (linkedWorld != null) {
            toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, linkedWorld, p);
        } else if (this.nameChecker.isValidNetherName(currentWorld)) {
            toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNormalName(currentWorld), p);
        } else {
            toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNetherName(currentWorld), p);
        }

        if (toLocation == null) {
            this.shootPlayer(p, eventLocation.getBlock(), type);
            p.sendMessage("This portal goes nowhere!");
            return;
        }
        MultiverseWorld fromWorld = this.worldManager.getMVWorld(p.getLocation().getWorld().getName());
        MultiverseWorld toWorld = this.worldManager.getMVWorld(toLocation.getWorld().getName());
        if (fromWorld.getCBWorld().equals(toWorld.getCBWorld())) {
            // The player is Portaling to the same world.
            this.plugin.log(Level.FINER, "Player '" + p.getName() + "' is portaling to the same world.");
            return;
        }
        if (!pt.playerHasMoneyToEnter(fromWorld, toWorld, p, p, false)) {
            System.out.println("BOOM");
            this.shootPlayer(p, eventLocation.getBlock(), type);
            this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was DENIED ACCESS to '" + toWorld.getCBWorld().getName() +
                    "' because they don't have the FUNDS required to enter.");
            return;
        }
        if (MultiverseCore.EnforceAccess) {
            if (!pt.playerCanGoFromTo(fromWorld, toWorld, p, p)) {
                this.shootPlayer(p, eventLocation.getBlock(), type);
                this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was DENIED ACCESS to '" + toWorld.getCBWorld().getName() +
                        "' because they don't have: multiverse.access." + toWorld.getCBWorld().getName());
            }
        } else {
            this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was allowed to go to '" + toWorld.getCBWorld().getName() + "' because enforceaccess is off.");
        }
    }
}