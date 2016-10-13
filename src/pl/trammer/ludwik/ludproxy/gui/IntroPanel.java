package pl.trammer.ludwik.ludproxy.gui;

import java.awt.FlowLayout;

import javax.swing.*;

/**
 * {@link JPanel} zarządzający wyglądem zakładki ze wstępnymi informacjami.
 * Wyświetla m.in. dane o adresie i porcie na którym nasłuchuje serwer. 
 * 
 * @author Ludwik Trammer
 */
public class IntroPanel extends JPanel {
	private static final long serialVersionUID = -3259769525577163672L;

	public IntroPanel(String serwer, int port) {
		super();
		setLayout(new FlowLayout(FlowLayout.CENTER));
		JLabel aboutLabel = new JLabel(
				"<html>" +
				"<center><h1><font color=\"green\">Lud</font>Proxy</h1>" +
				"<p><em>It works... most of the time!</em></p></center>" +
				"<p style=\"margin-top:20px;\">Pamiętaj, że żeby korzystać z LudProxy musisz skonfigurować " +
				"ustawienia proxy w swojej preglądarce:<br><br></p>" +
				"<p><b>Serwer:</b> " + serwer + "</p>" +
				"<p><b>Port:</b> " + port + "</b></p>" +
				"</html>"
		);
		add(aboutLabel);
	}
}
