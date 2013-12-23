package MoF;


import amidst.Log;
import amidst.Options;
import amidst.gui.PlayerMenuItem;
import amidst.map.IconLayer;
import amidst.map.Layer;
import amidst.map.Map;
import amidst.map.MapObject;
import amidst.map.MapObjectPlayer;
import amidst.map.layers.BiomeLayer;
import amidst.map.layers.GridLayer;
import amidst.map.layers.NetherFortressLayer;
import amidst.map.layers.PlayerLayer;
import amidst.map.layers.SlimeLayer;
import amidst.map.layers.SpawnLayer;
import amidst.map.layers.StrongholdLayer;
import amidst.map.layers.TempleLayer;
import amidst.map.layers.VillageLayer;
import amidst.minecraft.Minecraft;
import amidst.utilties.FramerateTimer;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

public class MapViewer extends JComponent implements MouseListener, MouseWheelListener, KeyListener {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8309927053337294612L;
	private Project proj;
	
	private JPopupMenu menu = new JPopupMenu();
	private double scale = 1;
	public int strongholdCount, villageCount;
	
	public Map worldMap;
	private MapObject selectedObject = null;
	private Point lastMouse;
	public Point lastRightClick = null;
	private Point2D.Double panSpeed;
	
	private boolean isMouseInside = false;
	private static int zoomLevel = 0, zoomTicksRemaining = 0;
	private static double targetZoom = 0.25f, curZoom = 0.25f;
	private Point zoomMouse = new Point();
	
	private Color textColor = new Color(1f, 1f, 1f),
				  panelColor = new Color(0.2f, 0.2f, 0.2f, 0.7f);
	private Font textFont = new Font("arial", Font.BOLD, 15);
	
	private FramerateTimer fps = new FramerateTimer(2);
	private FontMetrics textMetrics;

	public void dispose() {
		//Log.debug("DISPOSING OF MAPVIEWER");
		worldMap.dispose();
		menu.removeAll();
		proj = null;
	}
	
	MapViewer(Project proj) {
		panSpeed = new Point2D.Double();
		this.proj = proj;
		IconLayer[] iconLayers = null;
		PlayerLayer playerLayer = null;
		if (!proj.saveLoaded) {
			iconLayers = new IconLayer[] {
				new VillageLayer(),
				new StrongholdLayer(),
				new TempleLayer(),
				new SpawnLayer(),
				new NetherFortressLayer()
			};
		} else {
			iconLayers = new IconLayer[] {
				new VillageLayer(),
				new StrongholdLayer(),
				new TempleLayer(),
				new SpawnLayer(),
				new NetherFortressLayer(),
				playerLayer = new PlayerLayer(proj.save)
			};
			
			for (MapObjectPlayer player : proj.save.getPlayers()) {
				menu.add(new PlayerMenuItem(this, player, playerLayer));
			}
		}
		
		worldMap = new Map(
				new Layer[] {
					new BiomeLayer(),
					new SlimeLayer()
				},
				new Layer[] {
					new GridLayer()
				},
				iconLayers
		); //TODO: implement more layers
		worldMap.setZoom(curZoom);
		
		addMouseListener(this);
		addMouseWheelListener(this);
		
		setFocusable(true);
	}
	
	@Override
	public void paint(Graphics g) {
		Graphics2D g2d = (Graphics2D)g;
		g2d.setColor(Color.black);
		g2d.fillRect(0, 0, this.getWidth(), this.getHeight());
		

		if (zoomTicksRemaining-- > 0) {
			double lastZoom = curZoom;
			curZoom = (targetZoom + curZoom) * 0.5;
			
			Point2D.Double targetZoom = worldMap.getScaled(lastZoom, curZoom, zoomMouse);
			worldMap.moveBy(targetZoom);
			worldMap.setZoom(curZoom);
		}

		Point curMouse = getMousePosition();
		isMouseInside = (curMouse != null);
		if (lastMouse != null) {
			if (curMouse != null) {
				double difX = curMouse.x - lastMouse.x;
				double difY = curMouse.y - lastMouse.y;
				// TODO : Scale with time
				panSpeed.setLocation(difX * 0.2, difY * 0.2);
			}
			
			lastMouse.translate((int) panSpeed.x, (int)panSpeed.y);
		}

		worldMap.moveBy((int)panSpeed.x, (int)panSpeed.y);
		if (Options.instance.mapFlicking.get()) {
			panSpeed.x *= 0.95f;
			panSpeed.y *= 0.95f;
		} else {
			panSpeed.x *= 0.f;
			panSpeed.y *= 0.f;
		}
		
		worldMap.width = getWidth();
		worldMap.height = getHeight();
		worldMap.draw(g2d);
		
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setFont(textFont);
		
		textMetrics = g2d.getFontMetrics(textFont);
		
		drawSeed(g2d);
		if (selectedObject != null)
			drawSelectedInformation(g2d);
		if (isMouseInside)
			drawMouseInformation(g2d, curMouse);
		if (Options.instance.showFPS.get())
			drawFramerate(g2d);
		drawRequirements(g2d);
	}
	
	private void drawSeed(Graphics2D g2d) {
		g2d.setColor(panelColor);
		g2d.fillRect(10, 10, textMetrics.stringWidth(Options.instance.getSeedMessage()) + 20, 30);
		g2d.setColor(textColor);
		g2d.drawString(Options.instance.getSeedMessage(), 20, 30);
	}
	
