package pl.trammer.ludwik.geo;

import java.io.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.trammer.ludwik.ludproxy.Info;

/**
 * Klasa zawiera statyczne metody wywołujące polecenie traceroute (tracert pod Windowsem)
 * i zwracające ich efekt.
 * <p>
 * Choć klasa wspiera zarówno Uniksowie traceroute jak i Windowsowe tracert to bez przeprowadzenia
 * szerszych testów nie da się stwierdzić czy poprawnie wpsółpracuje ze wszystkimi wersjami
 * i implementacjami tych poleceń. Program był z powodzeniem testowany pod
 * Mac OS X 10.7 Lion, Ubuntu 10.04 Lucid Lynx (po doinstalowaniu pakietu "traceroute") i
 * pod Windowsem 7.
 * 
 * @author Ludwik Trammer
 *
 */
public class Traceroute {
	/**
	 * Nazwa polecenia realizującego w systemie funkcjonalność traceroute.
	 * Pod Windowsem ta wartość będzie ustawiona na "tracert". W innych systemach
	 * "traceroute".
	 */
	final static public String EXEC_NAME;
	
	/**
	 * Wyrażenie regularne, które interpretuje pojedyńczą linię wyjścia polecenia traceroute
	 * (lub tracert jeśli klasa używana jest pod Windowsem) i "wyławia" nazwę hosta
	 * (grupa 1) i adres ip (grupa 2)
	 */
	final static public Pattern TRACE_LINE;

	final static private Info info = new Info("traceroute");
	
	static {
		// różny forat zależnie od systemu
		if(System.getProperty("os.name").startsWith("Windows")) {
			TRACE_LINE = Pattern.compile("[0-9]+.+?([0-9a-zA-z-.]+)\\s+\\[([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\]");
			EXEC_NAME = "tracert -w 2000 -h 15";
		} else {
			TRACE_LINE = Pattern.compile("[0-9]+\\s+([0-9a-zA-z-.]+)\\s+\\(([0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3})\\)");
			EXEC_NAME = "traceroute -w 2 -m 15";
		}
	}
	
	/**
	 * Zwraca listę obiektów {@link java.io.InetAddress} zawierających adres ip i nazwę hosta
	 * routerów realizujących połączenia z zadanym serwerem końcowym.
	 * Lista jest ułożona w kolejności hronologicznej.
	 * <p>
	 * Jeśli polecenie typu traceroute nie mogło zostać wykonane zwracana jest pusta lista.
	 * 
	 * @param endPoint adres serwera końcowego
	 * @return lista routerów, które uczestniczą w połączeniu z tym serwerem
	 */
	public static InetAddress[] getHosts(InetAddress endPoint) {
		ArrayList<InetAddress> hosts = new ArrayList<InetAddress>();
		BufferedReader reader = null;
		try {
			Process proc = Runtime.getRuntime().exec(EXEC_NAME + " " + endPoint.getHostAddress());
			reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			String line;
			Matcher match;
			while ((line = reader.readLine()) != null) {
				match = TRACE_LINE.matcher(line);
				if(match.find()) {
					/*
					 * Trochę dziwny sposób na stworzenie obiektu InetAddress, ale dzięki temu
					 * obiekt zawiera zarówno adres ip jak i nazwę hosta (pochodzącą z wyjścia polecenia traceroute),
					 * bez wykonywania dodatkowych zapytań reverse DNS.
					 * 
					 * match.group(1) - nazwa hosta
					 * match.group(2) - adres ip
					 */
					InetAddress addressWithoutName = InetAddress.getByName(match.group(2));
					InetAddress addressWithName = InetAddress.getByAddress(match.group(1), addressWithoutName.getAddress());
					hosts.add(addressWithName);
				}
			}
			if(reader!=null) reader.close();
		} catch (IOException e) {
			info.err("Nie można było wykonać polecenia traceroute. Czy w systemie jest zainstalowane narzędzie traceroute?");
			return (InetAddress[]) hosts.toArray(new InetAddress[]{});
		}
		return (InetAddress[]) hosts.toArray(new InetAddress[]{});
	}
	
}
