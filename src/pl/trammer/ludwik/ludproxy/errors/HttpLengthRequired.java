package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "411: Length Required".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HttpLengthRequired extends HttpBadRequest {
	public HttpLengthRequired(String msg) {
		super(msg);
		error_code = 411;
		error_desc = "Length Required";
	}
}