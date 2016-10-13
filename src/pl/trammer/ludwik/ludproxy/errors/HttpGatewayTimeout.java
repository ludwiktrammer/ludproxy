package pl.trammer.ludwik.ludproxy.errors;

/**
 * Wyjątek spowoduje wysłanie do klienta strony błędu "504: Gateway Timeout".
 * <p>
 * Więcej informacji w opisie klasy {@link HttpError}.
 *
 */
@SuppressWarnings("serial")
public class HttpGatewayTimeout  extends HttpInternalServerError {
	public HttpGatewayTimeout(String msg) {
		super(msg);
		error_code = 504;
		error_desc = "Gateway Timeout";
	}
}