	private void drawMouseInformation(Graphics2D g2d, Point mousePosition) {		
		g2d.setColor(panelColor);
		Point mouseLocation = worldMap.screenToLocal(mousePosition);
		String biomeName = worldMap.getBiomeNameAt(mouseLocation);
		String mouseLocationText = biomeName + " [" + mouseLocation.x + ", " + mouseLocation.y + "]";
		int stringWidth = textMetrics.stringWidth(mouseLocationText);
		g2d.fillRect(getWidth() - (25 + stringWidth), 10, (15 + stringWidth), 30);
		
		g2d.setColor(textColor);
		g2d.drawString(mouseLocationText, getWidth() - (18 + stringWidth), 30);
	}
	
	private void drawRequirements(Graphics2D g2d) {		
		g2d.setColor(panelColor);
		String requirements = worldMap.getRequirements();
		int stringWidth = textMetrics.stringWidth(requirements);
		g2d.fillRect(getWidth() - (25 + stringWidth), getHeight() - 40, (15 + stringWidth), 30);
		
		g2d.setColor(textColor);
		g2d.drawString(requirements, getWidth() - (18 + stringWidth), getHeight() - 20);
	}
	
	private void drawSelectedInformation(Graphics2D g2d) {
		g2d.setColor(panelColor);
		String selectionMessage = selectedObject.getName() + " [" + selectedObject.rx + ", " + selectedObject.ry + "]";
		g2d.fillRect(10, 45, 45 + textMetrics.stringWidth(selectionMessage), 35);

		g2d.setColor(textColor);
		double width = selectedObject.getWidth();
		double height = selectedObject.getHeight();
		double ratio = width/height;
		
		g2d.drawImage(selectedObject.getImage(), 15, 50, (int)(25.*ratio), 25, null);
		g2d.drawString(selectionMessage, 50, 68);
	
	}
	private void drawFramerate(Graphics2D g2d) {
		fps.tick();
		String framerate = fps.toString();
		g2d.setColor(panelColor);
		g2d.fillRect(10, getHeight() - 40, textMetrics.stringWidth(framerate) + 20, 30);
		g2d.setColor(textColor);
		g2d.drawString(framerate, 20, getHeight() - 20);
	}
	
	public void centerAt(long x, long y) {
		worldMap.centerOn(x, y);
	}
	
	public void adjustZoom(Point position, int notches) {
		zoomMouse = position;
		if (notches > 0) {
			if (zoomLevel < (Options.instance.maxZoom.get()?10:10000)) {
				targetZoom /= 1.1;
				zoomLevel++;
				zoomTicksRemaining = 100;
			}
		} else {
			if (zoomLevel > -20) {
				targetZoom *= 1.1;
				zoomLevel--;
				zoomTicksRemaining = 100;
			}
		}
	}
	
	public void mouseWheelMoved(MouseWheelEvent e) {
		int notches = e.getWheelRotation();
		
		adjustZoom(getMousePosition(), notches);
	}
	@Override
	public void mouseClicked(MouseEvent e) {
		if (!e.isMetaDown()) {
			Point mouse = getMousePosition();
			MapObject object = worldMap.getObjectAt(mouse, 50.0);
			
			if (selectedObject != null)
				selectedObject.localScale = 1.0;

			if (object != null)
				object.localScale = 1.5;
			selectedObject = object;
		}
	}
	
	
	@Override
	public void mouseEntered(MouseEvent arg0) {
	}
	@Override
	public void mouseExited(MouseEvent arg0) {
	}
	@Override
	public void mousePressed(MouseEvent e) {
		if (!e.isMetaDown())
			lastMouse = getMousePosition();
	}
	
	@Override
	public void mouseReleased(MouseEvent e) {
		if (e.isPopupTrigger() && Minecraft.getActiveMinecraft().version.saveEnabled()) {
			lastRightClick = getMousePosition();
			if (proj.saveLoaded) {
				menu.show(e.getComponent(), e.getX(), e.getY());
			}
		} else lastMouse = null;
	}
	
	public MapObject getSelectedObject() {
		return proj.curTarget;
	}
	
	
	public void movePlayer(String name, ActionEvent e) {
		//PixelInfo p = getCursorInformation(new Point(tempX, tempY));
		
		//proj.movePlayer(name, p);
	}
	
	public void saveToFile(File f) {
		BufferedImage image = new BufferedImage(worldMap.width, worldMap.height, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2d = image.createGraphics();
		
		worldMap.draw(g2d);
		
		g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g2d.setFont(textFont);
		
		textMetrics = g2d.getFontMetrics(textFont);

		FontMetrics textMetrics = g2d.getFontMetrics(textFont);
		
		drawSeed(g2d);
		drawRequirements(g2d);
		
		
		try {
			ImageIO.write(image, "png", f);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		g2d.dispose();
		image.flush();
	}
	
	@Override
	public void keyPressed(KeyEvent e) {
		// TODO Auto-generated method stub
		Point mouse = getMousePosition();
		if (mouse == null)
			mouse = new Point((int)(getWidth() >> 1), (int)(getHeight () >> 1));
		if (e.getKeyCode() == KeyEvent.VK_EQUALS)
				adjustZoom(mouse, -1);
		else if (e.getKeyCode() == KeyEvent.VK_MINUS)
				adjustZoom(mouse, 1);
	}

	@Override
	public void keyReleased(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void keyTyped(KeyEvent e) {
		// TODO Auto-generated method stub
		
	}
}
