package pl.trammer.ludwik.ludproxy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Klasa, której obiekty symbolizują odpowiedzi serwera na zapytania HTTP.
 * Każda odpowiedź składa się z nagłówka, ciała odpowiedzi, a także informacji 
 * o nagłówku zapytania na które jest to odpowiedź.
 * <p>
 * Zapisywane są również informacje o czasie wysłania zapytania 
 * i czasie otrzymania odpowiedzi (potrzebne potem do obliczania
 * wieku odpowiedzi).
 * 
 * @author Ludwik Trammer
 */
public class ServerResponse implements java.io.Serializable {
	private static final long serialVersionUID = -3716084520702669165L;
	private RequestHeader request;
	private ResponseHeader header;
	private MessageBody body;
	private boolean invalidated = false;
	private InetAddress serverIp;
	boolean conditionalRequest = false; // czy wysłaliśmy zapytanie warunkowe
	boolean conditionalRequestVerified = false; // czy serwer odpowiedział 403 Not Motified
	
	/**
	 * Data i godzina o której poproszono zdalny serwer o odpowiedź.
	 */
	private HttpDate request_sent;
	
	/**
	 * Konstruktor tworzy obiekt odpowiedzi serwera wysyłając do niego zapytanie,
	 * a następnie zapisując odpowiedź i informacje o niej wewnątrz obiektu.
	 * 
	 * @param request Nagłówek zapytania, które zostanie wysłane do serwera. Tworzony obiekt
	 * będzie symbolizował odpowiedź serwera na to zapytanie.
	 * @param requestBody Ciało zapytania, które zostanie wysłane razem z nagłówkiem
	 * (pole {@code Content-Length} w nagłówku zostanie automatycznie ustawione na długość ciała).
	 */
	public ServerResponse(RequestHeader request, byte[] requestBody) throws HttpError, IOException {
		/* brzydko to trochę wygląda, ale dzięki takiemu zagnieżdżeniu odwołanie do konstruktora jest
		 * w pierwszej linii i jednocześnie pole "Content-Length" zostaje zmodyfikowane przed
		 * wywołaniem drugiego konstruktora.
		 */
		
		this((RequestHeader)(request.setField("Content-Length", requestBody.length+"")),
				new LudInputStream(new ByteArrayInputStream(requestBody)), null, null);
	}
	
	/**
	 * Konstruktor działa podobnie jak {@link #ServerResponse(RequestHeader, byte[])}, ale dodatkowo
	 * przyjmuje strumień wejściowy klienta (z którego zostanie pobrane ciało zapytania) i strumień
	 * wyjściowy klienta (na który zostanie przesłana przetworzona odpowiedź serwera).
	 * 
	 * @param request Nagłówek zapytania, które zostanie wysłane do serwera. Tworzony obiekt
	 * będzie symbolizował odpowiedź serwera na to zapytanie.
	 * @param clientInput strumień wejściowy klienta z którego zostanie pobrane
	 * ciało zapytania klienta (jeśli istnieje)
	 * @param clientOutput strumień wyjściowy klienta na który zostanie przekazana
	 * odpowiedź serwera na zapytanie (lub {@code null} jeśli ma nigdzie nie przekazywać).
	 */
	public ServerResponse(RequestHeader request, LudInputStream clientInput, OutputStream clientOutput) throws HttpError, IOException {
		this(request, clientInput, clientOutput, null);
	}
	
