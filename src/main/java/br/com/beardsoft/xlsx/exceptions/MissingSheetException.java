package br.com.beardsoft.xlsx.exceptions;

public class MissingSheetException extends RuntimeException {

	private static final long serialVersionUID = -5820911757481692824L;

	public MissingSheetException() {
		super();
	}

	public MissingSheetException(String msg) {
		super(msg);
	}

	public MissingSheetException(Exception e) {
		super(e);
	}

	public MissingSheetException(String msg, Exception e) {
		super(msg, e);
	}
}
