package pl.trammer.ludwik.ludproxy;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Klasa abstrakcyjna symbolizująca nagłówek wiadomości HTTP.
 * Klasa przechowuje informacje o wszystkich polach nagłówka
 * i udostępnia wygodne metody pozwalające na dostęp do nich
 * i ich manipulację. Pozwala również generować na podstawie
 * obiektu gotową do wysłania reprezentację tekstową lub bitową
 * nagłówka. 
 * 
 * @author Ludwik Trammer
 * @see ResponseHeader
 * @see RequestHeader
 *
 */
public abstract class Header implements java.io.Serializable {
	private static final long serialVersionUID = 614077006032183225L;
	protected Map<String, String> fields = new HashMap<String, String>();
	protected String protocol_name, protocol_version;
	/**
	 * Data i godzina otrzymania pierwszej linii nagłówka.
	 */
	protected HttpDate received_date;

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
	public Header(LudInputStream in) throws HttpError, IOException {
		String firstLine;
				
		//nagłówek może być poprzedzony dowolną liczbą pustych linii:
		while((firstLine = in.readLine()).equals("")) {}
		
		received_date = new HttpDate();
		
		parseFirstLine(firstLine);
		getFieldsFromStream(in);
	}
	
	/**
	 * Tworzy nowy, pusty obiekt nagłówka. Ustawia bierzącą datę
	 * w polu "Date".
	 * 
	 * @param protocol_name nazwa protokołu (raczej zawsze "HTTP")
	 * @param protocol_version wersja protokołu HTTP (zazwyczaj "1.1")
	 */
	public Header(String protocol_name, String protocol_version) {
		this.protocol_name = protocol_name;
		this.protocol_version = protocol_version;
		
		received_date = new HttpDate();
		
		setField("Date", new HttpDate().toString());
	}
	
	/**
	 * Konstruktor kopiujący. Tworzy nowy obiekt nagłówka,
	 * na podstawie obiektu podanego jako argument.
	 * @param original obiekt na bazie którego zostanie stworzona kopia.
	 */
	public Header(Header original) {
		fields = new HashMap<String, String>(original.fields);
		protocol_name = original.protocol_name;
		protocol_version = original.protocol_version;
		received_date = original.received_date;
	}
	
	/** 
	 * Interpretuje dane znajdujące się w strumieniu wejściowym jako pola nagłówka.
	 * Informacje o polach i ich wartościach zapisuje wewnątrz obiektu.
	 * <p>
	 * Zgodnie z RFC 2616 linie zaczynające się od spacji lub znaku tabulacji interpretuje
	 * jako kontynuację wcześniejszej linii. 
	 * @param in strumień wejściowy
	 * @return sam siebie
	 */
	protected Header getFieldsFromStream(LudInputStream in) throws IOException {
		String line, fieldName=null, fieldValue=null;
		
		// przechodzimy przez linię nagłówka aż do pustej:
		while(!(line = in.readLine()).equals("")) {			
			if(line.charAt(0)==' ' || line.charAt(0)=='\t') {
				if(fieldName!=null) {
					// kontynuacja poprzedniego pola, więc dopisujemy
					setField(fieldName, getField(fieldName) + " " + line.trim());
				}
			} else { // nowe pole
				String[] f = line.trim().split(":\\s*", 2);
				fieldName = f[0];
				fieldValue = f[1];
				
				appenedField(fieldName, fieldValue);
			}
		}
		return this;
	}
	
	/**
	 * Zwraca nazwę i wersję protokołu użytego w wiadomości HTTP
	 * np. "HTTP/1.1"
	 * @return nazwa i wersja protokołu
	 */
	public String getProtocol() {
		return protocol_name + "/" + protocol_version;
	}
	
	/**
	 * Zwraca wersję protokołu używanego w nagłówku.
	 * Np. "1.1" dla {@code HTTP 1.1}.
	 */
	public String getProtocolVersion() {
		return protocol_version;
	}
	
	/**
	 * Zwraca datę i godzinę o której zaczęliśmy otrzymywać nagłówek.
	 */
	public HttpDate receivedDate() {
		return received_date;
	}
	
	/**
	 * Metoda interpretuje pierwszą linię wiadomości HTTP
	 * @param line łańcuch znaków interpretowany jako pierwsza linia wiadomości
	 * @return sam siebie
	 */
	abstract protected Header parseFirstLine(String line)  throws HttpError;
	
