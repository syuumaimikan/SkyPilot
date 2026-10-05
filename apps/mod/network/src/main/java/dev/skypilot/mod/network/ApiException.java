package dev.skypilot.mod.network;

/** Error from the SkyPilot API ({@code {code, message}} body) or the transport. */
public final class ApiException extends RuntimeException {
	private final int status;
	private final String code;

	public ApiException(int status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}

	public int status() {
		return status;
	}

	public String code() {
		return code;
	}

	public boolean unauthorized() {
		return status == 401;
	}
}
