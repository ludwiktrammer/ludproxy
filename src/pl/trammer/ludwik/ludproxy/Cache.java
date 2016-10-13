package pl.trammer.ludwik.ludproxy;
import java.io.File;
import java.io.IOException;
import java.util.*;

import pl.trammer.ludwik.ludproxy.errors.*;

import jdbm.PrimaryHashMap;
import jdbm.RecordManager;
import jdbm.RecordManagerFactory;

/**
 * Klasa pozwalająca umieszczać i pobierać z cache obiekty {@link ServerResponse},
 * czyli odpowiedzi serwera. Stan cache zapamiętywany jest między uruchomieniami programu,
 * dzięki bibliotece <a href="http://code.google.com/p/jdbm2/">JDBM2</a>.
 * <p>
 * Wszystkie metody klasy są statyczne.
 * 
 * @author Ludwik Trammer
 */
public class Cache {
	private static RecordManager recMan;
	private static PrimaryHashMap<String, ServerResponse> storage;
	
	static {
		try {
			recMan = RecordManagerFactory.createRecordManager(System.getProperty("java.io.tmpdir") + File.separator + "LudProxy-cache");
			storage = recMan.hashMap("ServerResponse"); 
		} catch (IOException e) {
			System.err.println("Mam problem z zapisywaniem w katalogu tymczasowym! Obrażam się.");
			System.exit(1);
		}
	}


	/**
	 * Statyczna metoda, która pozwala zapisywać odpowidzi od serwera 
	 * (obiekty {@link ServerResponse}) do późniejszego wykorzystania.
	 * <p>
	 * Odpowiedź nie zostanie zapisana jeśli powstała w reakcji na zapytanie
	 * inne niż {@code GET}, jeśli ma status odpowiedzi inny niż
	 * 200, 203, 300, 301 lub 410 lub zapisywanie w cache zostało
	 * zabronione za pomocą nagłówka {@code Cache-Control}
	 * (chodzi o komendy {@code no-cache}, {@code no-store} i {@code no-private}).
	 * <p>
	 * Zapytania inne niż {@code GET} i {@code HEAD} mogą modyfikować zasób,
	 * którego dotyczą, więc (zgodnie z zaleceniem RFC 2616) próba zapisu
	 * w cache odpowiedzi na takie zapytania spowoduje automatyczne oznaczenie
	 * znajdującej się wcześniej w cache odpowiedzi dotyczących tego zasobu
	 * jako "nieświeżej" (stale).
	 * 
	 * @param header nagłówek zapytania na które została udzielona odpowiedź
	 * @param response obiekt odpowiedzi zapisywanej w cache
	 * @return {@code true} jeśli obiekt został zapisany w cache
	 * lub {@code false} jeśli nie został zapisany.
	 * @see ServerResponse
	 */
	public static boolean put(RequestHeader header, ServerResponse response) throws HttpError, IOException {
		
		if(header.getMethod().equals("HEAD")) {
			ServerResponse sr = storage.get(header.getUrl());
			if(sr!=null) {
				if(sr.getHeader().fieldEquals("Etag", response.getHeader().getField("Etag"))
						|| sr.getHeader().fieldEquals("Last-Modified", response.getHeader().getField("Last-Modified"))) {
					// Etag lub Last-Modified inne niż ostatnio, więc nasza kopia jest nieaktulna! Wywalamy ją!
					storage.remove(header.getUrl()); recMan.commit();
				}
			}
			return false;
		}
		if(!header.getMethod().equals("GET")) {
			/* 
			 * Odbyło się zapytanie inne niż GET lub HEAD i mogło zmodyfikować ten zasób,
			 * więc oznaczamy go jako nieświeży!
			 */
			ServerResponse sr = storage.get(header.getUrl());
			if(sr!=null) { 
				sr.invalidate(); storage.put(header.getUrl(), sr); recMan.commit();
			}
			
			return false;
		}
		if(!Arrays.asList(200, 203, 300, 301, 410).contains(response.getHeader().getStatus())) {
			// Odpowiedzi o statusach poza wymienionymi nie mogą być cachowane!
			return false;
		}
		  
		if(header.fieldContainsValue("Cache-Control", "no-cache")
				|| header.fieldContainsValue("Cache-Control", "no-store")) return false;		
		
		ResponseHeader responseHeader = response.getHeader();
		if(responseHeader.fieldContainsValue("Cache-Control", "no-cache")
				|| responseHeader.fieldContainsValue("Cache-Control", "private")
				|| responseHeader.fieldContainsValue("Cache-Control", "no-store")) return false;
				
		
		/* 
		 * Doszliśmy do końca, czyli zapytanie jest GET, status odpowiedzi to
		 * 200, 203, 300, 301, 410, a Cache-Control się nie sprzeciwia.
		 * Uf! Możemy zapisać!
		 */
		storage.put(header.getUrl(), response);
		recMan.commit();
		return true;
	}
	
