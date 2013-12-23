package MoF;

import amidst.Log;
import amidst.Options;
import amidst.map.MapObject;
import amidst.map.MapObjectPlayer;
import amidst.minecraft.Minecraft;
import amidst.minecraft.MinecraftUtil;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.KeyListener;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

@Deprecated //TODO: we should remove this and integrate it into Options
public class Project extends JPanel {
	private static final long serialVersionUID = 1132526465987018165L;
	
	public MapViewer map;
	public static int FRAGMENT_SIZE = 256;
	private Timer timer;
	private Timer reqTimer;
	public MapObject curTarget;
	public final FinderWindow window;
	
	public boolean saveLoaded;
	public SaveLoader save;
	
	public Project(String seed) {
		this(stringToLong(seed));
		Options.instance.seedText = seed;
		
		Google.track("seed/" + seed + "/" + Options.instance.seed);
	}
	
	public Project(long seed) {
		this(seed, SaveLoader.Type.DEFAULT, null);
	}
	
	public Project(SaveLoader file) {
		this(file.seed, SaveLoader.genType, file, null);
		
		Google.track("seed/file/" + Options.instance.seed);
	}
	
	public Project(String seed, SaveLoader.Type type) {
		this(stringToLong(seed), type, null);
		
		Google.track("seed/" + seed + "/" + Options.instance.seed);
	}
	
	public Project(long seed, SaveLoader.Type type, FinderWindow win) {
		this(seed, type, null, win);
	}
	public Project(long seed, SaveLoader.Type type, SaveLoader saveLoader, FinderWindow win) {
		SaveLoader.genType = type;
		this.window = win; 
		saveLoaded = !(saveLoader == null);
		save = saveLoader;
		//Enter seed data:
		Options.instance.seed = seed;
		
		BorderLayout layout = new BorderLayout();
		this.setLayout(layout);
		MinecraftUtil.createBiomeGenerator(seed, type);
		//Create MapViewer
		map = new MapViewer(this);
		add(map, BorderLayout.CENTER);
		//Debug
		this.setBackground(Color.BLUE);
		
		//Timer:
		reqTimer = new Timer();
		reqTimer.schedule(new TimerTask() {
			public void run() {
				reqTick();
			}
		}, 1000);

		timer = new Timer();
		timer.scheduleAtFixedRate(new TimerTask() {
			public void run() {
				tick();
			}
		}, 20, 20);
		
	}
	
	public void reqTick() {
		String requirements = map.worldMap.checkRequirements();
		
		Log.debug(Options.instance.getSeedMessage() + " : " + requirements);
		/*if(!requirements.equals("PERFECT"))
		{
			Random random = new Random();
			long seed = random.nextLong();
			SaveLoader.Type worldType = SaveLoader.Type.DEFAULT;
			window.setProject(new Project(seed, worldType, window));
		}*/
	}
	
	public void tick() {
		map.repaint();
	}
	
	public void dispose() {
		map.dispose();
		map = null;
		timer.cancel();
		timer = null;
		reqTimer.cancel();
		reqTimer = null;
		curTarget = null;
		save = null;
		System.gc();
	}
	
	private static long stringToLong(String seed) {
		long ret;
		try {
			ret = Long.parseLong(seed);
		} catch (NumberFormatException err) { 
			ret = seed.hashCode();
		}
		return ret;
	}
	
	
	public KeyListener getKeyListener() {
		return map;
	}
	public void moveMapTo(long x, long y) {
		map.centerAt(x, y);
	}
}
