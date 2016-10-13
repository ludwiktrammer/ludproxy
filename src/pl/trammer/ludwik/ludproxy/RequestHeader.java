package pl.trammer.ludwik.ludproxy;
import java.io.IOException;
import java.net.*;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Obiekty klasy reprezentują nagłówki zapytań HTTP.
 * Klasa dziedziczy z abstrakcyjnej klasy {@link Header}.
 * 
 * @author Ludwik Trammer
 * @see Header
 * @see ResponseHeader
 */
public class RequestHeader extends Header {
	private static final long serialVersionUID = 1603812703637570046L;
	private String method, path, host;
	private int port;

	/**
	 * Konstruktor kopiujący.
	 * @param original obiekt na podstawie którego zostanie stworozny nowy obiekt klasy
	 */
	public RequestHeader(RequestHeader original) {
		super(original);
		method = original.method;
		path = original.path;
		host = original.host;
		port = original.port;
	}
	
	/**
	 * Konstruktor tworzący nowy obiekt nagłówka zapytania, z pustymi polami nagłówka.
	 * 
	 * @param method metoda zapytania, np. {@code GET}
	 * @param path ścieżka zasobu, np. {@code /index.html}
	 * @param host adres serwera na którym znajduje się zasób, np. {@code www.pjwstk.edu.pl}
	 * @param port port na którym czuwa serwer HTTP, np. {@code 80}
	 */
	public RequestHeader(String method, String path, String host, int port) {
		super("HTTP", "1.1");
		this.method = method.toUpperCase();
		this.path = path;
		this.host = host.toLowerCase();
		this.port = port;
	}

	/**
	 * Tworzy obiekt nagłówka na podstawie danych znajdujących się w
	 * strumieniu wejściowym.
	 * <p>
	 * Dane znajdujące się w strumieniu wejściowym interpretowane są
	 * jako nagłówek HTTP i czytane aż do końca nagłówka. W wypadku 
	 * problemów z formatem danych zostanie zwrócony jeden z błędów
	 * z rodziny {@link HttpError}.
	 * 
	 * @param in strumień wejściowy, w którym znajdują się dane nagłówka
	 */
	public RequestHeader(LudInputStream in) throws HttpError, IOException {
		super(in);
	}

	/**
	 * Interpretuje łańcuch znaków jako pierwszą linię nagłówka zapytania
	 * i zapisuje wewnątrz obiektu uzyskane w ten sposób dane o wersji protokołu (np. {@code HTTP/1.1}),
	 * metodzie zapytania (np. {@code POST}), ścieżce (np. {@code /example.php}), hoscie i porcie.
	 * 
	 * Pierwsza linia zapytania HTTP ma postać {@code METODA ŚCIEŻKA PROTOKÓŁ}. W zapytaniach przekazywanych
	 * do serwera proxy {@code ŚCIEŻKA} jest ścieżką absolutną, zawierającą adres hosta (z czego korzystam).
	 * 
	 * @param line tekst do interpretowania jako pierwsza linia nagłówka zapytania HTTP
	 */
	@Override
	protected RequestHeader parseFirstLine(String line) throws HttpError {
		String[] elements = line.trim().split("\\s+", 3);
		if(elements.length!=3) {
			throw new HttpBadRequest("Pierwsza linia nagłówka zapytania wygląda dziwnie. Czy to na pewno zapytanie HTTP?");
		}

		method = elements[0].toUpperCase();
		String[] protocol = elements[2].split("/", 2);
		protocol_name = protocol[0].toUpperCase();
		protocol_version = protocol[1];
	
		if(!protocol_name.equals("HTTP") || !protocol_version.subSequence(0, 2).equals("1."))
			throw new HTTPVersionNotSupported("Zapytanie korzysta z protokołu innego niż HTTP 1.x. Nie umiem!");	
		
		try {
			URL url = new URL(elements[1]);
			
			// przyjmujemy tylko http!
			if(!url.getProtocol().equals("http")) throw new HttpBadRequest("Mam się połączyć przez inny protokół niż http. Nie umiem!");
			
			path = url.getFile();
			host = url.getHost().toLowerCase();
			port = url.getPort();
			if(port==-1) port=80; //domyślny port
			
		} catch (MalformedURLException e) {
			throw new HttpBadRequest("Adres z którym kazano mi się połączyć jest niepoprawny!");
		}
		return this;
	}
	
	/**
	 * @return adres hosta do którego kierowane jest zapytanie
	 */
	public String getHost() {
		return host;
	}
	
	/**
	 * @return numer portu na którym czuwa serwer do którego kierowane jest zapytanie
	 */
	public int getPort() {
		return port;
	}

	/**
	 * @return ścieżka której dotyczy zapytanie (np. {@code /index.html})
	 */
	public String getPath() {
		return path;
	}
	
	/**
	 * @return metoda wykorzystywana w zapytaniu (np. {@code POST} lub {@code GET})
	 */
	public String getMethod() {
		return method;
	}
	
	/**
	 * @return Adres URL zasobu o który prosi zapytanie (np. {@code www.pjwstk.edu.pl/index.html} lub {@code 192.168.1.1:2222/panel/})
	 */
	public String getUrl() {
		return host + (port==80 ? "" : ":" + port) + path;
	}
	
	/**
	 * Zwraca nowy obiekt nagłówka zapytania zmodyfikowany w ten sposób,
	 * że jest gotowy do wysłania dalej.
	 * <p>
	 * Między innymi usuwane są nagłówki zdefiniowane w RFC 2616
	 * jako "Hop-by-Hop" (dotyczące jedynie danego połączenia między
	 * dwoma maszynami), dodawany jest nagłówek "Via" z informacjami
	 * o serwerze proxy, a nagłówek "Connection" (regulujący czy
	 * połączenie jest trwałe czy nietrwałe) ustawiany jest na
	 * stosowną wartość.
	 * @return nowy obiekt klasy {@code RequestHeader}
	 */
	public RequestHeader newForRetransmission() {
		RequestHeader newrh = new RequestHeader(this);
		newrh.getReadyForRetransmition();
		newrh.setField("Connection", "close");
		newrh.setField("Host", host);
		return newrh;
	}
	
	/**
	 * Zwraca wygenerowany łańcuch znaków reprezentujący pierwszą linię nagłówka
	 * zapytania HTTP, zgodną ze specyfiką danego zapytania.
	 * <p>
	 * Na przykład "GET /index.html HTTP/1.1".
	 * 
	 * @return tekst pierwszej linii zapytania HTTP
	 */
	@Override
	protected String getFirstLine() {
		return method + " " + path + " " + getProtocol() + "\r\n";
	}

	/**
	 * Udziela odpowiedzi na pytanie czy nagłówek jest częścią trwałego połączenia HTTP.
	 * <p>
	 * W tej chwili trwałe połączenia obsługiwane są tylko dla {@code HTTP 1.1}, w sytuacjach
	 * gdy zapytanie nie wymusza nietrwałego połączenia przez nagłówek {@code Connection}.
	 * 
	 * @return czy to połączenie jest trwałe?
	 */
	public boolean keepAlive() {
		return getProtocol().equals("HTTP/1.1") && !fieldContainsValue("Connection", "close");
	}
	
}
