package pl.trammer.ludwik.ludproxy;
import java.io.IOException;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Obiekty klasy reprezentują nagłówki odpowiedzi HTTP.
 * Klasa dziedziczy z abstrakcyjnej klasy {@link Header}.
 * 
 * @author Ludwik Trammer
 * @see Header
 * @see RequestHeader
 */
public class ResponseHeader extends Header {
	private static final long serialVersionUID = -1578621898094739135L;
	protected int status_code;
	protected String status_desc;

	/**
	 * Konstruktor kopiujący.
	 * @param original obiekt na podstawie którego zostanie stworozny nowy obiekt klasy
	 */
	public ResponseHeader(ResponseHeader original) {
		super(original);
		status_code = original.status_code;
		status_desc = original.status_desc;
	}
	
	/**
	 * Konstruktor tworzący nowy obiekt nagłówka odpowiedzi, z pustymi polami nagłówka.
	 * 
	 * @param status_code kod statusu odpowiedzi (np. {@code 200})
	 * @param status_desc opis statusu odpowiedzi (np. {@code OK})
	 */
	public ResponseHeader(int status_code, String status_desc) {
		super("HTTP", "1.1");
		this.status_code = status_code;
		this.status_desc = status_desc;
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
	public ResponseHeader(LudInputStream in) throws HttpError, IOException {
		super(in);
	}

	/**
	 * Interpretuje łańcuch znaków jako pierwszą linię nagłówka zapytania
	 * i zapisuje wewnątrz obiektu uzyskane w ten sposób dane o wersji protokołu (np. {@code HTTP/1.1}) oraz
	 * o numerze i opisie statusu odpowiedzi (np. {@code 200} {@code OK}).
	 * 
	 * Pierwsza linia odpowiedzi HTTP ma postać {@code PROTOKÓŁ KOD OPIS}.
	 * 
	 * @param line tekst do interpretowania jako pierwsza linia nagłówka odpowiedzi HTTP
	 */
	@Override
	protected ResponseHeader parseFirstLine(String line) throws HttpError {
		String[] elements = line.trim().split(" +", 3);
		if(elements.length<2)  {
			throw new HttpBadGateway("Pierwsza linia nagłówka odpowiedzi wygląda dziwnie. Czy to na pewno zapytwanie HTTP?");
		}

		String[] protocol = elements[0].split("/", 2);
		protocol_name = protocol[0].toUpperCase();
		protocol_version = protocol[1];
		
		try {
			status_code = Integer.parseInt(elements[1]);
		} catch (NumberFormatException e) {
			throw new HttpBadRequest("Status odpowiedzi nie jest liczbą!");
		}	
		status_desc = (elements.length>2 ? elements[2] : "");
		
		return this;
	}
	
	/**
	 * @return kod statusu danej odpowiedzi (np. {@code 404})
	 */
	public int getStatus() {
		return status_code;
	}
	
	/**
	 * Zmiana kodu statusu odpowiedzi.
	 * @param status_code nowy kod statusu odpowiedzi
	 * @return zwraca samego siebie
	 */
	public ResponseHeader setStatus(int status_code) {
		this.status_code = status_code;
		return this;
	}
	
	/**
	 * Zmiana opisu statusu odpowiedzi.
	 * @param status_desc nowy opis statusu odpowiedzi
	 * @return zwraca samego siebie
	 */
	public ResponseHeader setStatusDescription(String status_desc) {
		this.status_desc = status_desc;
		return this;
	}
	
	/**
	 * Zwraca nowy obiekt nagłówka odpowiedzi zmodyfikowany w ten sposób,
	 * że jest gotowy do wysłania dalej.
	 * <p>
	 * Między innymi usuwane są nagłówki zdefiniowane w RFC 2616
	 * jako "Hop-by-Hop" (dotyczące jedynie danego połączenia między
	 * dwoma maszynami), dodawany jest nagłówek "Via" z informacjami
	 * o serwerze proxy, Age z wiekiem odpowiedzi w sekundach
	 * (szczególnie ważne gdy wysyłany jest z cache), 
	 * a nagłówek "Connection" (regulujący czy połączenie jest trwałe
	 * czy nietrwałe) ustawiany jest na stosowną wartość.
	 * 
	 * @param sr obiekt odpowiedzi serwera ({@link ServerResponse}), której częścią jest ten nagłówek.
	 * Potrzebny żeby sprawdzić czy dane połączenie jest trwałe.
	 * @return nowy obiekt klasy {@code ResponseHeader}
	 */
	public ResponseHeader newForRetransmission(ServerResponse sr) throws HttpBadGateway {
		ResponseHeader newrh = new ResponseHeader(this);
		newrh.getReadyForRetransmition();
		
		if(sr.getRequest().keepAlive()) {
			newrh.setField("Connection", "Keep-Alive"); // niby nie wyagane, a przeglądarki tego oczekują
		} else {
			newrh.setField("Connection", "close");
		}
		
		if(!newrh.containsField("Content-Length") && sr.getRequest().keepAlive()) {
			/* 
			 * Nie znamy długości wiadomości, będziemy ją będzie przekazywać
			 * klientowi jako chunked!
			 */
			newrh.setField("Transfer-Encoding", "chunked");
		}
		
		newrh.setField("Age", sr.getAge()+"");
		
		
		return newrh;
	}

	/**
	 * Zwraca wygenerowany łańcuch znaków reprezentujący pierwszą linię nagłówka
	 * odpowiedzi HTTP, zgodną ze specyfiką danej odpowiedzi.
	 * <p>
	 * Na przykład "HTTP/1.1 404 Not Found".
	 * 
	 * @return tekst pierwszej linii odpowiedzi HTTP
	 */
	@Override
	protected String getFirstLine() {
		return getProtocol() + " " + status_code + " " + status_desc + "\r\n";
	}

}
