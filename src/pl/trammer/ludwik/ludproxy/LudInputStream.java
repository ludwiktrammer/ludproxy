package pl.trammer.ludwik.ludproxy;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import pl.trammer.ludwik.ludproxy.errors.*;

/**
 * Klasa dziedzicząca klasę {@code InputStream} i {@code FilterInputStream}
 * posiadająca dodatkowe metody przydatne przy odczytywaniu wiadomości HTTP.
 * 
 * @author Ludwik Trammer
 */
public class LudInputStream extends FilterInputStream {
		
	/**
	 * Tworzy nowy obiekt klasy na podstawie obiektu klasy {@code InputStream}.
	 * @param i obiekt klasy {@code InputStream}
	 */
	public LudInputStream(InputStream i) {
		super(i);
	}
		
	/**
	 * Odczytuje zadaną ilość bajtów z strumienia wejściowego, jednocześnie na bierząco 
	 * przekazując odczytywane dane do strumienia wyjściowego.
	 * 
	 * @param len ilość bajtów do odczytania ze strumienia wejściowego.
	 * Jeśli {@code -1} będzie czytać aż do zamknięcia strumienia.
	 * @param output strumień wyjściowy na który mają na bierząco (w czasie odczytywania)
	 * być przekazywane dane, lub {@code null} jeśli dane nie mają być przekazywane na
	 * bierząco.
	 * @return tablica bajtów zawierająca wszystkie odczytane dane.
	 */
	public byte[] forwardAndRead(int len, OutputStream output)  throws IOException, HttpError {
		return forwardAndRead(len, output, false);
	}
	
	/**
	 * Metoda działa analogicznie do {@link #forwardAndRead(int, OutputStream)}, ale dodatkowo
	 * jeśli ostatni parametr ustawiony jest na {@code true} każdą porcję danych odczytanych
	 * ze strumienia wejściowego przekazywaną na strumień wyjściowy poprzedza nagłówkiem 
	 * zawierającym liczbę bajtów przekazywanych w danej porcji. Jest to format 
	 * zgodny z Transfer Encoding {@code chunked} z {@code HTTP 1.1}.
	 * <p>
	 * Dane w zwracanej tablicy zawierają jedynie informacje odczytane bezpośrednio
	 * ze strumienia wejściowego i nie zawierają żadnych dodatkowych nagłówków.
	 * 
	 * @param len ilość bajtów do odczytania ze strumienia wejściowego.
	 * Jeśli {@code -1} będzie czytać aż do zamknięcia strumienia.
	 * @param output strumień wyjściowy na który mają na bierząco (w czasie odczytywania)
	 * być przekazywane dane, lub {@code null} jeśli dane nie mają być przekazywane na
	 * bierząco.
	 * @param writeChunkHeaders czy porcje danych przekazywane na strumień wyjściowy
	 * mają być poprzedzane nagłówkami informującymi o ich wielkości.
	 * @return tablica bajtów zawierająca wszystkie odczytane dane.
	 */
	public byte[] forwardAndRead(int len, OutputStream output, boolean writeChunkHeaders) throws IOException, HttpError {
		byte[] buffer = new byte[1000];
		ByteArrayOutputStream store = new ByteArrayOutputStream();
		int lenRemaining = len;
		
		int limit = (len==-1 || buffer.length < lenRemaining ? buffer.length : lenRemaining);
		
		for(int bytesRead=0; limit > 0 && (bytesRead = in.read(buffer, 0, limit)) != -1; )
		{			
			if(bytesRead>0) {
				store.write(buffer, 0, bytesRead);
				
				try {
					if(writeChunkHeaders) {
						if(output!=null) output.write((Integer.toHexString(bytesRead) + "\r\n").getBytes());
					}
		
					if(output!=null) output.write(buffer, 0, bytesRead);
					
					if(writeChunkHeaders) {
						if(output!=null) output.write(("\r\n").getBytes());
					}
				} catch(IOException e) {
					throw new OutputStreamClosed();
				}
				
				// zmniejszamy długość do przeczytania o to co przeczytaliśmy
				if( len!=-1) lenRemaining -= bytesRead;
				
				limit = (len==-1 || buffer.length < lenRemaining ? buffer.length : lenRemaining);
			}
		}
					
		byte[] ba = store.toByteArray();
		
		// spodziewaliśmy się konkretnych danych, a połączenie
		// na starcie było zamknięte. Wyjątek!
		if (ba.length==0 && len>0) throw new InputStreamClosed();
		
		return ba;
	}

	/**
	 * Odczytuje pojedyńczą linię tekstu ze strumienia wejściowego.
	 * <p>
	 * Zastosowanie tej metody ma tę przeagę nad metodami z klasy
	 * {@code InputReader}, że reszta danych znajdujących się
	 * w strumieniu nadal interpretowana jest jako dane binarne. 
	 * Można więc odczytywać dane jako tekst w miejscach gdzie spodziewamy
	 * się tekstu (nagłówki HTTP) i jako bajty w miejscach gdzie nie znamy
	 * dokładnego typu danych (reszta wiadomości HTTP).
	 * <p>
	 * Jako że RFC 2616 mówi, że jedynym zakończeniem wiersza dopuszczalnym
	 * w nagłówku HTTP jest para CR+LF ta metoda również nie rozpoznaje innych
	 * symboli końca wiersza.
	 * 
	 * @return linia tekstu
	 */
	public String readLine() throws IOException, InputStreamClosed {
		ByteArrayOutputStream lineBytes = new ByteArrayOutputStream();
		int ch;
		boolean accessedStream = false;
		
		while((ch = in.read()) != -1) {
			accessedStream = true;

			if(ch == '\r') {
				/* 
				 * Nowa linia to tylko "\r\n". Tak mówi RFC.
				 * Samotne \r lub \n się nie liczy!
				 */
				if((ch = in.read()) == '\n') { 
					break;
				} else {
					lineBytes.write('\r');
				}
			}
			
			if(ch != -1) lineBytes.write(ch);
		}
		
		if (!accessedStream) throw new InputStreamClosed();
		
		return lineBytes.toString();
	}
	
	/**
	 * Wyjątek informujący o tym, że strumień wejściowy był zamknięty
	 * gdy uważaliśmy, że powinny znajdować się w nim dane do odczytania.
	 * 
	 * @author Ludwik Trammer
	 */
	@SuppressWarnings("serial")
	public class InputStreamClosed extends IOException {
		public InputStreamClosed(String msg) {
			super(msg);
		}
		
		public InputStreamClosed() {
			super();
		}
	}
	
	/**
	 * Wyjątek informujący o tym, że strumień wyjściowy był zamknięty
	 * gdy chcieliśmy wysłać przez niego odpowiedź.
	 * 
	 * @author Ludwik Trammer
	 */
	@SuppressWarnings("serial")
	public class OutputStreamClosed extends IOException {
		public OutputStreamClosed(String msg) {
			super(msg);
		}
		
		public OutputStreamClosed() {
			super();
		}
	}
}