	/**
	 * Konstruktor działa tak samo jak {@link #ServerResponse(RequestHeader, LudInputStream, OutputStream)}, ale
	 * dodatkowo jako czwarty argument pobiera istniejący obiekt {@link ServerResponse} będący pobraną z cache
	 * odpowiedzią serwera na podobne zapytanie.
	 * <p>
	 * Konstrukcja ta jest wykorzystywana do zapytań warunkowych, czyli w sytuacjach gdy pobrana z cache odpowiedź
	 * straciła już swoją "świeżość" (gdyby nadal była aktualna zostałaby zwrócona bezpośrednio). Jeśli argument
	 * {@code cached} jest obecny konstruktor zmienia zapytanie w zapytanie warunkowe, dodając odpowiednie
	 * wartości do nagłówków "If-Match" i "If-Modified-Since". Jeśli serwer odpowie wiadomością o kodzie 304
	 * odpowiedź do klienta zostanie przygotowana na podstawie (uważanej wcześniej za "nieświeżą") odpowiedzi
	 * z cache. Wszystkie pola nagłówków istniejące w nowej odpowiedzi zastępują jednak odpowiadające im pola nagłówków
	 * z odpowiedzi starej.
	 * 
	 * @param request Nagłówek zapytania, które zostanie wysłane do serwera. Tworzony obiekt
	 * będzie symbolizował odpowiedź serwera na to zapytanie.
	 * @param clientInput strumień wejściowy klienta z którego zostanie pobrane
	 * ciało zapytania klienta (jeśli istnieje)
	 * @param clientOutput strumień wyjściowy klienta na który zostanie przekazana
	 * odpowiedź serwera na zapytanie (lub {@code null} jeśli ma nigdzie nie przekazywać).
	 * @param cached obiekt wcześniejszej odpowiedzi na podobne zapytanie, który posłuży jako
	 * wzorzec do zapytania warunkowego. Wartość {@code null} jeśli zapytanie nie ma być warunkowe.
	 */
	public ServerResponse(RequestHeader request, LudInputStream clientInput, OutputStream clientOutput, ServerResponse cached) throws HttpError, IOException {
		this.request = request;
		Socket serverSocket=null;
		LudInputStream serverInput=null;
		OutputStream serverOutput=null;
		
		try{
			// połącz z serwerem
			serverIp = InetAddress.getByName(request.getHost());
			serverSocket = new Socket(serverIp, request.getPort());
			
			serverInput = new LudInputStream(serverSocket.getInputStream());
			serverOutput = serverSocket.getOutputStream();
			
			if(cached!=null && !request.containsField("If-Modified-Since") && !request.containsField("If-Match")) {
				/* 
				 * Mamy tę stronę w cache, ale jest już nieświeża.
				 * Po upewnieniu się, że klient nie robi warunkowego zapytania
				 * sami takie zrobimy.
				 */
				if(cached.getHeader().containsField("Etag")) {
					request.setField("If-Match", cached.getHeader().getField("Etag"));
					conditionalRequest = true;
				}
				if(cached.getHeader().containsField("Last-Modified")) {
					request.setField("If-Modified-Since", cached.getHeader().getField("Last-Modified"));
					conditionalRequest = true;
				}	
			}

			// przekaż serwerowi nagłówek
			serverOutput.write(request.newForRetransmission().getBytes());

			// oraz treść zapytania
			new MessageBody(clientInput, request, serverOutput, null);
			
			//zanotuj czas zapytania
			request_sent = new HttpDate();

			//zinterpretuj nagłówek odpowiedzi
			header = new ResponseHeader(serverInput);
			
			if(conditionalRequest && header.getStatus()==304) {
				/* Nasza stara wersja z cache jest znowu świeża :) */
				
				conditionalRequestVerified = true;
				
				// Nagłówki z nowej odpowiedzi są ważniejsze od tych ze starej.
				// Zastępujemy. Po czym stary nagłówek staje się nowym.
				cached.getHeader().getFields().putAll(header.getFields());
				
				// Nowa data odbioru
				cached.getHeader().received_date = header.received_date;
				
				header = cached.getHeader();
				body = cached.getBody();
				
				// przekaż klientowi nagłówek
				if(clientOutput!=null) clientOutput.write(header.newForRetransmission(this).getBytes());
				
				// przekaż klientowi treść
				if(clientOutput!=null)clientOutput.write(body.getBytes());				
			} else {
				/* Dostaliśmy zupełnie nową wersję do przekazania klientowi.
				 * Przekażmy.
				 */
				
				// przekaż klientowi nagłówek
				if(clientOutput!=null) clientOutput.write(header.newForRetransmission(this).getBytes());

				// odczytaj dane od serwera, na bierząco przekazując do klienta
				body = new MessageBody(serverInput, header, clientOutput, request);
			}
			
			// Sami siebie umieszczamy w cache!
			Cache.put(request, this);
	
		} catch (UnknownHostException e) {
			throw new HttpBadRequest("Nie znaleziono serwera o podanym adresie.");
		} finally {
			// zamknijmy połączenie z serwerem
			if(serverInput!=null) serverInput.close();
			if(serverOutput!=null) serverOutput.close();
			if(serverSocket!=null) serverSocket.close();
		}
	}