	/**
	 * Zwraca wygenerowany łańcuch znaków reprezentujący pierwszą linię nagłówka
	 * wiadomości HTTP, zgodną ze specyfiką danego obiektu reprezentującego nagłówek.
	 * <p>
	 * Na przykład "GET /index.html HTTP/1.1" lub "HTTP/1.1 200 OK".
	 * 
	 * @return tekst pierwszej linii nagłówka HTTP
	 */
	abstract protected String getFirstLine();

	/**
	 * Metoda wprowadza w nagłówku różne zmainy, które wymagane są
	 * zanim proxy prześle wiadomość dalej.
	 * <p>
	 * Między innymi usuwane są nagłówki zdefiniowane w RFC 2616
	 * jako "Hop-by-Hop" (dotyczące jedynie danego połączenia między
	 * dwoma maszynami) i dodawany jest nagłówek "Via" z informacjami
	 * o serwerze proxy.
	 * <p>
	 * Nie jest to jednak metoda publiczna.
	 */
	protected void getReadyForRetransmition() {
		final String[] HOP_BY_HOP_FIELDS = {
				"Connection",
				"Keep-Alive",
				"Proxy-Authenticate",
				"Proxy-Authorization",
				"Te",
				"Upgrade",
				"Proxy-Connection"
		};
		
		// Usuwam nagłówki zdefiniowane jako "Hop-By-Hop"
		for(String h : HOP_BY_HOP_FIELDS) {
			fields.remove(h);
		}
		
		//Używamy HTTP 1.1
		protocol_name = "HTTP";
		protocol_version = "1.1";
		
		// Dodajmy lokalny komputer do pola Via
		String localhost;
		try {
			localhost = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			localhost = "unknown";
		}
		
		if(fields.containsKey("via")) {
			fields.put("Via", fields.get("via") + ", 1.1 " + localhost);
		} else {
			fields.put("Via", getProtocol() + " " + localhost);
		}
		
	}
	
	/**
	 * Statyczna metoda konsekwentnie zmieniająca wielkość znaków
	 * w nazwach pól nagłówków {@code HTTP} na zgodną ze wzorcem.
	 * <p>
	 * Wielkość znaków w nazwach pól nagłówków {@code HTTP} nie ma znaczenia.
	 * Jednak dużo wygodniej do potrzeb wyszukiwania i porównywania
	 * byłoby żeby była spójna i konsekwentna. Metoda normalizuje wielkość
	 * znaków w tekście do wersji zgodnej z najpopularniejszą konwencją
	 * dotyczącą wielkości znaków w nazwach pól {@code HTTP}.
	 * <p>
	 * Na przykład: zarówno "nazwa-pola", "NAZWA-POLA" i "Nazwa-pola"
	 * zostaną zwrócone jako "Nazwa-Pola".
	 * 
	 * @param name tekst do znormalizowania wielkości znaków
	 * @return wersja znormalizowna
	 */
	public static String normalizeFieldName(String name) {
		name = name.trim().toLowerCase();
		String[] parts = name.split("-");
		
		StringBuilder result = new StringBuilder(name.length());
		for(int i=0; i < parts.length; i++) {
			if(i>0) result.append("-");
			result.append(Character.toUpperCase(parts[i].charAt(0)));
			result.append(parts[i].substring(1));
		}
		
		return result.toString();
	}
	
	
	/**
	 * Zwraca mapę zawierającą jako klucze i wartości
	 * nazwy i wartości pól tego nagłówka.
	 * <p>
	 * W większości wypadków zamiast użycia tej metody polecane
	 * jest użycie metod klasy {@link Header} bezpośrednio
	 * manipulujących polami.
	 * 
	 * @return Mapa zawierająca nazwy i wartości pól nagłówka
	 * @see #getField(String)
	 * @see #containsField(String)
	 * @see #setField(String, String)
	 * @see #appenedField(String, String)
	 * @see #removeField(String)
	 * @see #getFieldAsArray(String)
	 * @see #getFieldAsDate(String)
	 * @see Header#fieldContainsValue(String, String)
	 */
	public Map<String, String> getFields() {
		return fields;
	}
	
	/**
	 * Zwraca wartość określonego pola nagłówka. Jeśli
	 * to pole nie jest obecne w danym nagłówku zwracane
	 * jest {@code null}.
	 * <p>
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * @param f nazwa pola nagłówka
	 * @return wartość danego pola lub {@code null}
	 */
	public String getField(String f) {
		return fields.get(normalizeFieldName(f));
	}
	
