package pl.trammer.ludwik.geo;

import java.io.IOException;
import java.net.InetAddress;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pl.trammer.ludwik.ludproxy.Info;
import pl.trammer.ludwik.ludproxy.RequestHeader;
import pl.trammer.ludwik.ludproxy.ServerResponse;
import pl.trammer.ludwik.ludproxy.errors.HttpError;

/**
 * Klasa służąca do ustalania współrzędnych geograficznych powiązanych z adresem ip.
 *
 */
public class Geolocation {
	final static private Pattern PATTERN = Pattern.compile("new GLatLng\\((-?[0-9.]+), (-?[0-9.]+)\\)");
	final static private Pattern ADRESIP_MESSAGE = Pattern.compile("<h2 align=\"center\" class=\"ip\">(.+?)</h2>");
	final static private Info info = new Info("geolocation");
	
	/**
	 * Na podstawie adresu ip zwraca współrzędne geograficzne przypisane do tego adresu.
	 * Jeśli z jakiegokolwiek powodu ustalenie współrzędnych było niemożliwe zwracana jest
	 * wartość {@code null}.
	 * <p>
	 * Ta implementacja ustala współrzędne korzystając z serwisu adres-ip.pl. Komunikacja
	 * z serwerem odbywa się przy pomocy klas reprezentujących elementy HTTP z LudProxy - 
	 * {@link pl.trammer.ludwik.ludproxy.RequestHeader} i {@link pl.trammer.ludwik.ludproxy.ServerResponse}.
	 * Just for fun.
	 * 
	 * @param ip sprawdzany adres ip
	 * @return współrzedne geograficzne lub {@code null}
	 */
	public static Coordinates getCoordinatesFromIP(InetAddress ip) {
		/*
		 * Zbudujmy zapytanie. Normalnie zapytania HTTP należałoby realizować
		 * specjalnymi klasami wbudowanymi do Javy, ale skoro już zrobiliśmy na potrzeby
		 * Proxy własne  klasy obsługujące elementy połączenia HTTP to czemu się nimi
		 * jeszcze trochę nie pobawić?
		 */
		RequestHeader requestHeader = new RequestHeader("POST", "/geolokalizacja/wynik", "www.adres-ip.pl", 80);
		
		/*
		 * Dodaję nagłówki specyficzne dla tego połączenia.
		 * Metody tworzące nagłówek i przekazujące go do serwera zapewnią
		 * prawidłowe wartości podstawowych nagłówków:
		 * Date, Content-Length, Connection, Host. Nie muszę ich ręcznie dodawać.
		 */
		requestHeader.setField("Content-Type", "application/x-www-form-urlencoded")
		.setField("User-Agent", "LudProxy/0.1");

		byte[] requestBody = ("ip="+ip.getHostAddress()).getBytes();

		try {
			ServerResponse response = new ServerResponse(requestHeader, requestBody);
			return parseAdresIpPl(new String(response.getBody().getBytes()));
		} catch (HttpError e) {
			info.err("Problem z pobieraniem geolokacji:" + e.getMessage()
					+ "\n" + ip.getHostName() + " nie zostanie wyświetlony na mapie!");
			return null;
		} catch (IOException e) {
			info.err("Błąd połączenia przy pobieraniu geolokacji!"
					+ "\n" + ip.getHostName() + " nie zostanie wyświetlony na mapie!");
			return null;
		}
	}
		
	/**
	 * Zwraca współrzedne geograficzne przypisane do adresu z którego aktualnie komunikuje
	 * się lokalny komputer.
	 * Jeśli z jakiegokolwiek powodu ustalenie współrzędnych było niemożliwe zwracana jest
	 * wartość {@code null}.
	 * 
	 * @return wspólrzędne lokalnego komputera lub {@code null}
	 */
	public static Coordinates getMyLocation() {
		RequestHeader requestHeader = new RequestHeader("POST", "/", "www.adres-ip.pl", 80);
		requestHeader.setField("User-Agent", "LudProxy/0.1");

		try {
			ServerResponse response = new ServerResponse(requestHeader, new byte[]{});
			return parseAdresIpPl(new String(response.getBody().getBytes()));
		} catch (HttpError e) {
			info.err("Problem z pobieraniem geolokacji dla localhost! " + e.getMessage());
			return null;
		} catch (IOException e) {
			info.err("Błąd połączenia przy pobieraniu geolokacji dla localhost! " + e.getMessage());
			return null;
		}
	}
	
	/**
	 * Zwraca współrzędne geograficzne na podstawie kodu strony. 
	 * 
	 * @param s kod strony zawierającej mapę Google Maps z oznaczeniem współrzędnych geograficznych
	 * @return współrzędne geograficzne lub {@code null}
	 */
	private static Coordinates parseAdresIpPl(String s) {
		Matcher find = PATTERN.matcher(s);

		if(find.find()) {
			double latitiude = Double.parseDouble(find.group(1));
			double longitiude = Double.parseDouble(find.group(2));

			// adres-ip.pl zwraca punkt (0, 0) jeśli nie ma danego adresu w swojej bazie
			if(latitiude==0 && longitiude==0)  {
				info.err("Adres-ip.pl nie zna położenia punktu. Zostanie on pominięty.");
				return null;
			}
				
			
			return new Coordinates(latitiude, longitiude);
		}
		
		Matcher message = ADRESIP_MESSAGE.matcher(s);
		if(message.find()) {
			info.say("Adres-ip.pl zwróciło informację: \"" + message.group(1) + "\". To ip zostanie pominięte.");
		} else {
			info.err("Niespodziewany format odpowiedzi od adres-ip.pl");
		}
		return null;
	}
}
