package com.jeancl.xlsx.exceptions;

public class OpenException extends RuntimeException {

	private static final long serialVersionUID = 5085517806453939161L;

	public OpenException() {
		super();
	}

	public OpenException(String msg) {
		super(msg);
	}

	public OpenException(Exception e) {
		super(e);
	}

	public OpenException(String msg, Exception e) {
		super(msg, e);
	}
}
