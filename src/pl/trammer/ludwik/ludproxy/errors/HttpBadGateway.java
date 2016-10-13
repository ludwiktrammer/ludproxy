package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "502: Bad Gateway".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HttpBadGateway extends HttpInternalServerError {
	public HttpBadGateway(String msg) {
		super(msg);
		error_code = 502;
		error_desc = "Bad Gateway";
	}
}
