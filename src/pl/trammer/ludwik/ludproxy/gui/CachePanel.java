package pl.trammer.ludwik.ludproxy.gui;

import java.awt.BorderLayout;
import java.awt.event.*;
import java.io.IOException;

import javax.swing.*;

import pl.trammer.ludwik.ludproxy.Cache;

/**
 * {@link JPanel} zarządzający wyglądem karty z podglądem stanu cache.
 * 
 * @author Ludwik Trammer
 */
public class CachePanel extends JPanel {
	private static final long serialVersionUID = -6711092893818419942L;

	public CachePanel() {
		super(new BorderLayout());

		final CacheTableModel tableModel = new CacheTableModel();
		JTable table = new JTable(tableModel);
		add(new JScrollPane(table));	

		// można sortować po kolumnach
		table.setAutoCreateRowSorter(true);

		// szerokość kolumn
		table.getColumnModel().getColumn(1).setMaxWidth(60);
		table.getColumnModel().getColumn(2).setMaxWidth(100);
		table.getColumnModel().getColumn(3).setMaxWidth(100);

		// dolny pasek
		Box bottomBar = Box.createHorizontalBox();
		JButton refreshButton = new JButton("Odśwież listę");
		refreshButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)   {
				tableModel.refresh();
			}
		});

		JButton clearCacheButon = new JButton("Wyczyść cache");
		clearCacheButon.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e)   {
				try {
					Cache.clear();
					tableModel.refresh();
				} catch (IOException e1) {
					e1.printStackTrace();
				}
			}
		});
		
		bottomBar.add(refreshButton);
		bottomBar.add(clearCacheButon);
		add(bottomBar, BorderLayout.SOUTH);
	}

}