	/**
	 * Zwraca wartości określonego pola nagłówka jako tablicę. 
	 * Pole nagłówka może zawierać wiele wartości, rodzielonych
	 * przecinkami. Każda wartość reprezentowana jest przez jeden
	 * element tablicy.
	 * <p>
	 * Jeśli podane pole nie istnieje w danym nagłówku zwrócona
	 * zostanie pusta tablica.
	 * <p>
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * @param f nazwa danego pola
	 * @return tablica wartości pola
	 */
	public String[] getFieldAsArray(String f) {
		return getFieldAsArray(f, false);
	}
	
	/**
	 * Działa analogicznie do {@link #getFieldAsArray(String)},
	 * ale jeśli drugi argument ustawiony jest na {@code true}
	 * wszystkie elementy tablicy będą zapisane małymi literami.
	 * 
	 * @param f nazwa pola nagłówka
	 * @param lowercase czy elementy tablicy mają być zapisane
	 * małymi literami
	 * @return tablica wartości danego pola
	 */
	public String[] getFieldAsArray(String f, boolean lowercase) {
		String v = getField(f);
		if(v==null) return new String[0]; // pusta tablica
		if(lowercase) v = v.toLowerCase();
		return v.split(",\\s*");
	}
	
	/**
	 * Interpretuje pole nagłówka jako datę i zwraca obiekt
	 * klasy {@link HttpDate}.
	 * <p>
	 * Pole musi być w jednym z formatów dat opisanych w
	 * RFC 2616. Jeśli format pola nie jest zgodny z 
	 * żadnym z tych formatów metoda zachowa się jakby pole
	 * nie istniało w danym nagłówku (zwrócona zostanie
	 * wartość {@code null}). Więcej informacji na temat 
	 * formatu daty znajduje się w opisie klasy
	 * {@link HttpDate}.
	 * <p>
	 * Jeśli podane pole nie istnieje w danym nagłówku
	 * zostaje zwrócona wartość {@code null}.
	 * <p>
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * 
	 * @param f nazwa pola nagłówka
	 * @return obiekt reprezentujący datę z tego pola
	 * @see HttpDate
	 */
	public HttpDate getFieldAsDate(String f) {
		String da = getField("f");
		if(da==null) return null;
		HttpDate date=null;
		try {
			date = new HttpDate(da);
		} catch (ParseException e) {
			return null;
		}
		return date;
	}	
	
	/**
	 * Zwraca informację czy w danym nagłówku znajduje się
	 * pole o podanej nazwie. Wielkość znaków w nazwie pola
	 * nie ma znaczenia
	 * @param f nazwa pola
	 * @return {@code true} jeśli pole istnieje w danym nagłówku
	 * lub {@code false} jeśli nie istnieje.
	 */
	public boolean containsField(String f) {
		return fields.containsKey(normalizeFieldName(f));
	}
	
	/**
	 * Zwraca {@code true} jeśli podane pole {@code f} zawiera jako jedną
	 * ze swoich wartości podaną wartość {@code v}. Pole nagłówka
	 * może zawierać wiele wartości rozdzielonych przecinkami. 
	 * Metoda zwraca {@code true} jeśli przynajmniej jedna z nich jest zgodna
	 * z wartością podaną jako argument.
	 * <p>
	 * Wielkość znaków nazwy pola oraz poszukiwanej wartości nie mają znaczenia.
	 * 
	 * @param f nazwa sprawdzanego pola
	 * @param v nazwa wartości szukanej w polu
	 * @return {@code true} jeśli {@code v} jest jedną z wartości pola, 
	 * {@code false} w pozostałych sytuacjach.
	 */
	public boolean fieldContainsValue(String f, String v) {
		return Arrays.asList(getFieldAsArray(f, true)).contains(v.toLowerCase());
	}
	
	/**
	 * Nadaje polu nagłówka {@code f} wartość {@code v}. Jeśli wcześniej
	 * istniała w tym polu już jakaś wartość jest ona zastępowana. 
	 * <p>
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * @param f nazwa pola
	 * @param v nowa wartość pola
	 * @return sam siebie
	 * @see #appenedField(String, String)
	 */
	public Header setField(String f, String v) {
		fields.put(normalizeFieldName(f), v.trim());
		return this;
	}
	
