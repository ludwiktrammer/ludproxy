package pl.trammer.ludwik.ludproxy;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Klasa tworzy obiekty reprezentuające date, które mogą być tworzone na podstawie
 * dat z nagłówków HTTP. Klasa wspiera wszystkie trzy formaty dat opisane w
 * RFC 2616:
 * <p>
 * <ul>
 * <li>Daty w stylu {@code "Sun Nov  6 08:49:37 1994"} czyli w formacie ANSI C
 * <li>Daty w stylu {@code "Sunday, 06-Nov-94 08:49:37 GMT"} czyli w formacie RFC 1036
 * <li>Daty w stylu {@code "Sun, 06 Nov 1994 08:49:37 GMT"} czyli w formacie RFC 1123,
 * który jest wymagany dla komunikacji w ramach wersji 1.1 protokołu HTTP.
 * </ul>
 * <p>
 * Daty zapisane w formacie RFC 1036, w którym lata zapisywane są przy pomocy
 * tylko dwóch cyfr, interpretowane są jako daty w XXI wieku (czyli w wypadku tego fromatu
 * data z przykładu powyżej to 6 listopada 2094). Jest to rozsądne założenie, bo
 * daty w nagłówkach HTTP zazwyczaj opisują wydarzenia z bardzo niedalekiej
 * przyszłości/przeszłości.
 * <p>
 * Klasa jest luźno inspirowana rozwiązaniami z klasy
 * {@code org.apache.http.impl.cookie.DateUtils}  z projektu
 * <a href="http://hc.apache.org/">Apache HttpComponents</a>, wydanego na licencji
 * Apache License 2.0.
 */
public class HttpDate implements java.io.Serializable{
	private static final long serialVersionUID = 7027410590982523538L;

	private Date date;
	
	private static final SimpleDateFormat FORMAT_RFC1123;
	private static final SimpleDateFormat FORMAT_RFC1036;
	private static final SimpleDateFormat FORMAT_ASCTIME;
	
	//od kiedy liczyć daty w formacie RFC 1036, które zapisują rok jako dwie cyfry
	private static final Date TWO_DIGIT_YEAR_START;

	private static final SimpleDateFormat[] DATE_FORMATS;
	
    static {
    	(FORMAT_RFC1123 = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)).setTimeZone(TimeZone.getTimeZone("GMT"));
    	(FORMAT_RFC1036 = new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US)).setTimeZone(TimeZone.getTimeZone("GMT"));
    	(FORMAT_ASCTIME = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.US)).setTimeZone(TimeZone.getTimeZone("GMT"));

    	DATE_FORMATS = new SimpleDateFormat[] {
    				FORMAT_RFC1123,
    				FORMAT_RFC1036,
    				FORMAT_ASCTIME
    		    };
    	
    	// Dwucyfrowe lata (RFC 1036) są latami w XXI wieku
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeZone(TimeZone.getTimeZone("GMT"));
        calendar.set(2000, Calendar.JANUARY, 1, 0, 0, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        TWO_DIGIT_YEAR_START = calendar.getTime();
        FORMAT_RFC1036.set2DigitYearStart(TWO_DIGIT_YEAR_START);  
    }

    /**
     * Tworzy obiekt daty na podstawie łańcucha znaków w jednym
     * z trzech formatów dat dopuszczanym przez RFC 2616.
     * @param s data zapisana jako tekst
     * @throws ParseException
     */
    public HttpDate(String s) throws ParseException {
    	
    	/* Próbujemy każdego możliwego formatu po kolei */
        for (SimpleDateFormat dateFormat : DATE_FORMATS) {
        
            try {
                date = dateFormat.parse(s);
            } catch (ParseException e) {
                // ten format nie wyszedł, spróbujmy następnego
            }
        }
        
        if(date==null) throw new ParseException("Data z nagłówka wcale nie wygląda jak data!", 0);
    }
    
    /**
     * Tworzy obiekt daty na podstawie obiektu klasy {@code java.util.Date}.
     * @param d obiekt klasy Date
     */
    public HttpDate(Date d) {
    	date = d;
    }
    
    /** 
     * Tworzy nowy obiekt daty, z wewnętrznym czasem ustawionym na moment utworzenia obiektu.
     * Daje to taki sam efekt jak {@code new HttpDate(new Date())}.
     */
    public HttpDate() {
    	date = new Date();
    }
    
    /**
     * Zwraca reprezentację tekstową daty w formacie opisanym w RFC 1123,
     * czyli formacie wymaganym do komunikacji HTTP 1.1.
     */
    public String toString() {
    	return FORMAT_RFC1123.format(date);
    }
 
    /**
     * Zwraca obiekt klasy {@code java.util.Date} reprezentujący tę
     * samą datę co dany obiekt.
     * @return obiekt klasy {@code java.util.Date}
     */
    public Date getDate() {
    	return date;
    }
    
    /**
     * Zwraca datę jako liczbę sekund, która upłynęła od początku 1 stycznia 1970.
     * Bardzo przydatne przy wyliczaniu różnic w sekundach pomiędzy dwoma
     * datami.
     * <p>
     * Obliczenia odbywają się wg. strefy czasowej GMT (która jest obowiązującą
     * strefą czasową w całej komunikacji HTTP).
     * 
     * @return liczba sekund, która upłynęła pomiędzy 1 stycznia 1970, a daną
     * datą.
     */
    public int timeAsInt() {
    	return (int)(getTime()/1000);
    }
    
    /**
     * Działa tak samo jak {{@link #timeAsInt()}, ale zwraca czas w milisekundach.
     */
    public long getTime() {
    	return date.getTime();
    }

    /**
     * Porównywanie dwóch dat. Możliwe jest porównanie z innymi obiektami
     * tej samej klasy oraz obiektami klasy {@code java.util.Date}.
     */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if ((obj instanceof HttpDate)) {
			HttpDate other = (HttpDate) obj;
			if (date.equals(other.date)) return true;
			return false;
		}
		if ((obj instanceof Date)) {
			Date other = (Date) obj;
			if (date.equals(other)) return true;
			return false;
		}
		return false;
	}

    
    
}
