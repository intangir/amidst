package amidst.map;

import java.awt.*;
import java.awt.RenderingHints.Key;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;

import javax.imageio.ImageIO;


import amidst.Amidst;

import amidst.Log;
import amidst.map.layers.BiomeLayer;

public class Map {
	private static final boolean START = true, END = false;
	private static final AffineTransform iMat = new AffineTransform();
	private FragmentManager fManager;
	
	private Fragment startNode = new Fragment();
	
	private double scale = 0.25;
	private Point2D.Double start;
	private HashMap<String, Integer> biomeMap; 
	private String missing;
	
	private int tileWidth, tileHeight;
	public int width = 1, height = 1;
	
	private final Object resizeLock = new Object(), drawLock = new Object();
	private AffineTransform mat;
	
	private boolean firstDraw = true;
	
	
	private RenderingHints renderingHints;
	
	// TODO : This must be changed with the removal of ChunkManager
	public Map(Layer[] layers,  Layer[] liveLayers, IconLayer[] iconLayers) {
		for (Layer layer : layers)
			layer.setMap(this);
		
		for (Layer layer : liveLayers)
			layer.setMap(this);
		
		for (IconLayer layer : iconLayers)
			layer.setMap(this);
		
		
		fManager = new FragmentManager(layers, liveLayers, iconLayers);
		mat = new AffineTransform();
		
		start = new Point2D.Double();
		addStart(0, 0);
		
		HashMap<Key, Object> hintMap = new HashMap<Key, Object>();
		hintMap.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
		hintMap.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
		hintMap.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_SPEED);
		hintMap.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_DISABLE);
		hintMap.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		hintMap.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		hintMap.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
		hintMap.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		hintMap.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		
		renderingHints = new RenderingHints(hintMap);
		
		biomeMap = new HashMap<String, Integer>();
	}
	
	
	public void draw(Graphics2D g) {
		if (firstDraw) {
			firstDraw = false;
			centerOn(0, 0);
			missing = "";
			biomeMap.clear();
		}
		// TODO: Enable via settings?
		//g.setRenderingHints(renderingHints);
		synchronized (drawLock) {
			int size = (int) (Fragment.SIZE * scale);
			int w = width / size + 2;
			int h = height / size + 2;
			
			while (tileWidth <  w) addColumn(END);
			while (tileWidth >  w) removeColumn(END);
			while (tileHeight < h) addRow(END);
			while (tileHeight > h) removeRow(END);
			
			while (start.x >     0) { start.x -= size; addColumn(START); removeColumn(END);   }
			while (start.x < -size) { start.x += size; addColumn(END);   removeColumn(START); }
			while (start.y >     0) { start.y -= size; addRow(START);    removeRow(END);      }
			while (start.y < -size) { start.y += size; addRow(END);      removeRow(START);    }
			
			//g.setColor(Color.pink);
			Fragment frag = startNode;
			size = Fragment.SIZE;
			if (frag.hasNext) {
				mat.setToIdentity();
				mat.translate(start.x, start.y);
				mat.scale(scale, scale);
				while (frag.hasNext) {
					frag = frag.nextFragment;
					frag.draw(g, mat);
					mat.translate(size, 0);
					if (frag.endOfLine) {
						mat.translate(-size * w, size);
					}
				}
			}
			frag = startNode;
			if (frag.hasNext) {
				mat.setToIdentity();
				mat.translate(start.x, start.y);
				mat.scale(scale, scale);
				while (frag.hasNext) {
					frag = frag.nextFragment;
					frag.drawLive(g, mat);
					mat.translate(size, 0);
					if (frag.endOfLine) {
						mat.translate(-size * w, size);
					}
				}
			}
			
			frag = startNode;
			if (frag.hasNext) {
				mat.setToIdentity();
				mat.translate(start.x, start.y);
				mat.scale(scale, scale);
				while (frag.hasNext) {
					frag = frag.nextFragment;
					frag.drawObjects(g, mat);
					mat.translate(size, 0);
					if (frag.endOfLine) {
						mat.translate(-size * w, size);
					}
				}
			}
			g.setTransform(iMat);
			
		}
	}
	public void addStart(int x, int y) {
		synchronized (resizeLock) {
			Fragment start = fManager.requestFragment(x, y);
			start.endOfLine = true;
			startNode.setNext(start);
			tileWidth = 1;
			tileHeight = 1;
		}
	}
	
	public void addColumn(boolean start) {
		synchronized (resizeLock) {
			int x = 0;
			Fragment frag = startNode;
			if (start) {
				x = frag.nextFragment.blockX - Fragment.SIZE;
				Fragment newFrag = fManager.requestFragment(x, frag.nextFragment.blockY);
				newFrag.setNext(startNode.nextFragment);
				startNode.setNext(newFrag);
			}
			while (frag.hasNext) {
				frag = frag.nextFragment;
				if (frag.endOfLine) {
					if (start) {
						if (frag.hasNext) {
							Fragment newFrag = fManager.requestFragment(x, frag.blockY + Fragment.SIZE);
							newFrag.setNext(frag.nextFragment);
							frag.setNext(newFrag);
							frag = newFrag;
						}
					} else {
						Fragment newFrag = fManager.requestFragment(frag.blockX + Fragment.SIZE, frag.blockY);
						
						if (frag.hasNext) {
							newFrag.setNext(frag.nextFragment);
						}
						newFrag.endOfLine = true;
						frag.endOfLine = false;
						frag.setNext(newFrag);
						frag = newFrag;
						
					}
				}
			}
			tileWidth++;
		}
	}
	public void removeRow(boolean start) {
		synchronized (resizeLock) {
			if (start) {
				for (int i = 0; i < tileWidth; i++) {
					Fragment frag = startNode.nextFragment;
					frag.remove();
					fManager.returnFragment(frag);
				}
			} else {
				Fragment frag = startNode;
				while (frag.hasNext)
					frag = frag.nextFragment;
				for (int i = 0; i < tileWidth; i++) {
					frag.remove();
					fManager.returnFragment(frag);
					frag = frag.prevFragment;
				}
			}
			tileHeight--;
		}
	}
	public void addRow(boolean start) {
		synchronized (resizeLock) {
			Fragment frag = startNode;
			int y;
			if (start) {
				frag = startNode.nextFragment;
				y = frag.blockY - Fragment.SIZE;
			} else {
				while (frag.hasNext)
					frag = frag.nextFragment;
				y = frag.blockY + Fragment.SIZE;
			}
			
			tileHeight++;
			Fragment newFrag = fManager.requestFragment(startNode.nextFragment.blockX, y);
			Fragment chainFrag = newFrag;
			for (int i = 1; i < tileWidth; i++) {
				Fragment tempFrag = fManager.requestFragment(chainFrag.blockX + Fragment.SIZE, chainFrag.blockY);
				chainFrag.setNext(tempFrag);
				chainFrag = tempFrag;
				if (i == (tileWidth - 1))
					chainFrag.endOfLine = true;
			}
			if (start) {
				chainFrag.setNext(frag);
				startNode.setNext(newFrag);
			} else {
				frag.setNext(newFrag);
			}
		}
	}
	public void removeColumn(boolean start) {
		synchronized (resizeLock) {
			Fragment frag = startNode;
			if (start) {
				fManager.returnFragment(frag.nextFragment);
				startNode.nextFragment.remove();
			}
			while (frag.hasNext) {
				frag = frag.nextFragment;
				if (frag.endOfLine) {
					if (start) {
						if (frag.hasNext) {
							Fragment tempFrag = frag.nextFragment;
							tempFrag.remove();
							fManager.returnFragment(tempFrag);
						}
					} else {
						frag.prevFragment.endOfLine = true;
						frag.remove();
						fManager.returnFragment(frag);
						frag = frag.prevFragment;
					}
				}
			}
			tileWidth--;
		}
	}
	
	public void moveBy(Point2D.Double speed) {
		moveBy(speed.x, speed.y);
	}
	
	public void moveBy(double x, double y) {
		start.x += x;
		start.y += y;
	}
	
	public void centerOn(long x, long y) {
		long fragOffsetX = x % Fragment.SIZE;
		long fragOffsetY = y % Fragment.SIZE;
		long startX = x - fragOffsetX;
		long startY = y - fragOffsetY;
		synchronized (drawLock) {
			while (tileHeight > 1) removeRow(false);
			while (tileWidth > 1) removeColumn(false);
			Fragment frag = startNode.nextFragment;
			frag.remove();
			fManager.returnFragment(frag);
			// TODO: Support longs?
			double offsetX = (double)(width >> 1);
			double offsetY = (double)(height >> 1);

			offsetX -= ((double)fragOffsetX)*scale;
			offsetY -= ((double)fragOffsetY)*scale;
			
			start.x = offsetX;
			start.y = offsetY;
			
			addStart((int)startX, (int)startY);
		}
	}
	
	public void setZoom(double scale) {
		this.scale = scale;
	}
	
	public double getZoom() {
		return scale;
	}
	
	public Point2D.Double getScaled(double oldScale, double newScale, Point p) {
		double baseX = p.x - start.x;
		double scaledX = baseX - (baseX / oldScale) * newScale;
		
		double baseY = p.y - start.y;
		double scaledY = baseY - (baseY / oldScale) * newScale;
		
		return new Point2D.Double(scaledX, scaledY);
	}
	
	public void dispose() {
		fManager.close();
	}
	
	public Fragment getFragmentAt(Point position) {
		Fragment frag = startNode;
		Point cornerPosition = new Point(position.x >> Fragment.SIZE_SHIFT, position.y >> Fragment.SIZE_SHIFT);
		Point fragmentPosition = new Point();
		while (frag.hasNext) {
			frag = frag.nextFragment;
			fragmentPosition.x = frag.getFragmentX();
			fragmentPosition.y = frag.getFragmentY();
			if (cornerPosition.equals(fragmentPosition))
				return frag;
		}
		return null;
	}
	
	public MapObject getObjectAt(Point position, double maxRange) {
		double x = start.x;
		double y = start.y;
		MapObject closestObject = null;
		double closestDistance = maxRange;
		Fragment frag = startNode;
		int size = (int) (Fragment.SIZE * scale);
		while (frag.hasNext) {
			frag = frag.nextFragment;
			for (int i = 0; i < frag.objectsLength; i ++) {
				if (frag.objects[i].parentLayer.isVisible()) {
					Point objPosition = frag.objects[i].getLocation();
					objPosition.x *= scale;
					objPosition.y *= scale;
					objPosition.x += x;
					objPosition.y += y;
					
					double distance = objPosition.distance(position);
					if (distance < closestDistance) {
						closestDistance = distance;
						closestObject = frag.objects[i];
					}
				}
			}
			x += size;
			if (frag.endOfLine) {
				x = start.x;
				y += size;
			}
		}
		return closestObject;
	}

	public Point screenToLocal(Point inPoint) {
		Point point = inPoint.getLocation();

		point.x -= start.x;
		point.y -= start.y;
		
		// TODO: int -> double -> int = bad?
		point.x /= scale;
		point.y /= scale;

		point.x += startNode.nextFragment.blockX;
		point.y += startNode.nextFragment.blockY;

		return point;
	}
	public String getBiomeNameAt(Point point) {
		Fragment frag = startNode;
		while (frag.hasNext) {
			frag = frag.nextFragment;
			if ((frag.blockX <= point.x) &&
				(frag.blockY <= point.y) &&
				(frag.blockX + Fragment.SIZE > point.x) &&
				(frag.blockY + Fragment.SIZE > point.y)) {
				int x = point.x - frag.blockX;
				int y = point.y - frag.blockY;
				
				return BiomeLayer.getBiomeNameForFragment(frag, x, y);
			}
		}
		return "Unknown";
	}

	public String checkRequirements() {
		missing = "Missing: ";
		String biome;
		Point p = new Point();
		int v;
		if(biomeMap.isEmpty())
		{
			for(int x = -1600; x <= 1600; x+=5) {
				for(int y = -1600; y <= 1600; y+=5) {
					p.setLocation(x, y);
					biome = getBiomeNameAt(p);

					v = biomeMap.containsKey(biome) ? biomeMap.get(biome) : 0;
					biomeMap.put(biome, v + 1);
				
				}
			}
		}
		return getRequirements();
	}
	
	public String getRequirements() {
		missing = "Missing: ";
		Point p = new Point();
		p.setLocation(0,0);
		if(!"Desert".equals(getBiomeNameAt(p)))
		{
			missing += "Spawn,";
		}
		if(!biomeMap.containsKey("Ocean"))
		{
			missing += "Ocean,";
		}
		if(!biomeMap.containsKey("Swampland"))
		{
			missing += "Swampland,";
		}
		if(!biomeMap.containsKey("Ice Mountains"))
		{
			missing += "Ice Mountains,";
		}
		if(!biomeMap.containsKey("Extreme Hills"))
		{
			missing += "Extreme Hills,";
		}
		if(!biomeMap.containsKey("Desert"))
		{
			missing += "Desert,";
		}
		if(!biomeMap.containsKey("Roofed Forest"))
		{
			missing += "Roofed,";
		}
		
		if(!biomeMap.containsKey("Savanna"))
		{
			missing += "Savanna,";
		}
/*		if(!biomeMap.containsKey("Mesa"))
		{
			missing += "Mesa,";
		}
*/		if(!biomeMap.containsKey("Jungle"))
		{
			missing += "Jungle,";
		}
/*		if(!biomeMap.containsKey("Mushroom Island"))
		{
			missing += "Mushroom,";
		}
*/		if(!biomeMap.containsKey("Mega Taiga"))
		{
			missing += "Mega Taiga,";
		}
		if(!biomeMap.containsKey("Ice Plains Spikes"))
		{
			missing += "Spikes,";
		}
		if(!biomeMap.containsKey("Sunflower Plains"))
		{
			missing += "Sunflowers,";
		}
		if(!biomeMap.containsKey("Flower Forest"))
		{
			missing += "Flower Forest,";
		}
		
		if(missing.equals("Missing: "))
		{
			missing = "PERFECT";
		}
		
		return missing;
	}
}
