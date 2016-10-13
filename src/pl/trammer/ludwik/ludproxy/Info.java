package pl.trammer.ludwik.ludproxy;

/**
 * Obiekty klasy posiadają metody pozwalające łatwo wypisywać informacje
 * o działaniu programu na strumień wyjścia oraz strumień błędów.
 * <p>
 * Każdy obiekt pamięta nazwę źródła z którego będzie otrzymywać komunikaty,
 * dzięki czemu każda wypisywana informacja opatrzona jest informacją o jej źródle
 * (np. wątku w którym nastąpiło dane zdarzenie).
 * 
 * @author Ludwik Trammer
 */
public class Info {
	private static boolean verbose = false;
	private String source;
	
	/**
	 * Określa czy program ma być "gadatliwy".
	 * Jeśli parametr {@code v} zostanie ustawiony na {@code false} wypisywane będą 
	 * tylko informacje o błędach.
	 * <p>
	 * Zmiany dokonywane za pomocą tej statycznej metody dotyczą wszystkich
	 * obiektów i wszystkich wątków.
	 * 
	 * @param v czy program ma wypisywać informacje nie będące błędami
	 */
	public static void setVerbose(boolean v) {
		verbose = v;
	}
	
	
	/**
	 * Tworzy nowy obiekt powiązany ze źródłem o nazwie {@code source}.
	 * Nazwa źródła będzie wykorzystywana w celach informacyjnych 
	 * przy wypisywaniu komunikatów.
	 * 
	 * @param source nazwa części programu skąd będą pochodzić komunikaty
	 */
	public Info(String source) {
		this.source = source;
	}
	
	/**
	 * Tworzy nowy obiekt dla którego źródłem będzie wątek o numerze {@code thread}.
	 * Numer wątku będzie wykorzystywany w celach informacyjnych przy wypisywaniu komunikatów.
	 * 
	 * @param thread numer wątku, którego dotyczy obiekt.
	 */
	public Info(int thread) {
		this("wątek " + thread);
	}
	
	/**
	 * Podobnie jak {@link #Info(int)}, ale tworzy obiekt dla wątku głównego.
	 */
	public Info() {
		this("wątek główny");
	}
	
	/**
	 * Wypisuje informację na strumień wyjścia. Informacja jest opatrzona dopiskiem o jej źródle pochodzenia.
	 * <p>
	 * Jeśli "gadatliwość" programu została ustawiona na {@code false} przy pomocy {@link #setVerbose(boolean)}
	 * metoda nie robi nic.
	 * 
	 * @param msg wiadomość do wypisania na strumień wyjścia
	 */
	public void say(String msg) {
		if(verbose) System.out.println("[LudProxy, " + source + "] " + msg);
	}
	
	/**
	 * Wypisuje informacje o błędzie na strumień błędów.
	 * Informacja jest opatrzona dopiskiem o numerze wątku, z którego pochodzi.
	 * 
	 * @param msg wiadomość do wypisania na strumień błędów
	 */
	public void err(String msg) {
		System.err.println("[LudProxy, " + source + "] BŁĄD: " + msg);
	}
	
}