	/**
	 * Konstruktor kopiujący, pozwalający opcjonalnie podmienić poszczególne
	 * elementy składowe przy kopiowaniu.
	 * 
	 * @param original obiekt na bazie którego stworzony (skopiowany) zostanie 
	 * nowy obiekt.
	 * @param request obiekt {@link RequestHeader}, który stanie się nagłówkiem
	 * zapytania w nowym obiekcie odpowiedzi serwera, lub {@code null} jeśli
	 * nagłówek zapytania ma zostać skopiowany z obiektu {@code original}.
	 * @param header obiekt {@link ResponseHeader}, który stanie się nagłówkiem
	 * odpowiedzi w nowym obiekcie odpowiedzi serwera, lub {@code null} jeśli
	 * nagłówek odpowiedzi ma zostać skopiowany z obiektu {@code original}.
	 * @param body obiekt {@link MessageBody}, który stanie się ciałem
	 * odpowiedzi w nowym obiekcie odpowiedzi serwera, lub {@code null} jeśli
	 * ciało odpowiedzi ma zostać skopiowany z obiektu {@code original}.
	 */
	public ServerResponse(ServerResponse original, RequestHeader request, ResponseHeader header, MessageBody body) {
		this.request = (request!=null ? request : new RequestHeader(original.request));
		this.header = (header!=null ? header : new ResponseHeader(original.header));
		this.body = (body!=null ? body : original.body);
		this.invalidated = original.invalidated;
		this.request_sent = original.request_sent;
	}

	/**
	 * Zwraca obiekt {@link ResponseHeader}, będący częścią składową
	 * obiektu odpowiedzi serwera
	 */
	public ResponseHeader getHeader() {
		return header;
	}

	/**
	 * Zwraca obiekt {@link MessageBody}, będący częścią składową
	 * obiektu odpowiedzi serwera
	 */
	public MessageBody getBody() {
		return body;
	}

	/**
	 * Zwraca obiekt {@link RequestHeader}, będący częścią składową
	 * obiektu odpowiedzi serwera
	 */
	public RequestHeader getRequest() {
		return request;
	}

	/**
	 * Pozwala oznaczyć odpowiedź jako "nieświeżą", nawet jeśli
	 * inne parametry odpowiedzi wskazywałyby na jej świeżość.
	 * 
	 * @return zwraca sam siebie
	 */
	public ServerResponse invalidate() {
		invalidated = true;
		return this;
	}
	
