package com.onarandombox.MultiverseNetherPortals.listeners;

import com.onarandombox.MultiverseCore.api.LocationManipulation;
import com.onarandombox.MultiverseCore.api.MVWorldManager;
import com.onarandombox.MultiverseCore.api.MultiverseMessaging;
import com.onarandombox.MultiverseCore.api.MultiverseWorld;
import com.onarandombox.MultiverseCore.event.MVPlayerTouchedPortalEvent;
import com.onarandombox.MultiverseCore.utils.PermissionTools;
import com.onarandombox.MultiverseNetherPortals.MultiverseNetherPortals;
import com.onarandombox.MultiverseNetherPortals.enums.PortalType;
import com.onarandombox.MultiverseNetherPortals.utils.MVLinkChecker;
import com.onarandombox.MultiverseNetherPortals.utils.MVNameChecker;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.util.Vector;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class MVNPEntityListener implements Listener
{
    private MultiverseNetherPortals plugin;
    private MVNameChecker nameChecker;
    private MVLinkChecker linkChecker;
    private MVWorldManager worldManager;
    private PermissionTools pt;
    private int cooldown = 250;
    private MultiverseMessaging messaging;
    private Map<String, Date> playerErrors;
    private Map<String, Location> eventRecord;
    private LocationManipulation locationManipulation;
    // This hash map will track players most recent portal touch.
    // we can use this cache to avoid a TON of unrequired calls to the
    // On entity portal touch calculations.

    public MVNPEntityListener(MultiverseNetherPortals plugin)
    {
        this.plugin = plugin;
        this.nameChecker = new MVNameChecker(this.plugin);
        this.linkChecker = new MVLinkChecker(this.plugin);
        this.worldManager = this.plugin.getCore().getMVWorldManager();
        this.pt = new PermissionTools(this.plugin.getCore());
        this.playerErrors = new HashMap<String, Date>();
        this.eventRecord = new HashMap<String, Location>();
        this.messaging = this.plugin.getCore().getMessaging();
        this.locationManipulation = this.plugin.getCore().getLocationManipulation();

    }
    
    protected void shootEntity(Entity e, Block block, PortalType type)
    {
        if(!plugin.isUsingBounceBack())
        {
            this.plugin.log(Level.FINEST, "You said not to use bounce back so the entity is free to walk into portal!");
            return;
        }
        if(e instanceof Player)
        {
            this.playerErrors.put(((Player)e).getName(), new Date());
        }
        double myconst = 2;
        double newVecX = 0;
        double newVecZ = 0;
        // Determine portal axis:
        BlockFace face = e.getLocation().getBlock().getFace(block);
        if(block.getRelative(BlockFace.EAST).getType() == Material.PORTAL || block.getRelative(BlockFace.WEST).getType() == Material.PORTAL)
        {
            this.plugin.log(Level.FINER, "Found Portal: East/West");
            if(e.getLocation().getX() < block.getLocation().getX())
            {
                newVecX = -1 * myconst;
            }
            else
            {
                newVecX = 1 * myconst;
            }
        }
        else
        {
            //NOrth/South
            this.plugin.log(Level.FINER, "Found Portal: North/South");
            if(e.getLocation().getZ() < block.getLocation().getZ())
            {
                newVecZ = -1 * myconst;
            }
            else
            {
                newVecZ = 1 * myconst;
            }
        }
        e.teleport(e.getLocation().clone().add(newVecX, .2, newVecZ));
        e.setVelocity(new Vector(newVecX, .6, newVecZ));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityPortalEnter(EntityPortalEnterEvent event)
    {
        Entity e = event.getEntity();
        Player p = (e instanceof Player) ? (Player)e : null;
        Location eventLocation = event.getLocation().clone();
        Location block = this.locationManipulation.getBlockLocation(e.getLocation());
        if(!plugin.isHandledByNetherPortals(block))
        {
            return;
        }
        if(p != null)
        {
            if(this.eventRecord.containsKey(p.getName()))
            {
                // The the eventRecord shows this player was already trying to go somewhere.
                if(this.locationManipulation.getBlockLocation(p.getLocation()).equals(this.eventRecord.get(p.getName())))
                {
                    // The player has not moved, and we've already fired one event.
                    return;
                }
                else
                {
                    // The player moved, potentially out of the portal, allow event to re-check.
                    this.eventRecord.put(p.getName(), this.locationManipulation.getBlockLocation(p.getLocation()));
                    // We'll need to clear this value...
                }
            }
            else
            {
                this.eventRecord.put(p.getName(), this.locationManipulation.getBlockLocation(p.getLocation()));
            }
            MVPlayerTouchedPortalEvent playerTouchedPortalEvent = new MVPlayerTouchedPortalEvent(p, event.getLocation());
            this.plugin.getServer().getPluginManager().callEvent(playerTouchedPortalEvent);
            if(!playerTouchedPortalEvent.canUseThisPortal())
            {
                // Someone else said the player is not allowed to go here.
                this.shootEntity(p, eventLocation.getBlock(), PortalType.NETHER);
                this.plugin.log(Level.FINEST, "Someone request this player be kicked back!!");
            }
            if(playerTouchedPortalEvent.isCancelled())
            {
                this.plugin.log(Level.FINEST, "Someone cancelled the enter Event for NetherPortals!");
                return;
            }
            if(this.playerErrors.containsKey(p.getName()))
            {
                Date lastTry = this.playerErrors.get(p.getName());
                if(lastTry.getTime() + this.cooldown > new Date().getTime())
                {
                    return;
                }
                this.playerErrors.remove(p.getName());
            }
        }
        PortalType type = (event.getLocation().getBlock().getType() == Material.PORTAL) ? PortalType.NETHER : PortalType.END;
        String currentWorld = event.getLocation().getWorld().getName();
        String linkedWorld = this.plugin.getWorldLink(event.getLocation().getWorld().getName(), type);
        Location currentLocation = event.getLocation();
        Location toLocation = null;
        if(linkedWorld != null)
        {
            toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, linkedWorld, e);
        }
        else if(this.nameChecker.isValidNetherName(currentWorld))
        {
            if(type == PortalType.NETHER)
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNormalName(currentWorld, PortalType.NETHER), e);
            }
            else
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getEndName(this.nameChecker.getNormalName(currentWorld, PortalType.NETHER)), e);
            }
        }
        else if(this.nameChecker.isValidEndName(currentWorld))
        {
            if(type == PortalType.NETHER)
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNetherName(this.nameChecker.getNormalName(currentWorld, PortalType.END)), e);
            }
            else
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNormalName(currentWorld, PortalType.END), e);
            }
        }
        else
        {
            if(type == PortalType.END)
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getEndName(currentWorld), e);
            }
            else
            {
                toLocation = this.linkChecker.findNewTeleportLocation(currentLocation, this.nameChecker.getNetherName(currentWorld), e);
            }
        }
        if(toLocation == null)
        {
            this.shootEntity(e, eventLocation.getBlock(), type);
            if(p != null)
            {
                this.messaging.sendMessage(p, "This portal goes nowhere!", false);
                if(type == PortalType.END)
                {
                    this.messaging.sendMessage(p, "No specific end world has been linked to this world and '" + this.nameChecker.getEndName(currentWorld) + "' is not a world.", true);
                }
                else
                {
                    this.messaging.sendMessage(p, "No specific nether world has been linked to this world and '" + this.nameChecker.getNetherName(currentWorld) + "' is not a world.", true);
                }
            }
            return;
        }
        MultiverseWorld fromWorld = this.worldManager.getMVWorld(e.getLocation().getWorld().getName());
        MultiverseWorld toWorld = this.worldManager.getMVWorld(toLocation.getWorld().getName());
        if(fromWorld.getCBWorld().equals(toWorld.getCBWorld()))
        {
            // The player is Portaling to the same world.
            if(p != null)
            {
                this.plugin.log(Level.FINER, "Player '" + p.getName() + "' is portaling to the same world.");
            }
            return;
        }
        if(p != null)
        {
            if(!pt.playerHasMoneyToEnter(fromWorld, toWorld, p, p, false))
            {
                System.out.println("BOOM");
                this.shootEntity(p, eventLocation.getBlock(), type);
                this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was DENIED ACCESS to '" + toWorld.getCBWorld().getName() + "' because they don't have the FUNDS required to enter.");
                return;
            }
            if(this.plugin.getCore().getMVConfig().getEnforceAccess())
            {
                if(!pt.playerCanGoFromTo(fromWorld, toWorld, p, p))
                {
                    this.shootEntity(p, eventLocation.getBlock(), type);
                    this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was DENIED ACCESS to '" + toWorld.getCBWorld().getName() + "' because they don't have: multiverse.access." + toWorld.getCBWorld().getName());
                }
            }
            else
            {
                this.plugin.log(Level.FINE, "Player '" + p.getName() + "' was allowed to go to '" + toWorld.getCBWorld().getName() + "' because enforceaccess is off.");
            }
        }
    }
    
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerPortal(EntityPortalEvent event)
    {
        if(event.isCancelled())
        {
            this.plugin.log(Level.FINEST, "EntityPortalEvent was cancelled! NOT teleporting!");
            return;
        }
        Location originalTo = event.getTo();
        if(originalTo != null)
        {
            originalTo = originalTo.clone();
        }
        Location currentLocation = event.getFrom().clone();
        if(!plugin.isHandledByNetherPortals(currentLocation))
        {
            return;
        }
        String currentWorld = currentLocation.getWorld().getName();
        PortalType type = PortalType.END;
        if(event.getFrom().getBlock().getType() == Material.PORTAL)
        {
            type = PortalType.NETHER;
            event.useTravelAgent(true);
        }
        String linkedWorld = this.plugin.getWorldLink(currentWorld, type);
        if(linkedWorld != null)
        {
            this.linkChecker.getNewTeleportLocation(event, currentLocation, linkedWorld);
        }
        else if(this.nameChecker.isValidNetherName(currentWorld))
        {
            if(type == PortalType.NETHER)
            {
                this.plugin.log(Level.FINER, "");
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getNormalName(currentWorld, PortalType.NETHER));
            }
            else
            {
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getEndName(this.nameChecker.getNormalName(currentWorld, PortalType.NETHER)));
            }
        }
        else if(this.nameChecker.isValidEndName(currentWorld))
        {
            if(type == PortalType.NETHER)
            {
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getNetherName(this.nameChecker.getNormalName(currentWorld, PortalType.END)));
            }
            else
            {
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getNormalName(currentWorld, PortalType.END));
            }
        }
        else
        {
            if(type == PortalType.END)
            {
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getEndName(currentWorld));
            }
            else
            {
                this.linkChecker.getNewTeleportLocation(event, currentLocation, this.nameChecker.getNetherName(currentWorld));
            }
        }
        if(event.getTo() == null || event.getFrom() == null)
        {
            return;
        }
        Player p = (event.getEntity() instanceof Player) ? (Player)event.getEntity() : null;
        if(event.getFrom().getWorld().equals(event.getTo().getWorld()))
        {
            // The player is Portaling to the same world.
            if(p != null)
            {
                this.plugin.log(Level.FINER, "Player '" + p.getName() + "' is portaling to the same world.  Ignoring.");
            }
            event.setTo(originalTo);
            return;
        }
        MultiverseWorld fromWorld = this.worldManager.getMVWorld(event.getFrom().getWorld().getName());
        MultiverseWorld toWorld = this.worldManager.getMVWorld(event.getTo().getWorld().getName());
        if(!event.isCancelled() && (fromWorld.getEnvironment() == World.Environment.THE_END) && (type == PortalType.END))
        {
            if(p != null)
            {
                this.plugin.log(Level.FINE, "Player '" + p.getName() + "' will be teleported to the spawn of '" + toWorld.getName() + "' since they used an end exit portal.");
            }
            event.getPortalTravelAgent().setCanCreatePortal(false);
            if((p != null) && toWorld.getBedRespawn() && (p.getBedSpawnLocation() != null) && (p.getBedSpawnLocation().getWorld().getUID() == toWorld.getCBWorld().getUID()))
            {
                event.setTo(p.getBedSpawnLocation());
            }
            else
            {
                event.setTo(toWorld.getSpawnLocation());
            }
        }
    }
}