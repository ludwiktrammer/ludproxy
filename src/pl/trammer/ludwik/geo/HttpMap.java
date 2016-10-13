package pl.trammer.ludwik.geo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.SwingUtilities;

import pl.trammer.ludwik.ludproxy.ServerResponse;

/**
 * Klasa dziedzicząca z klasy {@link LudMapPanel}, przystosowana do graficznego
 * prezentowania na mapie komunikacji z serwerami. Wewnątrz klasy przechowywana jest lista serwerów
 * i obiektów klasy {@link ConversationProperties}, zawierających dane o komunikacji z tymi serwerami.
 * <p>
 * Klasa posiada m.in. metody pozwalające na dodawanie kolejnych serwerów do wyświetlenia oraz na
 * oznaczanie części z nich jako wyróżnione.
 * <p>
 * <b>Terminologia</b>:
 * <ul>
 * <li>Określenie <em>"komunikacja z serwerem"</em> używane w opisie metod tej klasy
 * oznacza sumę wszystkich połączeń między programem, a danym serwerem (od czasu uruchomienia
 * programu lub momentu wyczyszczenia przez użytkownika listy połączeń).<br>
 * <li><em>"Suma ilości danych"</em> w ramach takiej komunikacji  to suma wielkości ciał wszystkich wiadomości
 * HTTP otrzymanych od danego serwera (w bajtach).<br>
 * <li><em>"Właściwości komunikacji z serwerem"</em> to informacje o komunikacji z danym serwerem, przechowywane wewnątrz obiektu
 * klasy {@link ConversationProperties}, takie jak współrzędne geograficzne serwera, lista pośrednich routerów,
 * suma ilości otrzymanych danych i średnie opóźnienie.
 * </ul>
 * 
 * @author Ludwik Trammer
 *
 */
public class HttpMap extends LudMapPanel {
	private static final long serialVersionUID = -2363688656937572270L;
	private Map<InetAddress, ConversationProperties> paths = new ConcurrentHashMap<InetAddress, ConversationProperties>();
	private Set<InetAddress> selected = null;
	private Coordinates localhostCoordinates = null;
	
	/**
	 * Na podstawie obiektu klasy {@link ServerResponse} przekazuje do obiektu
	 * informacje na temat połączenia w ramach którego otrzymano tę odpowiedź.
	 * Odbywa się to zgodnie z zasadami opisanymi przy okazji metody
	 * {@link #processConnection(InetAddress, double, int)}.
	 * 
	 * @param response odpowiedź od serwera
	 */
	public void processResponse(ServerResponse response) {
		processConnection(response.getServerIp(), response.getLatency(), response.getContentLength());
	}
	
	/**
	 * Przekazuje do obiektu informacje o pojedyńczym połączeniu z serwerem (wraz z parametrami
	 * takimi jak ilość otrzymanych danych i opóźnienie).
	 * <p>
	 * Jeśli na mapie nie było wcześniej informacji o komunikacji z danym serwerem to metoda sprawdzi
	 * (w nowym wątku) fizyczne położenie serwera, oraz ścieżkę routerów na drodze do niego (traceroute)
	 * i doda do mapy informacje o komunikacji z serwerem.
	 * Jeśli mapa obejmowała już wcześniej komunikację z danym serwerem to metoda jedynie 
	 * zaktualizuje jej właściwości korzystając z parametrów
	 * {@code latency} (opóźnienie) i {@code length} (ilość danych w połączniu).
	 * 
	 * @param server adres ip serwera którego dotyczy to połączenie
	 * @param latency wartość opóźnienia w połączeniu (w sekundach)
	 * @param length ilość bajtów danych otrzymanych w ramach danego połączenia
	 * @see ConversationProperties#update(double, int)
	 * @see #paintComponent(Graphics)
	 * @see Geolocation
	 * @see Traceroute
	 */
	public void processConnection(final InetAddress server, final double latency, final int length) {

		if(paths.containsKey(server)) {
			/*
			 * Komunikacja z tym serwerem jest już na mapie, zaktualizujemy je tylko nowymi danymi
			 * (rozmiar i opóźnienie)
			 */
			paths.get(server).update(latency, length);
			repaint();
		} else {
			/* 
			 * Nowy serwer do umieszczenia na mapie.
			 * W nowym wątku, bo będziemy wykonywać kosztowne czynności.
			 */
			new Thread() {
				public void run() {
					// Sprawdź położenie końcowego serwera
					Coordinates serverCo = Geolocation.getCoordinatesFromIP(server);
					
					// jeśli jeszcze nie ustaliliśmy położenia lokalnego komputera
					// to zróbmy to teraz
					if(localhostCoordinates==null) localhostCoordinates = Geolocation.getMyLocation();
					
					if(serverCo==null) return; // nie udało się znaleźc położenia dla IP
					
					ConversationProperties properties;
					synchronized(HttpMap.class) {
						if(!paths.containsKey(server)) {
							properties = new ConversationProperties(serverCo, latency, length);
							paths.put(server, properties);
						} else {
							properties= paths.get(server);
							properties.update(latency, length);
						}
					}
					
					SwingUtilities.invokeLater(new Runnable() {
					    public void run() {
					    	repaint();
					    }
					  });
					
					/*
					 * Dodaliśmy już informacje o serwerze i odświeżyliśmy mapę, prezentując
					 * nowe informacje użytkownikowi.
					 * Dopiero teraz uruchamiamy traceroute.
					 * Gdy zwróci ono pośrednie routery zaktualizujemy informacje i ponownie
					 * odświeżymy mapę, prezentując nowe dane.
					 */
					properties.setIntermediateRouters(Traceroute.getHosts(server));
					
					SwingUtilities.invokeLater(new Runnable() {
					    public void run() {
					    	repaint();
					    }
					  });
				}
			}.start();
		}
		
	}
	
