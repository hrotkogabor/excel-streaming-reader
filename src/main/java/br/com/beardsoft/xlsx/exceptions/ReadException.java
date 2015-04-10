package br.com.beardsoft.xlsx.exceptions;

public class ReadException extends RuntimeException {

	private static final long serialVersionUID = 8776051016425182858L;

	public ReadException() {
		super();
	}

	public ReadException(String msg) {
		super(msg);
	}

	public ReadException(Exception e) {
		super(e);
	}

	public ReadException(String msg, Exception e) {
		super(msg, e);
	}
}
