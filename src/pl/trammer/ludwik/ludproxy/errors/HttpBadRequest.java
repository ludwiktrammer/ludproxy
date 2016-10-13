package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "400: Bad Request".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HttpBadRequest extends HttpError {
	public HttpBadRequest(String msg) {
		super(msg);
		error_code = 400;
		error_desc = "Bad Request";
	}
}