	/**
	 * Usuwa wszystkie informacje o wcześniejszej komunikacji z serwerami.
	 */
	public void clear() {
		paths.clear();
	}
	
	/**
	 * Rysuje na mapie informacje o komunikacji z serwerami wg. listy serwerów przechowywanej
	 * wewnątrz obiektu.
	 * <p>
	 * Każda linia narysowana na mapie symbolizuje sumę wszystkich połączeń wykonanych
	 * z danym serwerem. Grubość i kolor linii zależy od właściwości opisanych w opisie
	 * metody {@link #drawConnection(Graphics2D, ConversationProperties, float, float)}. 
	 * <p>
	 * Jeśli obiekt otrzymał listę "wyróżnionych" połączeń (czyli w praktyce tych, które
	 * są zaznaczone w tabeli pod mapą) to pozostałe linie zostaną "wyszarzone", a
	 * do wyróżnionych linii dodatkowo dodane zostaną etykiety komputerów na krańcach
	 * połączenia (na biało) oraz routerów biorących udział w przekazywaniu wiadomości
	 * (na szaro).
	 * 
	 */
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2d = (Graphics2D)g;
		ConversationProperties properties;
		
		/*
		 * Narysuj linie komunikacji ze wszystkimi serwerami.
		 * Jeśli istnieje lista wyróżnionych
		 * (selected) zmniejsz jasność i nasycenie.
		 */
		for(InetAddress server : paths.keySet()) {
			properties = paths.get(server);
			if(selected==null) drawConnection(g2d, properties, 1, 1);
			else drawConnection(g2d, properties, 0.3f, 0.6f);
		}

