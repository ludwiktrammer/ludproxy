package pl.trammer.ludwik.ludproxy.gui;

import java.net.InetAddress;
import java.util.HashSet;
import java.util.Vector;

import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;

import pl.trammer.ludwik.geo.HttpMap;
import pl.trammer.ludwik.ludproxy.ServerResponse;

/**
 * Model danych stojący za tabelą prezentującą bierzące połączenia.
 * <p>
 * Za każdym razem gdy tworzone jest nowe połączenie wywoływana jest
 * metoda {@link #add(int, ServerResponse)}, która dodaje informacje
 * o tym połączeniu do tabeli. Dzięki temu dane w tabeli zawsze
 * wyświetlane są na bieżąco.
 * <p>
 * Model służy również jako słuchacz zmian zaznaczenia w tabeli. W razie
 * zmiany przesyłana jest informacja o zaznaczonych rzędach do mapy,
 * korzystając z metody {@link HttpMap#setSelected(java.util.Set)}.
 * 
 * @author Ludwik Trammer
 *
 */
public class ConnectionsTableModel extends AbstractTableModel implements ListSelectionListener {
	private static final long serialVersionUID = 208611177069387618L;
	private Vector<ConnectionRow> connections = new Vector<ConnectionRow>();
	String[] columns = {"Wątek", "Adres", "Status", "Typ", "Warunkowe?", "Ilość danych", "Opóźnienie"};
	HttpMap map;
	
	public ConnectionsTableModel(HttpMap map) {
		super();
		this.map = map;
	}
	
	@Override
	public int getColumnCount() {
		return columns.length;
	}

	@Override
	public int getRowCount() {
		return connections.size();
	}

	 @Override
	  public Class<?> getColumnClass(int c) {
		 if(c==1 || c==3 || c==4 || c==5) return String.class;
		 if(c==6) return Double.class;
		 return Integer.class;
	  }
	
	@Override
	public Object getValueAt(int row, int column) {
		return connections.get(row).getAtColumn(column);
	}
	
	@Override
	public String getColumnName(int col) {
		return columns[col];
	}
	
	public boolean isCellEditable(int row, int col) {
		return false;
	}
	
	/**
	 * Dodaje informacje o połączeniu do tabeli.
	 * @param threadNum numer wątku, który realizuje to połączenie.
	 * @param response obiekt {@link ServerResponse}, który jest efektem
	 * tego połączenia.
	 */
	public synchronized void add(int threadNum, ServerResponse response) {
		connections.add(new ConnectionRow(threadNum, response));
		fireTableRowsInserted(connections.size()-1, connections.size()-1);
	}
	
	/**
	 * Usuwa zapisane informacje o połączeniach.
	 */
	public void clear() {
		connections.clear();
		fireTableDataChanged();
	}
	
	/**
	 * Metoda intefejsu {@link javax.swing.event.ListSelectionListener}, wywoływana
	 * przy zmianie zaznaczenia w tabeli. Metoda wysyła informacje o zaznaczonych
	 * rzędach do mapy przy pomocy metody {@link HttpMap#setSelected(java.util.Set)},
	 * aby ta mogła je graficznie wyświetlić.
	 */
	@Override
	public void valueChanged(ListSelectionEvent e) {
		ListSelectionModel lsm = (ListSelectionModel)e.getSource();
		if (lsm.isSelectionEmpty()) {
			map.setSelected(null);
		} else {
			int minIndex = lsm.getMinSelectionIndex();
            int maxIndex = lsm.getMaxSelectionIndex();
            HashSet<InetAddress> selected = new HashSet<InetAddress>();
            for (int i = minIndex; i <= maxIndex; i++) {
                if (lsm.isSelectedIndex(i)) {
                    selected.add(connections.get(i).serverIp);
                }
            }
            map.setSelected(selected);
		}
	}

	/**
	 * Klasa wewnętrzna reprezentująca informacje na
	 * temat jednego wiersza tabeli.
	 *
	 */
	private class ConnectionRow {
		private int thread;
		private String address;
		private int status;
		private String type;
		private String conditional;
		private double latency;
		private int size;
		private InetAddress serverIp;
		
		public ConnectionRow(int threadNum, ServerResponse response) {
			thread = threadNum;
			address = response.getRequest().getUrl();
			status = response.getHeader().getStatus();
			type = response.getHeader().getField("Content-Type");
			if(type!=null) type = type.split(";")[0]; // obetnij dodatkowe info w typie
			latency = response.getLatency();
			size = response.getContentLength();
			serverIp = response.getServerIp();
			
			/* 
			 * Jeśli zapytanie było warunkowe i serwer potwierdził aktualność kopii w cache
			 * wyświetlamy w tabeli "potwierdzone", jeśli przysłał nową wersję wyświetlany
			 * "nowe".
			 */
			if(response.verifiedConditional()) {
				status = 304;
				size = 0;
				conditional = "zweryfikowane";
			} else if(response.wasConditional()) {
				conditional = "nowa kopia";
			} else {
				conditional = "nie";
			}

		}
		
		public Object getAtColumn(int index) {
			switch(index) {
			case 0:
				return thread;
			case 1:
				return address;
			case 2:
				return status;
			case 3:
				return type;
			case 4:
				return conditional;
			case 5:
				return getHumanRedableSize();
			case 6:
				return latency;
			}
			return null;
		}
		
		public String getHumanRedableSize() {
		    if (size < 1024) return size + " B";
		    int exp = (int) (Math.log(size) / Math.log(1024));
		    String pre = "KMGTPE".charAt(exp-1) + "i";
		    return String.format("%.1f %sB", size / Math.pow(1024, exp), pre);
		}
	}
}
