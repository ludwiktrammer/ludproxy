package pl.trammer.ludwik.ludproxy.gui;

import java.awt.Image;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.*;

import pl.trammer.ludwik.geo.HttpMap;
import pl.trammer.ludwik.ludproxy.ServerResponse;

/**
 * Główne okno GUI programu. 
 * 
 * @author Ludwik Trammer
 *
 */
public class MainWindow extends JFrame {
	private static final long serialVersionUID = -7519277950968380612L;
	private HttpMap map = new HttpMap();
	private ConnectionsTableModel connectionsModel = new ConnectionsTableModel(map);
	
	/**
	 * Konstruktor tworzący okno.
	 * @param serwer adres pod którym czuwa serwer proxy
	 * (zostanie wyświetlone na stronie wstępnej w GUI)
	 * @param port pod którym czuwa serwer proxy
	 * (zostanie wyświetlone na stronie wstępnej w GUI)
	 */
	public MainWindow(String serwer, int port) {
		setTitle("LudProxy");
		setDefaultCloseOperation(EXIT_ON_CLOSE);

		// ikona
		try {
			Image image = ImageIO.read((MainWindow.class).getResource("icon.png"));
			setIconImage(image);
		} catch (IOException e) {
			System.err.println("Brakuje ikony!");
			System.exit(106);
		}

		// Karty na górze
		JTabbedPane tabs = new JTabbedPane();
		tabs.addTab("Wstęp", new IntroPanel(serwer, port));
		tabs.addTab("Cache", new CachePanel());
		tabs.addTab("Połączenia", new ConnectionsPanel(connectionsModel, map));
		add(tabs);
		
		pack();
	    setSize(720, 640);
	    setVisible(true);
	}
	
	/**
	 * Wyświetla w zakładce "połączenia" informacje o połączeniu
	 * w ramach którego otrzymano odpowiedź {@code response}
	 * <p>
	 * Metoda przekazuje otrzymane informacje modelowi danych tabeli
	 * z połączeniami (korzystając z metody {@link ConnectionsTableModel#add(int, ServerResponse)}
	 * oraz obiektowi mapy (korzystając z metody {@link pl.trammer.ludwik.geo.ServerMap#processResponse(ServerResponse)}.
	 * 
	 * @param threadNum numer wątku, który realizuje to połączenie.
	 * @param response obiekt {@link ServerResponse}, który jest efektem
	 * tego połączenia.
	 */
	public void displayConnection(int threadNum, ServerResponse response) {
		connectionsModel.add(threadNum, response);
		map.processResponse(response);
	}
}