	/**
	 * Sprawdza czy odpowiedź jest "świeża", czyli czy można ją przesłać do
	 * klienta prosto z cache, bez wykonywania zapytania warunkowego.
	 * <p>
	 * Odpowiedź jest uznawana za "świeżą" jeśli:
	 * 
	 * <ol>
	 * <li> Jej świeżość nie została "unieważniona" metodą {@link #invalidate()}.
	 * <li> Nie zawiera instrukcji {@code must-revalidate} w nagłówku "Cache-Control".
	 * <li> Jej {@linkplain #getAge() wiek} nie przewyższa maksymalnego wieku wymaganego
	 * przez klient i serwer (wybierana jest bardziej restrykcyjna wartość z tych dwóch),
	 * po wzięciu poprawki na instrukcje {@code max-stale} i {@code min-fresh} od klienta.
	 * Instrukcja {@code max-stale} pozwala traktować odpowiedź jako "świeżą", jeżeli okres od
	 * upłynięcia jej czasu świeżości nie przekracza {@code max-stale} sekund; {@code min-fresh}
	 * nakazuje traktować odpowiedź jako nieświeżą, jeżeli pozostało jej mniej niż
	 * {@code min-fresh} sekund okresu świeżości.
	 * </ol>
	 * <p>
	 * Jeśli ani serwer ani klient nie przesłali informacji o maksymalnym dopuszczalnym
	 * wieku odpowiedzi (przez instrukcje {@code max-age} lub nagłówek {@code Expires}) to
	 * jako maksymalny wiek odpowiedzi uznawane jest 10% czasu, który minął od ostatniej
	 * modyfikacji zasobu. Jeśli data ostatniej modyfikacji zasobu nie jest znana uznaje się,
	 * że odpowiedź jest świeża przez 10 minut.
	 * 
	 * @return {@code true} jeśli odpowiedź jest uznawana za świeżą i {@code false} w
	 * przeciwnym wypadku.
	 * @see #getAge()
	 * @see #invalidate()
	 */
	public boolean isFresh() {
		if(invalidated) return false;
		if(header.fieldContainsValue("Cache-Control", "must-revalidate")) return false;
		
		Integer server_max_age = null; 
		Integer client_max_age = null;
		Integer reall_max_age = null;
		Integer min_fresh = null ;
		Integer max_stale = null;
		
		try {
			server_max_age = Integer.parseInt(header.getCacheControlValue("max-age"));
		} catch(NumberFormatException e) { /* zostaje null */ }
		
		if(server_max_age==null) {
			// jeśli nie udało się z nagłówkiem Cache-Control to wtedy (i tylko wtedy!)
			// możemy spróbować z nagłówkiem Expires
			HttpDate Expires = header.getFieldAsDate("Expires");
			if(Expires!=null) {
				server_max_age = header.getFieldAsDate("Expires").timeAsInt() - new HttpDate().timeAsInt();
			}
		}
		
		try {
			client_max_age = Integer.parseInt(request.getCacheControlValue("max-age"));
		} catch(NumberFormatException e) { /* zostaje null */ }
		
		try {
			min_fresh = Integer.parseInt(request.getCacheControlValue("min-fresh"));
		} catch(NumberFormatException e) { /* zostaje null */ }
		
		try {
			max_stale = Integer.parseInt(request.getCacheControlValue("max-stale"));
		} catch(NumberFormatException e) { /* zostaje null */ }
		
		if((server_max_age==null) && (client_max_age==null)) {
			/* Ani klient ani serwer nie podał nam maksymalnego wieku.
			 * Musimy sami sobie coś wymyśleć.
			 * Wymyślmy więc, że maksymalny wiek to 10% czasu, który
			 * minął od ostatniej modyfikacji.
			 * Lub 10 minut, jeśli nie znamy daty ostatniej modfyikacji.
			 */
		
			HttpDate LastModified = header.getFieldAsDate("Last-Modified");
			
			if(LastModified==null) {
				// Nie znamy daty ostaniej modyfikacji. Sztywno ustawiamy na 10 minut.
				reall_max_age = 60*10;
			} else {
				// 10% czasu od ostatniej modyfikacji
				reall_max_age = (int)((new HttpDate().timeAsInt()-LastModified.timeAsInt())*0.10);
			}
		} else if((server_max_age!=null) && (client_max_age!=null)) { 
			/* 
			 * Zarówno klient jak i serwer określiły max-age.
			 * Wybierzmy bardziej restrykcyjną wersję.
			 */
			reall_max_age = Math.min(server_max_age, client_max_age);
		} else if(server_max_age!=null) { // tylko serwer
			reall_max_age = server_max_age;
		} else { // tylko klient
			reall_max_age = client_max_age;
		}
		
		return reall_max_age + (max_stale!=null ? max_stale : 0) > getAge() - (min_fresh!=null ? min_fresh : 0);
	}

