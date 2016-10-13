package pl.trammer.ludwik.ludproxy;
import java.io.*;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Klasa reprezentująca "ciało" wiadomości HTTP.
 * 
 * @author Ludwik Trammer
 *
 */
public class MessageBody implements java.io.Serializable {
	private static final long serialVersionUID = 8059937303887036643L;
	private final byte[] body;
	
	/**
	 * Tworzy nowy obiekt "ciała" wiadomości HTTP, której treścią jest przekazana
	 * tablica bajtów.
	 * @param bytes treść wiadomości HTTP
	 */
	public MessageBody(byte[] bytes) {
		body = bytes;
	}
	
	/**
	 * Odczytuje treść "ciała" wiadomości HTTP ze strumienia wejściowego, jednocześnie na bierząco
	 * przekazując ją na strumień wyjściowy.
	 * <p>
	 * Metoda przyjmuje obiekty nagłówków, które wykorzystywane są do stwierdzenia kiedy należy
	 * przestać czytać strumień wejściowy, wg. następującego algorytmu:
	 * <p>
	 * <ol>
	 * <li> Jeśli wiadomość jest odpowiedzią na nagłówek HEAD lub ma status 1xx, 203 lub 304 to
	 * "ciało" wiadomości jest puste.
	 * <li> Jeśli wiadomość jest zakodowana przy pomocy Content Encoding {@code chunked} to
	 * będzie odczytywana w "porcjach" zdefiniowanych przez to kodowanie.
	 * <li> Jeśli wiadomość posiada nagłówek {@code Content-Length} to określa on ile
	 * bajtów należy odczytać ze strumienia wejściowego.
	 * <li> Jeśli żadne z powyższych stwierdzeń nie jest prawdziwe, a wiadomość
	 * przekazywana jest przy pomocy nietrwałego połączenia HTTP to jej koniec
	 * będzie oznaczony przez zamknięcie połączenia przez drugą stronę.
	 * </ol>
	 * <p>
	 * Jeśli odbieramy dane z serwera, który wykorzystuje sposób numer 3,
	 * a musi przesłać je do klienta przy pomocy trwałego połączenia HTTP
	 * metoda automatycznie zaczyna kodować dane w locie jako "chunked".
	 * <p>
	 * Niezależnie od sytuacji i formatu odbieranych danych, dane zawsze zapisywane są 
	 * wewnątrz obiektu w formie "ciągłej", ze "zdjętym" Transfer Encoding.
	 * 
	 * @param in strumień wejściowy z którego będą odczytywane dane
	 * @param header nagłówek wiadomości, której "ciało" chcemy odczytać ze strumienia
	 * @param out strumień wyjściowy na który na bierząco będą przekazywane odczytywane dane
	 * @param request nagłówek wiadomości na którą odpowiedzią jest właśnie odczytywana
	 * wiadomość, lub {@code null} jeśli nie dotyczy. 
	 */
	public MessageBody(LudInputStream in, Header header, OutputStream out, RequestHeader request) throws HttpError, IOException {
		boolean noBody = false;
		
		if(header instanceof ResponseHeader) {
			// Odpowiedzi na zapytanie typu HEAD
			// oraz odpowiedzi 1xx, 204, 304 nie mają ciała
			
			ResponseHeader rh = (ResponseHeader) header;
			if((request!=null && request.getMethod().equals("HEAD"))
					|| rh.getStatus()/100==1
					|| rh.getStatus()==204
					|| rh.getStatus()==304) {
				noBody = true;
			}
		}
		
		if(noBody) {
			body = new byte[]{};
			header.setField("Content-Length", 0+"");
		} else if(header.containsField("Transfer-Encoding") && !header.fieldContainsValue("Transfer-Encoding", "identity")) {
			// treść "pokawałkowana" (chunked)
			ByteArrayOutputStream tmp = new ByteArrayOutputStream();
			
			int chunkLen;
			byte[] bytes;
			String chunkHeader="";
			try {
				do {
					// Odczytujemy nagłówek fragmentu, mówiący ile danych
					// mamy do odczytania w danym fragmencie
					while((chunkHeader = in.readLine()) != null && chunkHeader.equals("")) {};

					if(chunkHeader==null) break; //połączenie zakończone
					
					/*
					 * W nagłówku fragmentu, po liczbie bajtów do odczytania mogą być oddzielone
					 * średnikiem rozszerzenia. Nas interesuje wyłącznie liczba.
					 */
					chunkLen = Integer.parseInt(chunkHeader.split("\\s*;")[0], 16);
					
					// odczytujemy dokładnie tyle bajtów ile nam pozwolił nagłówek fragmentu
					bytes = in.forwardAndRead(chunkLen, out, true);
					if(bytes==null) break; //połączenie zakończone
					tmp.write(bytes);	
				} while(chunkLen > 0);
				
				// wyślij nagłówek końca wiadomości typu chunked:
				if(out!=null) out.write((0 + "\r\n\r\n").getBytes());
				
				// Po treści mogą być dodatkowe nagłówki ("trailing headers"):
				if(header.containsField("Trailer")) header.getFieldsFromStream(in);
				
				body = tmp.toByteArray();
				
				// dostosuj nagłówek do tego w jakiej postaci właśnie zapisaliśmy ciało:
				header.removeField("Trailer");
				header.removeField("Transfer-Encoding");
				header.setField("Content-Length", body.length+"");

			} catch(NumberFormatException e) {
				throw new HttpBadGateway("Długość danych określona w nagłówkach chunked nie jest poprawną liczbą (?!)");
			}
			
			
		} else if(header.containsField("Content-Length")) {
			// Pobieramy z strumienia dokładnie tyle ile określono w Content-Length
			try {
				body = in.forwardAndRead(Integer.parseInt(header.getField("Content-Length")), out);
			} catch(NumberFormatException e) {
				throw new HttpBadGateway("Długość danych określona w nagłówku nie jest poprawną liczbą (?!)");
			}			
		} else if(header instanceof ResponseHeader && (
					header.fieldContainsValue("Connection", "close") || (!header.containsField("Connection") && header.getProtocolVersion().equals("1.0")))) {
			// Ojoj. Jaki nieczytelny ten if().
			// Chodzi o to, że jest to odpowiedź w nietrwałym połączeniu,
			// więc o końcu wiadomości można zorientować się po tym, że serwer zamknął połączenie
			
			// Jeśli używamy trwałego połączenia z klietem to trzeba będzie mu to przekazać jako chunked
			boolean usingChunked =  (request!=null && request.keepAlive());
			
			body = in.forwardAndRead(-1, out, usingChunked);	
			
			// wyślij nagłówek końca wiadomości typu chunked:
			if(usingChunked && out!=null) out.write((0 + "\r\n\r\n").getBytes());
			
			// teraz już znamy długość, więc zapiszmy ją w nagłówku!
			header.setField("Content-Length", body.length+"");
		} else if(header instanceof RequestHeader){
			// zapytanie bez ciała, ok
			body = new byte[]{}; 
			header.setField("Content-Length", 0+"");
		} else {
				throw new HttpBadGateway("Otrzymano za mało informacji żeby stwierdzić długość odpowiedzi");
		}
	}
	
	/**
	 * Zwraca "ciało" wiadomości jako tablicę bajtów.
	 * @return tablica bajtów
	 */
	public byte[] getBytes() {
		return body;
	}
	
	/**
	 * Zwraca długość wiadomości (w bajtach)
	 * @return długość wiadomości
	 */
	public int length() {
		return body.length;
	}
}