	/**
	 * Dododaje wartość {@code v} do pola {@code f}. W jednym polu nagłówka
	 * może znajdować się wiele wartości, rodzielonych przecinkami. Jeśli
	 * w polu {@code f} znajdowała się już wcześniej jakaś wartość, nowa
	 * wartość dopisywana jest po przecinku.
	 * <p>
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * @param f nazwa pola
	 * @param v wartość, która zostanie dopisana do pola
	 * @return sam siebie
	 * @see #setField(String, String)
	 */
	public Header appenedField(String f, String v) {
		if(containsField(f)) {
			fields.put(normalizeFieldName(f), getField(f) + ", " + v.trim());
		} else {
			fields.put(normalizeFieldName(f), v.trim());
		}
		
		return this;
	}
	
	/**
	 * Podane pole zostanie usunięte z nagłówka.
	 * Wielkość znaków w nazwie pola nie ma znaczenia.
	 * @param f nazwa pola do usunięcia
	 * @return sam siebie
	 */
	public Header removeField(String f) {
		fields.remove(normalizeFieldName(f));
		return this;
	}
	
	/**
	 * Metoda zwraca informację czy wartość pola {@code f}
	 * jest identyczna z podaną wartością {@code value}.
	 * <p>
	 * Wielkość znaków w nazwie pola oraz w wartości nie mają znaczenia.
	 * <p>
	 * Jeśli pole nie istnieje w danym nagłówku zostanie
	 * zwrócone {@code true} jeśli {@code value} jest równe
	 * {@code null} i {@code false} w każdym innym wypadku.
	 * @param f nazwa porónywanego pola
	 * @param value wartość do którego porównywana jest wartość
	 * z pola
	 * @return Wartość {@code true} jeśli podana wartość
	 * jest identyczna z wartością w polu (niezależnie od
	 * wielkości liter) lub wartość {@code false} w przeciwnym
	 * wypadku.
	 */
	public boolean fieldEquals(String f, String value) {
		String field_value = getField(f);
		if(field_value==null && value==null) return true;
		if(field_value==null) return false;
		return field_value.toLowerCase().equals(
				value.toLowerCase());
	}

	/**
	 * Metoda zwraca wartość podanej instrukcji w ramach nagłówka {@code Cache-Control}.
	 * Jeśli w ramach danego nagłówka nie istnieje pole {@code Cache-Control}, w polu
	 * nie istnieje zadana instrukcja, lub nie posiada ona żadnej wartości zwracana
	 * jest wartość {@code null}.
	 * <p>
	 * Przykład: Dla nagłówka {@code Cache-Control: private, max-age=60} wywołanie metody
	 * {@code getCacheControlValue("max-age")} zwórci "60", a wywołanie 
	 * {@code getCacheControlValue("private")} zwróci {@code null}.
	 * 
	 * @param name nazwa instrukcji w ramach nagłówka Cache-Control
	 * @return wartość tej instrukcji
	 * @see #fieldContainsValue(String, String)
	 */
	public String getCacheControlValue(String name) {
		String[] list = getFieldAsArray("Cache-Control", true);
		String[] esplit;
		for(String element : list) {
			esplit = element.split("=", 2);
			if(esplit[0].equalsIgnoreCase(name) && esplit.length>1) return esplit[1];
		}
		return null;
	}
	
	/**
	 * Zwraca tekstową reprezentację nagłówka, zgodną z protokołem HTTP
	 * (gotową do wysłania do klienta/serwera). Tekst kończy się pustą linią.
	 */
	public String toString() {
		/*
		 * Metoda tworzy gotowy do wysłania nagłówek
		 */
		StringBuilder result = new StringBuilder();
		result.append(getFirstLine());
		for(String name : fields.keySet()) {
			result.append(name + ": " + fields.get(name) + "\r\n");
		}
		result.append("\r\n");
		
		return result.toString();
	}
	
	/**
	 * Zwraca reprezentację nagłówka identyczną do tej zwracanej przez
	 * {@link #toString()}, ale w formie tablicy bajtów.
	 * @return tablica bajtów gotowa do wysłania przez {@code OutputStream}
	 */
	public byte[] getBytes() {
		return toString().getBytes();
	}
	
	/**
	 * Zwraca datę i godzinę o której otrzymano nagłówek.
	 */
	public HttpDate recivedDate() {
		return received_date;
	}

}