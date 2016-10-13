package pl.trammer.ludwik.ludproxy.errors;
import java.io.*;
import java.text.MessageFormat;
import java.util.Date;

import pl.trammer.ludwik.ludproxy.LudInputStream;
import pl.trammer.ludwik.ludproxy.RequestHeader;
import pl.trammer.ludwik.ludproxy.ResponseHeader;

/**
 * Klasa abstrakcyjna definiujące wyjątki odnoszące się do konkretnych
 * kodów błędów z protokołu HTTP. Taki wyjątek, stworzony gdziekolwiek w
 * wątku obsługi połączenia zostanie automatycznie "złapany" i 
 * przesłany do klienta w formie odpowiedzi HTTP z odpowiednim kodem
 * błędu i stroną informacyjną zawierającą opis błedu.
 * <p>
 * Na przykład jeśli w czasie parsowania nagłówka zapytania wygeneruję
 * wyjątek:
 * <p>
 * {@code throw new HttpInternalServerError("Wszystko mi się pomieszało!");}
 * <p>
 * zostanie on przesłany do klienta jako odpowiedź HTTP ze statusem 500, a
 * użytkownikowi zostanie wyświetlona strona z opisem błędu.
 * <p>
 * Strona generowana jest na podstawie wzoru z pliku {@code error_template.html}.
 * Najłatwiej zobaczyć przykładową stronę błędu wpisując w przeglądarce
 * adres nieistniejącej domeny.
 * 
 * @author Ludwik Trammer
 *
 */
@SuppressWarnings("serial")
public abstract class HttpError extends Exception {
	protected int error_code;
	protected String error_desc;
	protected static String template;
	
	/**
	 * Konstruktor tworzący wyjątek.
	 * @param msg wiadomość, która zostanie wyświetlona na stronie błędu przesłanej do klienta
	 */
	public HttpError(String msg) {
		super(msg);
	}
	
	static {
		// ładuję template odrazu, żeby nie ładować za każdym razem od nowa	
		try {
			LudInputStream is = new LudInputStream(HttpError.class.getResourceAsStream("error_template.html"));
			byte[] error_buffer = is.forwardAndRead(-1, null);
			template = new String(error_buffer, "UTF-8");
			is.close();
		} catch (Exception e) {
			System.err.println("Nie mogę załadować pliku strony błędu!!!");
		} 
	}
	
	/**
	 * Zwraca tekst odpowiedź HTTP odpowiadającą danemu błędowi. Odpowiedź składa się z nagłówków
	 * (w tym odpowiednio ustawionego statusu odpowiedzi) oraz treści, czyli informacyjnej strony HTML
	 * i jest gotowa do bycia wysłaną do klienta.
	 * 
	 * @param requestHeader nagłówek bierzącego zapytania otrzymanego od klienta,
	 * lub wartość {@code null} jeśli niedotyczy. Zostanie wyświetlony w celach
	 * informacyjnych na stronie błedu przekazanej do klienta.
	 * @param responceHeader nagłówek bierzącej odpowiedzi otrzymanego od serwera,
	 * lub wartość {@code null} jeśli niedotyczy. Zostanie wyświetlony w celach
	 * informacyjnych na stronie błedu przekazanej do klienta.
	 * @return tekst odpowiedź HTTP odpowiadającą danemu błędowi.
	 */
	public String getErrorResponse(RequestHeader requestHeader, ResponseHeader responceHeader) {
		Object[] arguments = {
				error_code,
				error_desc,
				getMessage(),
				(requestHeader==null ? "(Nie było zapytania)" : requestHeader),
				(responceHeader==null ? "(Nie było odpowiedzi)" : responceHeader),
				new Date()
		};
		
		String body = MessageFormat.format(template, arguments);
		
		ResponseHeader header = new ResponseHeader(error_code, error_desc);
		try {
			header.setField("Server", "LudProxy")
				.setField("Content-Type", "text/html; charset=utf-8")
				.setField("Connection", "close")
				.setField("Content-Length", body.getBytes("utf8").length + "");	
		} catch (UnsupportedEncodingException e) { e.printStackTrace(); }
		
		return header + body;	
	}
	
	/**
	 * Działa identycznie do {@link #getErrorResponse(RequestHeader, ResponseHeader)}, ale zwraca
	 * odpowiedź jako tablicę bajtów, gotową do bycia wysłaną do klienta przez OutputStream.
	 * 
	 * @param requestHeader nagłówek bierzącego zapytania otrzymanego od klienta,
	 * lub wartość {@code null} jeśli niedotyczy. Zostanie wyświetlony w celach
	 * informacyjnych na stronie błedu przekazanej do klienta.
	 * @param responceHeader nagłówek bierzącej odpowiedzi otrzymanego od serwera,
	 * lub wartość {@code null} jeśli niedotyczy. Zostanie wyświetlony w celach
	 * informacyjnych na stronie błedu przekazanej do klienta.
	 * @return odpowiedź HTTP odpowiadającą danemu błędowi, w formie tablicy bajtów.
	 */
	public byte[] getErrorResponseAsBytes(RequestHeader requestHeader, ResponseHeader responceHeader) {
		byte[] response = {};
		try {
			response = getErrorResponse(requestHeader, responceHeader).getBytes("utf8");
		} catch (UnsupportedEncodingException e) { e.printStackTrace(); }
		return response;
	}
}