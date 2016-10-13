package pl.trammer.ludwik.ludproxy;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

import javax.swing.SwingUtilities;

import pl.trammer.ludwik.ludproxy.errors.*;
import pl.trammer.ludwik.ludproxy.gui.MainWindow;

/**
 * Klasa, której obiekty obsługują wątki połączenia z klientem (agentem użytkownika).
 * Każdy obiekt klasy odpowiada za jedno połączenie (choć w ramach jednego połączenia,
 * a tym samym jednego wątku pomiędzy klientem i programem może zostać przesłanych
 * wiele zapytań i odpowiedzi).
 * 
 * @author Ludwik Trammer
 *
 */
public class ClientConnection extends Thread {
	Socket clientSocket;
	private static int threadCount = 0;
	private int id;
	private LudInputStream clientInput = null;
	private OutputStream clientOutput = null;
	private MainWindow window;
	
	/**
	 * Konstruktor otrzymuje obiekt klasy Socket symbolizujący
	 * połączenie  klientem, którym obiekt będzie zarządzać, a dodatkowo
	 * nadaje temu obiektowi numer porządkowy
	 * (używany do generowania komunikatów).
	 * 
	 * @param soc Połączenie z klientem
	 * @param window okno GUI wyświetlające informacje o działaniu
	 * serwera proxy (lub {@code null} jeśli program działa w trybie
	 * "no-gui")
	 */
	public ClientConnection(Socket soc, MainWindow window) {
		clientSocket = soc;
		id = ++threadCount;
		this.window = window;
	}

	/**
	 * Uruchomienie wątku obsługi klienta.
	 * Wewnątrz znajduje się pętla, która odbiera kolejne zapytania
	 * od klienta, i podejmuje decyzje czy udzielić mu odpowiedzi
	 * z cache czy odpytując serwer.
	 */
	public void run() {
		Info info = new Info(id);
		info.say("Rozpoczynamy obsługę połączenia w nowym wątku");

		RequestHeader requestHeader=null;
		ServerResponse response = null;
		ServerResponse cached = null;

		try {
			clientInput = new LudInputStream(clientSocket.getInputStream());
			clientOutput = clientSocket.getOutputStream();

			do {
				requestHeader=null;
				response = null;
				cached = null;
				
				try {
					try {

						info.say("Wątek czeka na nowe zapytanie.");

						try {
							//zinterpretój nagłówek zapytania
							requestHeader = new RequestHeader(clientInput);
						} catch (LudInputStream.InputStreamClosed e) {
							info.say("Klient zakończył połączenie.");
							break;
						}

						cached = Cache.get(requestHeader);

						try {
							if(cached!=null && cached.isFresh()) { // świeże w cache
									info.say("Znalazłem " + requestHeader.getUrl() + " w cache!");
									
									if((requestHeader.containsField("If-Match") || requestHeader.containsField("Etag")) && requestHeader.fieldEquals("If-Match", cached.getHeader().getField("Etag"))
											&& requestHeader.fieldEquals("If-Modified-Since", cached.getHeader().getField("Last-Modified"))) {
										/* 
										 * Jest świeże w cache, klient zapytał warunkowo i ten warunek się zgadza.
										 * Odpowiadamy więc 304 Not Modified!
										 */
										clientOutput.write(cached.getHeader()
												.newForRetransmission(cached)
												.setStatus(304)
												.setStatusDescription("Not Modified")
												.getBytes());
									} else { // jest świeże w cache, bez warunku, odpowiadamy 200 z cache
										clientOutput.write(cached.getHeader().newForRetransmission(cached).getBytes());
										clientOutput.write(cached.getBody().getBytes());
									}

							} else { 
								info.say("Proszę " + requestHeader.getHost() + " o " + requestHeader.getPath());
								
								// przekazujemy cached, jeśli jest to postaramy się zrobić z tego zapytanie warunkowe
								response = new ServerResponse(requestHeader, clientInput, clientOutput, cached);
								
								// przeslij do wyswietlenia w GUI
								if(window!=null) {
									final ServerResponse tmp_response = response;
									SwingUtilities.invokeLater(new Runnable() {
									    public void run() {
									    	window.displayConnection(id, tmp_response);
									    }
									  });
								}
					
								info.say("Wysłałem " + requestHeader.getPath() + " (" + response.getHeader().getField("Content-Type") + ") do klienta");
							}
						} catch (LudInputStream.InputStreamClosed e) {
							throw new HttpBadGateway("Serwer docelowy przedwczesnie zakończył połączenie!");
						} catch (LudInputStream.OutputStreamClosed e) {
							info.err("Klient przedwcześnie zakończył połączenie!");
							break;
						}

					} catch(HttpError e) {
						throw e; // złapiemy trochę później
					} catch (IOException e) {
						throw e; // to też - nie ma sensu przekazywać do klienta informacji o zerwaniu połaczenia z klientem
					}   catch (Exception e) {
						e.printStackTrace();
						throw new HttpInternalServerError("Ups! Nastąpił wyjątek " + e + ". Ale wpadka!");
					}
					


				} catch(HttpError e) {
					info.err(e.getMessage());
					clientOutput.write(e.getErrorResponseAsBytes(requestHeader, (response==null ? null : response.getHeader())));
				}

			} while(requestHeader!=null && requestHeader.keepAlive() && clientSocket.isConnected());
			info.say("Kończę wątek.");

		} catch (IOException e) {
			info.err("Problem z połączeniem. Kończę wątek.");
		} finally {
			try {
				clientInput.close();
				clientOutput.close();
				clientSocket.close();
			} catch (IOException ignore) {}
		}
	}
}