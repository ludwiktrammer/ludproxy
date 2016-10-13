package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "505: HTTP Version Not Supported".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HTTPVersionNotSupported  extends HttpInternalServerError {
	public HTTPVersionNotSupported(String msg) {
		super(msg);
		error_code = 505;
		error_desc = "HTTP Version Not Supported";
	}
}
