package br.com.beardsoft.xlsx.exceptions;

public class NotSupportedException extends RuntimeException {

	private static final long serialVersionUID = -6851711987283786248L;

	public NotSupportedException() {
		super();
	}

	public NotSupportedException(String msg) {
		super(msg);
	}

	public NotSupportedException(Exception e) {
		super(e);
	}

	public NotSupportedException(String msg, Exception e) {
		super(msg, e);
	}
}
