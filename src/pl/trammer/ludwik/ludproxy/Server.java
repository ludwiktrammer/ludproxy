package pl.trammer.ludwik.ludproxy;
import java.io.*;
import java.net.*;
import java.util.List;

import javax.swing.SwingUtilities;

import pl.trammer.ludwik.ludproxy.gui.MainWindow;

import joptsimple.OptionParser;
import joptsimple.OptionSet;

/**
 * Główna klasa serwera proxy. Czeka na połączenia od klienta
 * i uruchamia nowe wątki {@link ClientConnection} do
 * ich obsługi.
 * 
 * @author Ludwik Trammer
 */
public class Server {
	ServerSocket serverSocket;
	MainWindow window;

	/**
	 * Uruchamienie programu.
	 * @param args Argumenty wywołania programu z konsoli tekstowej.
	 * Program przyjmuje argumenty wywołania w następującej postaci:
	 * <p>
	 * {@code [--quiet] [--no-gui] [interfejs [port]]}
	 * <p>
	 * Więcej informacji
	 * na ten temat znajduje się w pliku {@code README.html} w
	 * katalogu głównym projektu.
	 * <p>
	 * Do parsowania argumentów wywołania wykorzystywana jest
	 * biblioteka 
	 * <a href="http://pholser.github.com/jopt-simple/">JOpt Simple</a>.
	 * 
	 */
	public static void main(String[] args) {
		// parsowanie opcji linii komend
		OptionParser parser = new OptionParser();
		parser.accepts("quiet");
		parser.accepts("no-gui");
		OptionSet options = parser.parse(args);
		List<String> arguments = options.nonOptionArguments();

		int serverPort = 8080;
		String serverName = "localhost";
		boolean quiet = options.has("quiet");
		boolean gui = !options.has("no-gui");
		
		if(arguments.size() > 0) serverName = arguments.get(0);
		try {
			if(arguments.size() > 1) serverPort = Integer.parseInt(arguments.get(1));
		} catch(NumberFormatException e) {
			System.err.println("Podany numer portu zupełnie nie jest liczbą!");
			System.exit(103);
		}

		Info.setVerbose(!quiet);

		new Server(serverName, serverPort, gui);
	}

	/**
	 * Uruchamia nowy serwer proxy. W teorii może być ich wiele - każdy stworzony
	 * obiekt klasy {@code Server} to nowy serwer nasłuchujący na własnym porcie.
	 * W praktyce w LudProxy zawsze istnieje tylko jeden obiekt tej klasy (no
	 * bo po co wiele serwerów na raz?).
	 * 
	 * @param serverName adres interfejsu na którym ma działać serwer
	 * @param serverPort port na którym serwer ma nasłuchiwać
	 * @param gui czy program ma wyświetlić graficzne okienko z infromacjami o
	 * działaniu proxy (stan cache, podgląd połączeń itd.)
	 */
	public Server(String serverName, final int serverPort, boolean gui) {
		Info info = new Info();

		info.say("Uruchamianie serwera proxy");

		try {
			final InetAddress serverAddress = InetAddress.getByName(serverName);
			serverSocket = new ServerSocket(serverPort, 50, serverAddress);
			info.say("Stworzono gniazdo do nasłuchu na " + serverAddress + ":" + serverPort);
			info.say("Serwer oczekuje na połączenia");
			
			if(gui) {
				try {
					SwingUtilities.invokeAndWait(new Runnable() {
					      public void run() {
					    	  window = new MainWindow(serverAddress.getHostAddress(), serverPort);
					      }
					    });
				} catch (Exception e) {
					e.printStackTrace();
				}
				
			}
			
			while (true) {
				Socket clientSocket = serverSocket.accept();
				info.say("Nowe połączenie od klienta "
						+ clientSocket.getInetAddress()
						+ ":"
						+ clientSocket.getPort());
				new ClientConnection(clientSocket, window).start();
			}
		} catch (UnknownHostException e) {
			info.err("Podany adres interfejsu do nasłuchu jest nieprawidłowy.");
			System.exit(101);
		} catch (IOException e) {
			info.err("Nastąpił problem połączenia przy uruchamianiu serwera. Upewnij się, że port nie jest zajęty.");
			System.exit(102);
		}
	}
}
