package pl.trammer.ludwik.geo;

import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JPanel;

/**
 * Klasa z której dziedziczy {@link HttpMap}. Choć powstała na potrzeby LudProxy
 * to zbudowana jest w sposób uniwersalny - może być wykorzystana w dowolnym
 * projekcie, który potrzebuje mapy świata na której będzie rysować coś
 * a podstawie współrzędnych geograicznych.
 * <p>
 * Plik graficzny z mapą został stworzony przez NASA i może być swobodnie wykorzystywany
 * bez opłat (<a href="http://visibleearth.nasa.gov/useterms.php">zobacz zasady licencyjne</a>).
 * Wikipedia twierdzi nawet, że znajduje się on w Domenie Publicznej, ale nie
 * udało mi się tego potwierdzić.
 * Grafikę można <a href="http://visibleearth.nasa.gov/view.php?id=57752">pobrać z serwisu
 * NASA Visible Earth</a>.
 * 
 * @author Ludwik Trammer
 *
 */
public class LudMapPanel extends JPanel {
	private static final long serialVersionUID = 8348564977286294932L;
	private Image image;
	private Image scaled;
	int panel_width;
	int panel_heigth;
	
	public LudMapPanel() {
		super();
		try {
			image = ImageIO.read((LudMapPanel.class).getResource("map.jpg"));
		} catch (IOException e) {
			System.err.println("Brakuje pliku z obrazkiem mapy!");
			System.exit(105);
		}
		
	}
	
	/**
	 * Skaluje grafikę mapy do wymiarów komponentu. 
	 */
	public void scale() {
		if(image==null) return;
		
		int image_width = image.getWidth(this);
		int image_height = image.getHeight(this);
		panel_width = getWidth();
		panel_heigth = getHeight();
		int scaled_width;
		int scaled_heigth;
		
		if(image_width < panel_width && image_height < panel_heigth) {
			// orginalne rozmiary
			scaled_width = -1;
			scaled_heigth = -1;
		} else if(panel_width/(double)panel_heigth > image_width/(double)image_height) {
			scaled_width = -1;
			scaled_heigth = (int)panel_heigth;
		} else {
			scaled_width = (int)panel_width;
			scaled_heigth = -1;		
		}
		
		/* jeśli byłoby 0 to by wywalał brzydki błąd */
		if(scaled_width==0) scaled_width=-1;
		if(scaled_heigth==0) scaled_heigth=-1;
		
		scaled = image.getScaledInstance(scaled_width, scaled_heigth, Image.SCALE_DEFAULT);	
	}
	
	/**
	 * Rysuje grafikę mapy na komponencie. W razie potrzeby (jeśli wymiary komponentu zmieniły
	 * się od ostatniego wywołania meotdy) wywołuje najpierw metodę {@link #scale()}.
	 */
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		
		if(panel_width!=getWidth() || panel_heigth!=getHeight()) {
			/* 
			 * przeskalowany obrazek był wyliczony dla innej wielkości panelu.
			 * Skaluj ponownie.
			 * 
			 */
			scale();
		}
		
		// narysuj mapę
		Point upperRight = getMapUpperLeftCorner();
		g.drawImage(scaled, (int)upperRight.getX(), (int)upperRight.getY(), this);
	}
	
	/**
	 * Zwraca punkt zawierajacy współrzędne (relatywne w stosunku do komponentu) lewego górnego punktu mapy
	 */
	public Point getMapUpperLeftCorner() {
		return new Point((panel_width-scaled.getWidth(this))/2, (panel_heigth-scaled.getHeight(this))/2);
	}
	
	/**
	 * Zwraca punkt zawierajacy współrzędne (relatywne w stosunku do komponentu) środka mapy
	 */
	public Point getMapCenter() {
		Point upperLeft = getMapUpperLeftCorner();
		return new Point((int)upperLeft.getX()+scaled.getWidth(this)/2, (int)upperLeft.getY()+scaled.getHeight(this)/2);
	}	
	
	/**
	 * Przelicza współrzędne geograficzne na współrzędne na mapie (relatywne w stosunku do komponentu)
	 * 
	 * @param c współrzędne geograficzne
	 * @return punkt w komponencie w którym grafika mapy odpowiada zadanym współrzędnym geograficznym
	 */
	public Point CoordinatesToMapPoint(Coordinates c) {
		Point center = getMapCenter();
		int x = (int)(center.getX() + c.getLongitude()*scaled.getWidth(this)/360.);
		int y = (int)(center.getY() - c.getLatitude()*scaled.getHeight(this)/180.);
		return new Point(x, y);
	}
}