	/** 
	 * Obliczanie wieku odpowiedzi. Algorytm jest dokładną kopią
	 * <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.2.3">algorytmu
	 * obliczania wieku opisanego w RFC 2616</a>.
	 * <p>
	 * Wiek obliczany jest na podstawie daty z nagłówka "Date" odpowiedzi,
	 * momentu wysłania zapytania, momentu otrzymania odpowiedzi, nagłówka
	 * "Age" odpowiedzi i bierzącego czasu.
	 * 
	 * @return wiek wiadomości w sekundach, zgodny z definicją wieku z RFC 2616
	 * @see #isFresh()
	 */
	public int getAge() {
		int age_value; 
		int date_value = (header.getFieldAsDate("Date")==null ? new HttpDate().timeAsInt() : header.getFieldAsDate("Date").timeAsInt());
		int request_time = request_sent.timeAsInt();
		int response_time = header.receivedDate().timeAsInt();
		int now = new HttpDate().timeAsInt();
		try {
			age_value = Integer.parseInt(header.getField("Age"));
		} catch(NumberFormatException e) {
			age_value = 0;
		}

		int apparent_age = Math.max(0, response_time - date_value);
		int corrected_received_age = Math.max(apparent_age, age_value);
		int response_delay = response_time - request_time;
		int corrected_initial_age = corrected_received_age + response_delay;
		int resident_time = now - response_time;
		int current_age = corrected_initial_age + resident_time;

		return current_age;
	}
	
	/**
	 * Zwraca datę i godzinę o której poproszono o bieżącą odpowiedź.
	 */
	public HttpDate requestSentDate() {
		return request_sent;
	}
	
	/**
	 * Zwraca ip serwera z którego została pobrana bierząca odpowiedź.
	 */
	public InetAddress getServerIp() {
		return serverIp;
	}
	
	/**
	 * Zwraca prawdę jeśli bieżący obiekt powstał z połączenia
	 * ciała odpowiedzi pobranej z cache i odpowiedzi serwera na zapytanie warunkowe.
	 * 
	 * @return {@code true} jeśli LudProxy zadało serwerowi zapytanie warunkowe
	 * i otrzymało odpowiedź o statusie 304, {@code false} w przeciwnym wypadku.
	 */
	public boolean verifiedConditional() {
		return conditionalRequestVerified;
	}
	
	/**
	 * Zwraca prawdę jeśli zapytanie na które jest to odpowiedź było zapytaniem
	 * warunkowym wygenerowanym przez program (niezaleznie od tego czy serwer
	 * odpowiedział 2003 czy 304).
	 * 
	 */
	public boolean wasConditional() {
		return conditionalRequest;
	}
	
	/**
	 * Zwraca czas, który upłynął pomiędzy momentem gdy zakończyliśmy wysyłać zapytanie
	 * do serwera i momentem gdy zaczęliśmy od niego otrzymywać odpowiedź.
	 * 
	 * @return opóźnienie odpowiedzi w sekundach
	 */
	public double getLatency() {
		return (header.receivedDate().getTime() - request_sent.getTime())/1000.;
	}
	 /**
	  * Zwraca wielkość ciała wiadomości (w bajtach). Wielkość zwracana jest na podstawie
	  * pola "Content-Length" w nagłówku. Nasze metody odczytywania odpowiedzi gwarantują,
	  * że będzie tam poprawna wartość, więc jest to metoda wystarczająca.
	  * 
	  */
	public int getContentLength() {
		return Integer.parseInt(header.getField("Content-Length"));
	}
	
}