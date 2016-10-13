package pl.trammer.ludwik.ludproxy.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;

import pl.trammer.ludwik.geo.HttpMap;

/**
 * {@link JPanel} zarządzający wyglądem karty z podglądem połączeń.
 * 
 * @author Ludwik Trammer
 */
@SuppressWarnings("serial")
public class ConnectionsPanel extends JPanel {
	public ConnectionsPanel(final ConnectionsTableModel model, final HttpMap map) {
		super(new BorderLayout());
		
		JPanel connections = new JPanel(new BorderLayout());
		final JTable table = new JTable(model);
		/* 
		 * model tabeli śledzi również zmiany zaznaczenia i w razie czego
		 * wyświetla do mapy informacje o tym co wyświetlać.
		 */
		table.getSelectionModel().addListSelectionListener(model);

		// kliknięcie mapy "czyści" zaznaczenia w tabeli
		map.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                table.clearSelection();
            }
		});
		connections.add(new JScrollPane(table));	
		
		// można sortować po kolumnach
		table.setAutoCreateRowSorter(true);

		// domyślna szerokość kolumn
		table.getColumnModel().getColumn(0).setMaxWidth(40);
		table.getColumnModel().getColumn(2).setMaxWidth(40);
		table.getColumnModel().getColumn(3).setMaxWidth(400);
		table.getColumnModel().getColumn(4).setMaxWidth(200);
		table.getColumnModel().getColumn(5).setMaxWidth(200);
		table.getColumnModel().getColumn(6).setMaxWidth(200);
		
		// dolny pasek
		Box bottomBar = Box.createHorizontalBox();
		
		
		JLabel selectLabel = new JLabel("Zaznacz połączenie żeby zobaczyć podpisy na mapie.");
		bottomBar.add(selectLabel);
		
		bottomBar.add(Box.createGlue());
		
		JButton clearButton = new JButton("Wyczyść listę połączeń");
		clearButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)   {
				model.clear();
				map.clear();
				map.repaint();
			}
		});
		
		bottomBar.add(clearButton);
			
		connections.add(bottomBar, BorderLayout.SOUTH);
				
		map.setPreferredSize(new Dimension(0,350));
		connections.setPreferredSize(new Dimension(0,0));
		
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, map, connections);
		split.setOneTouchExpandable(true); 
		add(split);
	}
}
