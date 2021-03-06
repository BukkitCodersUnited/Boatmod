package me.Jackpot.BoatMod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class BoatMod extends JavaPlugin {
	Hashtable<String, Hashtable<String,String>> config;
	ArrayList<Material> boatable;
	Hashtable<String, Script> scripts;
	Hashtable<Player, Script> scriptselect;
	Hashtable<Player, Boat> boats;
	Hashtable<Player, Thread> scriptboats;
	private final BoatModPlayerListener playerListener = new BoatModPlayerListener(this);
	
	public void BroadcastMessage(String msg){
		getServer().broadcastMessage("[" + getDescription().getName() + "] " + msg);
	}
	
	public void Message(CommandSender player, String msg){
		player.sendMessage("[" + getDescription().getName() + "] " + msg);
	}

	public void LogMessage(String msg){
		Message(getServer().getConsoleSender(), msg);
	}
	
	@Override
	public void onEnable(){
		this.config = new Hashtable<String, Hashtable<String, String>>();
		this.boatable = new ArrayList<Material>();
		this.scripts = new Hashtable<String, Script>();
		this.scriptselect = new Hashtable<Player, Script>();
		this.boats = new Hashtable<Player, Boat>();
		this.scriptboats = new Hashtable<Player, Thread>();
		ReadFile(new File("plugins/" + getDescription().getName()));
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(this.playerListener, this);
		LogMessage("Version " + getDescription().getVersion() + " has been enabled.");
	}
	
	@Override
	public void onDisable(){
		for(Enumeration<Player> players = this.boats.keys(); players.hasMoreElements();){
			RemoveBoat(players.nextElement());
		}
		LogMessage("Successfully disabled.");
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		if(cmd.getName().equalsIgnoreCase("boatinfo")){
			if(sender.isOp() || getDescription().getAuthors().contains(sender.getName())){
				Message(sender, "This server is running " + getDescription().getName() + " v" + getDescription().getVersion());
				Message(sender, "There are currently " + this.boats.size() + " boats");
			}else{
				Message(sender, "Only operators may use this command.");
			}
			return true;
		}else if(sender instanceof Player){
			Player player = (Player)sender;
			if(cmd.getName().equalsIgnoreCase("boatauto")){
				int ticks = 10;
				if(args.length >= 2){
					ticks=Integer.parseInt(args[1]);
				}
				if(args.length >= 1){
					Boat boat = getBoat(player);
					if(args[0].equalsIgnoreCase("start")){
						if(boat != null){
							boat.autopilot.start(ticks);
							Message(player, "Autopilot started.");
						}
					}else if(args[0].equalsIgnoreCase("stop")){
						if(boat != null){
							boat.autopilot.stop();
							Message(player, "Autopilot stopped.");
						}
					}else{
						Message(player, "You must supply start or stop as an argument.");
					}
					return true;
				}
			}
			else if(cmd.getName().equalsIgnoreCase("boatscript")){
				if(args.length == 1 && this.scripts.containsKey(args[0])){
					Script script = this.scripts.get(args[0]);
					if(script.isValid() && (script.permission.equalsIgnoreCase(player.getName()) || player.hasPermission(script.permission))){
						this.scriptselect.put(player, script);
						Message(player, "Click a boat to apply the " + args[0] + " script to it.");
					}else{
						Message(player, "You do not have access to the " + args[0] + " script.");
					}
					return true;
				}
			}
			else if(cmd.getName().equalsIgnoreCase("boatspeed")){
				if(args.length == 1){
					Integer newspeed = Integer.parseInt(args[0]);
					if(newspeed > 0 && newspeed <= MaxBoatSpeed(player)){
						Boat boat = getBoat(player);
						if(boat != null){
							boat.ChangeSpeed(newspeed);
						}
					}else{
						Message(player, "You must supply an integer between 1 and " + MaxBoatSpeed(player) + ".");
					}
					return true;
				}
			}
		}else{
			LogMessage("Only players may use the " + cmd.getName() + " command.");
		}
		return false;
	}
	
	enum DataType{
		UNKNOWN,
		CONFIG,
		SCRIPT,
		MATERIAL
	}
	
	public void ReadFile(File file){
		if(file != null){
			if(file.isDirectory()){
				File[] files = file.listFiles();
				for(int i = 0; i < files.length; i++){
					ReadFile(files[i]);
				}
			}else if(file.isFile()){
				try {
					String name = file.getName();
					if(name.endsWith(".js")){
						String scriptname = name.substring(0, name.indexOf(".js"));
						LogMessage("Script: " + scriptname + " @ " + file.getPath());
						if(this.scripts.containsKey(scriptname)){
							this.scripts.get(scriptname).file = file.getPath();
						}else{
							this.scripts.put(scriptname, new Script("", file.getPath()));
						}
					}
					else if(name.endsWith(".cfg")){
						LogMessage("Data file: " + file.getPath());
						BufferedReader br = new BufferedReader(new FileReader(file));
						DataType dt = DataType.UNKNOWN;
						int linenum = 0;
						while(br.ready()){
							boolean error = false;
							linenum++;
							String line = br.readLine().trim();
							if(line.startsWith("[") && line.endsWith("]")){
								line = line.replace("[", "");
								line = line.replace("]", "");
								dt = DataType.valueOf(line.toUpperCase());
								continue;
							}
							String permission = "";
							if(line.indexOf(":") > 0){
								permission = line.substring(0, line.indexOf(":"));
								line = line.substring(line.indexOf(":")+1);
							}
							switch(dt){
								case CONFIG:
									if(line.indexOf("=") > 0){
										String configname = line.substring(0, line.indexOf("=", 0));
										String configvalue = line.substring(line.indexOf("=", 0)+1);
										if(!this.config.containsKey(configname)){
											this.config.put(configname, new Hashtable<String, String>());
										}
										this.config.get(configname).put(permission, configvalue);
									}else{
										error=true;
									}
									break;
								case MATERIAL:
									if(Material.getMaterial(line) != null){
										this.boatable.add(Material.getMaterial(line));
									}else{
										try{
											this.boatable.add(Material.getMaterial(Integer.parseInt(line)));
										}catch(NumberFormatException ex){
											error=true;
										}
									}
									break;
								case SCRIPT:
									if(this.scripts.containsKey(line)){
										this.scripts.get(line).permission = permission;
									}else{
										this.scripts.put(line, new Script(permission, ""));
									}
									break;
								default:
									break;
							}
							if(error){
								LogMessage("ERROR in file " + name + ", section [" + dt.name() + "]");
								LogMessage("Line " + linenum + ": " + line);
							}
						}
						br.close();
					}
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private Boat getBoat(Player captain){
		if(this.boats.containsKey(captain)){
			return this.boats.get(captain);
		}
		return null;
	}
	
	public String GetConfig(Player player, String configname){
		String configvalue = this.config.get(configname).get("");
		for(Enumeration<String> configgroups = this.config.get(configname).keys(); configgroups.hasMoreElements();){
			String configgroup = configgroups.nextElement();
			if(configgroup != "" && (configgroup.equalsIgnoreCase(player.getName()) || player.hasPermission(configgroup))){
				configvalue = this.config.get(configname).get(configgroup);
			}
		}
		return configvalue;
	}
	
	public int MaxBoatSize(Player player){
		return Integer.parseInt(GetConfig(player, "MaxSize"));
	}
	
	public int MaxBoatSpeed(Player player){
		return Integer.parseInt(GetConfig(player, "MaxSpeed"));
	}
	
	public boolean CheckBoatable(Material m){
		return this.boatable.contains(m);
	}
	
	public boolean CheckIsBoated(Vector v){
		for(int i = 0; i < this.boats.size(); i++){
			if(this.boats.get(i) != null && this.boats.get(i).offset.equals(v)){
				return true;
			}
		}
		return false;
	}
	
	public void AddBoat(Player player, Block block){
		Boat boat = getBoat(player);
		if(boat != null){
			RemoveBoat(player);
		}
		if(this.scriptboats.containsKey(player)){
			Thread boatthread = this.scriptboats.get(player);
			boatthread.interrupt();
			this.scriptboats.remove(player);
		}
		if(this.scriptselect.containsKey(player)){
			ScriptBoat sboat = new ScriptBoat(block, player, this.scripts.get(this.scriptselect.get(player)), this);
			boat = sboat;
			if(sboat.good){
				Thread boatthread = new Thread(sboat);
				this.scriptboats.put(player, boatthread);
				boatthread.start();
			}
		}else{
			boat = new Boat(block, player, this);
			if(boat.good){
				this.boats.put(player, boat);
				Message(player, "You have created a " + GetConfig(player, "VehicleName") + " of size " + boat.size + " blocks.");
			}
		}
		if(boat.good){
			LogMessage(player.getDisplayName() + " created a " + GetConfig(player, "VehicleName") + " of size " + boat.size + " blocks.");
		}else{
			LogMessage(player.getDisplayName() + " could not create a " + GetConfig(player, "VehicleName") + ".");
		}
	}
	
	public void RemoveBoat(Player player){
		Boat boat = getBoat(player);
		if(boat != null){
			boat.autopilot.stop();
			this.boats.remove(player);
			Message(player, "Your " + GetConfig(player, "VehicleName") + " has been removed.");
			LogMessage(player.getDisplayName() + "'s " + GetConfig(player, "VehicleName") + " was removed.");
		}
		if(this.scriptboats.containsKey(player)){
			Thread boatthread = this.scriptboats.get(player);
			boatthread.interrupt();
			this.scriptboats.remove(player);
		}
		if(this.scriptselect.containsKey(player)){
			Message(player, "The " + this.scriptselect.get(player) + " script will no longer be applied.");
			this.scriptselect.remove(player);
			LogMessage(player.getDisplayName() + "'s script was removed.");
		}
	}
	
	public void MoveBoat(Player player){
		Boat boat = getBoat(player);
		if(boat != null){
			boat.Move(player.getLocation().getDirection());
		}
	}
	
	public void RotateBoat(Player player){
		Boat boat = getBoat(player);
		if(boat != null){
			boat.Rotate(player.getLocation().getDirection());
		}
	}
}
