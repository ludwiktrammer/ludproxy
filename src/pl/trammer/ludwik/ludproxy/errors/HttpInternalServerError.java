package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "500: Internal Server Error".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HttpInternalServerError extends HttpError {
	public HttpInternalServerError(String msg) {
		super(msg);
		error_code = 500;
		error_desc = "Internal Server Error";
	}
}