package pl.trammer.ludwik.geo;

/**
 * Obiekty tej klasy symbolizują współrzędne geograficzne.
 * 
 * @author Ludwik Trammer
 *
 */
public class Coordinates {
	private double longitude, latitude;
	
	/**
	 * Tworzy nowy obiekt współrzędnych geograficznych.
	 * 
	 * @param latitude szerokość geograficzna
	 * @param longitude długość geograficzna
	 */
	public Coordinates(double latitude, double longitude) {
		setLatitude(latitude);
		setLongitude(longitude);
	}
	
	/**
	 * Zmiana długości geograficznej. Wartość musi znajdować się w przedziale od -180 do 180.
	 * <p>
	 */
	public void setLongitude(double longitude) {
		if(longitude > 180 || longitude < -180) throw new IllegalArgumentException("Nieprawidłowa długość geograficzna!");
		this.longitude = longitude;
	}
	
	/**
	 * Zmiana szeroości geograficznej. Wartość musi znajdować się w przedziale od -90 do 90.
	 */
	public void setLatitude(double latitude) {
		if(latitude > 90 || latitude < -90) throw new IllegalArgumentException("Nieprawidłowa szerokość geograficzna!");
		this.latitude = latitude;
	}
	
	/**
	 * Zwraca długość geograficzną.
	 */
	public double getLongitude() {
		return longitude;
	}

	/**
	 * Zwraca szerokość geograficzną.
	 */
	public double getLatitude() {
		return latitude;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(latitude);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(longitude);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	/**
	 * Dwie współrzędne geograficzne są sobie równe jeśli mają
	 * identyczne długość i szerokość geograficzną.
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Coordinates other = (Coordinates) obj;
		if (Double.doubleToLongBits(latitude) != Double
				.doubleToLongBits(other.latitude))
			return false;
		if (Double.doubleToLongBits(longitude) != Double
				.doubleToLongBits(other.longitude))
			return false;
		return true;
	}
}
