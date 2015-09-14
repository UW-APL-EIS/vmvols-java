package edu.uw.apl.vmvols.model.virtualbox;

/**
 * @author Stuart Maclean
 */

public class VDIMissingSignatureException extends VDIException {

	public VDIMissingSignatureException() {
		super();
	}
	
	public VDIMissingSignatureException( String msg ) {
		super( msg );
	}
}

// eof