		if(selected!=null) { // istnieje lista wyróżnionych
			/*
			 * Na początku grupujemy etykiety, które powinny zostać wyświetlone w tym
			 * samym miejscu na mapie. Dzięki temu wypiszemy je po przecinku, a nie
			 * jedne na drugich. Osobno grupowane są komputery końcowe i osobno
			 * routery przekazujące dane po drodze.
			 */
			HashMap<Coordinates, String> labels_servers = new HashMap<Coordinates, String>();
			HashMap<Coordinates, String> labels_routers = new HashMap<Coordinates, String>();
		
			labels_servers.put(localhostCoordinates, "localhost");
			for(InetAddress server : selected) {
				properties = paths.get(server);
				if(properties==null) continue;
				Coordinates co = properties.getServerCoordinates();
				if(labels_servers.containsKey(co)) {
					String label = labels_servers.get(co) + ", " + server.getHostName();
					labels_servers.put(co, label);
				} else {
					labels_servers.put(co, server.getHostName());
				}
				
				for(IntermediateRouter router : properties.getIntermediateRouters()) {
					if(!labels_routers.containsKey(router.getCoordinates())) {
						labels_routers.put(router.getCoordinates(), router.getAddress().getHostName());
					}
				}
			}
			
			/*
			 * Rysowanie (szarych) etykiet routerów
			 */
			for(Coordinates c : labels_routers.keySet()) {
				String label = labels_routers.get(c);
				drawText(g2d, label, c, Color.GRAY);
			}
			
			/*
			 * Rysowanie linii wyróżnionych komunikacji.
			 * Zawsze w pełnym kolorze.
			 */
			for(InetAddress server : selected) {
				properties = paths.get(server);
				drawConnection(g2d, properties, 1, 1);
			}
			
			/*
			 * Rysowanie (białych) etykiet komputerów na krańcach połączenia
			 */
			for(Coordinates c : labels_servers.keySet()) {
				String label = labels_servers.get(c);
				drawText(g2d, label, c, Color.WHITE);
			}
		}
		
	}
	
	/**
	 * Wypisywanie tekstu w określonym punkcie mapy. 
	 * 
	 * @param g2d kontekst graficzny
	 * @param text tekst do wypisania
	 * @param co współrzędne geograficzne punktu na mapie w którym
	 * ma zostać wypisany tekst
	 * @param color kolor tekstu (niezależnie od wybranego koloru tekst
	 * będzie dodatkowo posiadał czarny cień)
	 */
	public void drawText(Graphics2D g2d, String text, Coordinates co, Color color) {
		Point point = CoordinatesToMapPoint(co);
		// cień
		g2d.setColor(Color.DARK_GRAY);
		g2d.drawString(text, (int)point.getX()+1, (int)point.getY()+1);
		
		g2d.setColor(color);
		g2d.drawString(text, (int)point.getX(), (int)point.getY());
	}
	
	/**
	 * Rysuje na mapie linie symbolizującą komunikację z serwerem, której właściwości zostały przekazane
	 * jako parametr {@code properties}.
	 * <p>
	 * Grubość linii zależy od sumy ilości danych, które zostały otrzymwane w komunikacji z serwerem.
	 * Bazowo jest to 1px, każde pełne 50kb otrzymanych danych zwiększa grubość o dodatkowe 1px.
	 * Maksymalna grubość to 10px.
	 * <p>
	 * Kolor linii zależy od średniego opóźnienia w komunikacji z serwerem. Barwa płynnie przechodzi
	 * od koloru turkusowego (wartości zbliżone do 0 ms), przez zielony i żółty aż do
	 * czerwonego (wartości w okolicach 500ms i więcej).
	 * 
	 * @param g2d kontekst graficzny
	 * @param properties właściwości komunikacji do narysowania
	 * @param saturation nasycenie koloru linii
	 * @param brigthness jasność koloru linii
	 */
	public void drawConnection(Graphics2D g2d, ConversationProperties properties, float saturation, float brigthness) {
		// obliczmy kolor linii 
		float hue = 0.5f - (float)properties.getLatency(); if(hue<0) hue=0;


		g2d.setColor(Color.getHSBColor(hue, saturation, brigthness));
		
		// obliczmy długość linii
		// 1px na każde 10 kb, ale nie więcej niż 10 pikseli
		float thickness = 1+properties.getLength()/(1024*50);
		if(thickness>10) thickness=10;
		g2d.setStroke(new BasicStroke(thickness));

		// antialiasing
		g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		/*
		 * Rysujemy linie pomiędzy kolejnymi punktami (routerami) na trasie połączenia.
		 */
		Coordinates prev = localhostCoordinates; // punkt startowy
		Coordinates end = properties.getServerCoordinates();
		
		Point line_start, line_stop;
		
		for(IntermediateRouter current : properties.getIntermediateRouters()) {
			line_start = CoordinatesToMapPoint(prev);
			line_stop = CoordinatesToMapPoint(current.getCoordinates());
			g2d.draw(new Line2D.Double(line_start.getX(), line_start.getY(), line_stop.getX(), line_stop.getY()));
			prev = current.getCoordinates();
		}
		
		/*
		 * Ostateczna linia pomiędzy ostatnio narysowanym punktem (lub punktem startowym
		 * jeśli jeszcze nie dostaliśmy listy pośrednich routerów) a serwerem końcowym.
		 */
		line_start = CoordinatesToMapPoint(prev);
		line_stop = CoordinatesToMapPoint(end);
		g2d.draw(new Line2D.Double(line_start.getX(), line_start.getY(), line_stop.getX(), line_stop.getY()));
	}
	
	/**
	 * Ustawia listę serwerów, które mają zostać wyróżnione na mapie. Komunikacja
	 * z nimi będzie wyróżniona kolorystycznie i będzie zawierać tekstowe etykiety.
	 * 
	 * @param selected adresy ip serwerów których komunikacja ma być wyróżniona
	 * lub wartość {@code null} jeśli nic nie ma być wyróżnione.
	 * @see HttpMap#paintComponent(Graphics)
	 */
	public void setSelected(Set<InetAddress> selected) {
		this.selected = selected;
		repaint();
	}
	
	/**
	 * Obiekty tej klasy przechowują zbiorcze informacje na temat komunikacji z serwerem,
	 * wykorzystywane do rysowania na mapie ścieżek połączeń między hostami.
	 * 
	 * @author Ludwik Trammer
	 *
	 */
	public class ConversationProperties {
		/**
		 * Współrzędzne geograficzne końcowego serwera.
		 */
		private Coordinates serverCoordinates;
		
		/**
		 * Lista routerów przekazujących dane.
		 */
		private List<IntermediateRouter> intermediateRouters = new ArrayList<IntermediateRouter>();
		
		private volatile double latency;
		private volatile int length;
		
		/**
		 * Tworzy nowy obiekt, ustawiając początkowe wartości sumy ilości danych i opóźnienia.
		 * 
		 * @param serverCoordinates współrzędzne geograficzne końcowego serwera
		 * @param latency opóźnienie połączenia
		 * @param length ilość danych otrzymanych od serwera
		 */
		public ConversationProperties(Coordinates serverCoordinates, double latency, int length) {
			this.serverCoordinates = serverCoordinates;
			this.latency = latency;
			this.length = length;
		}
		
		/**
		 * Aktualizuje informacje o istniejącej komunikacji z serwerem o dane pojedyńczego
		 * połączenia.
		 * <p>
		 * Parametr {@code latency} wykorzystywany jest do obliczenia średniego opóźnienia
		 * w ramach całej komunikacji, w sposób inspirowany wzorem na "Estimated RTT"
		 * w komunikacji TCP (z wykładu).
		 * <p>
		 * Parametr {@code length} jest dodawany do wcześniejszej sumy ilości danych 
		 * w ramach komunikacji z danym serwerem.
		 * 
		 * @param latency opóźnienie w połączniu
		 * @param length ilość danych otrzymanych od serwera w ramach tego połączenia
		 */
		public synchronized void update(double latency, int length) {
			this.length += length;
			
			double alpha = 0.125;
			this.latency = this.latency*(1-alpha)+latency*alpha;
		}
		
		/**
		 * Przyjmuje listę adresów ip routerów, które pośredniczą
		 * w danej komunikacji. Metoda sprawdza ich położenie
		 * geograficzne i zapisuje wynik wewnątrz obiektu.
		 * 
		 * @param list lista adresów ip routerów pośredniczących w
		 * komunikacji
		 */
		public void setIntermediateRouters(InetAddress[] list) {
			Coordinates c;
			Coordinates prev=null;
			List<IntermediateRouter> ir = new ArrayList<IntermediateRouter>();
			for(InetAddress router : list) {
				c = Geolocation.getCoordinatesFromIP(router);
				
				if(c==null) continue;
				if(c.equals(prev)) continue; // dwa identyczne punkty pod rząd są nieprzydatne - pomijamy

				ir.add(new IntermediateRouter(router, c));
				prev = c;
			}
			intermediateRouters = ir;
		}
		
		/**
		 * Zwraca listę routerów pośredniczących w tej komunikacji.
		 * 
		 */
		public List<IntermediateRouter> getIntermediateRouters() {
			return intermediateRouters;
		}

		/**
		 * Zwraca współrzędne geograficzne końcowego komputera (serwera)
		 * 
		 */
		public Coordinates getServerCoordinates() {
			return serverCoordinates;
		}

		/**
		 * Zwraca średnie opóźnienie w ramach komunikacji z danym serwerem.
		 * 
		 * @see #update(double, int)
		 */
		public double getLatency() {
			return latency;
		}

		/**
		 * Zwraca sumę ilości danych w ramach komunikacji z danym serwerem.
		 * 
		 */
		public int getLength() {
			return length;
		}
	}
	
	/**
	 * Obiekty tej klasy symbolizują routery pośredniczące w komunikacji z serwerem.
	 *
	 */
	public class IntermediateRouter {
		private InetAddress address;
		private Coordinates coordinates;
		
		/**
		 * Tworzy nowy obiekt routera o podanym adresie ip i współrzędnych geograficznych.
		 * 
		 * @param address adres ip routera
		 * @param coordinates współrzędne geograficzne routera
		 */
		public IntermediateRouter(InetAddress address, Coordinates coordinates) {
			this.address = address;
			this.coordinates = coordinates;
		}
		
		/**
		 * Zwraca adres ip routera.
		 *
		 */
		public InetAddress getAddress() {
			return address;
		}
		
		/**
		 * Zwraca współrzędne geograficzne routera.
		 *
		 */
		public Coordinates getCoordinates() {
			return coordinates;
		}
	}
}