	/**
	 * Statyczna metoda, która zwraca przechowywane w cache wcześniejsze odpowiedzi od serwera,
	 * pasujące do nagłówka nowego zapytania.
	 * <p>
	 * Jeśli metoda nowego zapytania różni się od {@code GET} lub {@code HEAD},
	 * korzystanie z cache zostało zabronione w nagłówku nowego zapytania
	 * (poprzez nagłówki {@code Pragma: no-cache} lub {@code Cache-Control: no-cache}
	 * zwrócone zostanie {@code null}.
	 * <p>
	 * Jeśli zapytanie używa metody {@code GET}, a użycie cache nie jest zabronione w
	 * nagłówkach tego zapytania, zostanie zwrócona wcześniejsza odpowiedź serwera
	 * znajdująca się w cache odpowiadająca temu zapytaniu, lub {@code null}
	 * jeśli takiej odpowiedzi w cache nie ma.
	 * <p>
	 * Jeśli zapytanie używa metody {@code HEAD}, a w cache znajduje się zapisana odpowiedź
	 * dotycząca danego zasobu zwrócona zostanie odpowiedź zawierająca nagłówki tej
	 * odpowiedzi, ale z pustą treścią (zgodnie z definicją metody {@code HEAD}).
	 * <p>
	 * Uwaga: odpowiedź zostanie zwrócona niezależnie od jej "świeżości", którą
	 * można sprawdzić korzystając z {@linkplain ServerResponse#isFresh() metody isFresh()}
	 * dostępnej w zwracanym obiekcie klasy {@link ServerResponse}.
	 * 
	 * @param header nagłówek zapytania
	 * @return wcześniejsza odpowiedź serwera pasująca do podanego nagłówka lub {@code null}.
	 * @see ServerResponse#isFresh()
	 * @see  ServerResponse#getAge()
	 */
	public static ServerResponse get(RequestHeader header) throws HttpBadGateway {
		// zwracamy odpowiedzi tylko dla GET i HEAD, inne mogą mieć "efekty uboczne",
		// więc powinny być każdorazowo przesyłane do serwera.
		if(!header.getMethod().equals("GET") && !header.getMethod().equals("HEAD")) return null;
		
		// Czy klient nie zabronił użycia cache?
		if(header.fieldContainsValue("Pragma", "no-cache") || header.fieldContainsValue("Cache-Control", "no-cache")) return null;
		
		ServerResponse response = get(header.getUrl());
		
		// Nie było w cache
		if(response==null) return null;
		
		// Jeśli mamy doczynienia z HEAD to trzeba zwrócić odpowiedź bez treści!
		if(header.getMethod().equals("HEAD")) {
			return new ServerResponse(response, header, null, new MessageBody(new byte[0]));
		}
		
		return new ServerResponse(response, header, null, null);
	}

	/**
	 * Statyczna metoda, która zwraca obiekt odpowiedzi serwera (klasy {@link ServerResponse}
	 * pasujący do podanego adressu URL.
	 * <p>
	 * Podany URL nie jest w żaden sposób normalizowany, więc musi być w formie zwracanej
	 * przez {@link RequestHeader#getUrl()} (m.in. domena musi być zapisana małymi literami).
	 * Wykorzystywanie adresów URL pochodzących z innych źródeł może nie działać zgodnie
	 * z oczekiwaniami.
	 * 
	 * @return odpowiedź serwera zapisaną pod podanym adresem lub {@code null} jeśli takowa
	 * nie istnieje w cache.
	 */
	public static ServerResponse get(String url) {
		return storage.get(url);
	}
	
	/**
	 * Zwraca wszystkie adresy przechowywane w tej chwili w cache. 
	 * @return zbiór obiektów String zawierających adresy stron przechowywanych w Cache
	 */
	public static Set<String> getCachedUrls() {
		return storage.keySet();
	}
	
	/**
	 * Usuwa wszystkie elementy zapisane w cache.
	 */
	public static void clear() throws IOException {
		storage.clear(); recMan.commit();
	}
	
}
