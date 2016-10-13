package pl.trammer.ludwik.ludproxy.gui;

import java.util.Vector;

import javax.swing.table.AbstractTableModel;

import pl.trammer.ludwik.ludproxy.Cache;

/**
 * Model danych stojący za tabelą prezentującą stan cache.
 * <p>
 * Przy tworzeniu modelu i za każdym razem gdy wywoływana jest
 * metoda {@link #refresh()} klasa pobiera aktualny
 * stan cache, korzystając z metod dostępnych w klasie
 * {@link Cache}.
 * 
 * @author Ludwik Trammer
 *
 */
public class CacheTableModel extends AbstractTableModel {
	private static final long serialVersionUID = 9158956530489529999L;
	private Vector<String> cachedElements = new Vector<String>(Cache.getCachedUrls());
	final static String[] COLUMNS = {"Adres", "Kod HTTP", "Wiek", "Świeżość"};
	
	public CacheTableModel() {
		super();
	}

	@Override
	public int getColumnCount() {
		return COLUMNS.length;
	}

	@Override
	public int getRowCount() {
		return cachedElements.size();
	}
	
	 @Override
	  public Class<?> getColumnClass(int c) {
		 if(getRowCount()>0) return getValueAt(0, c).getClass();
		 return Object.class;
	  }

	@Override
	public Object getValueAt(int row, int col) {
		switch(col) {
			case 0:
				return cachedElements.get(row);
			case 1:
				return Cache.get(cachedElements.get(row)).getHeader().getStatus();
			case 2:
				return Cache.get(cachedElements.get(row)).getAge();
			case 3:
				return Cache.get(cachedElements.get(row)).isFresh() ? "świeże" : "nie świeże";
		}
		return null;
	}

	@Override
	public String getColumnName(int col) {
		return COLUMNS[col];
	}
	
	public boolean isCellEditable(int row, int col) {
		return false;
	}
	
	/**
	 * Pobiera aktualny stan cache i wyświetla zmiany w tabeli.
	 */
	public void refresh() {
		cachedElements = new Vector<String>(Cache.getCachedUrls());
		fireTableDataChanged();
	}
}
