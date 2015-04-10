package com.jeancl.xlsx.exceptions;

public class CloseException extends RuntimeException {

	private static final long serialVersionUID = -2147651734107373278L;

	public CloseException() {
		super();
	}

	public CloseException(String msg) {
		super(msg);
	}

	public CloseException(Exception e) {
		super(e);
	}

	public CloseException(String msg, Exception e) {
		super(msg, e);
	}
}
