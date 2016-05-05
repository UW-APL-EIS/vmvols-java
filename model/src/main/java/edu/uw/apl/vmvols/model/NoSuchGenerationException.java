package edu.uw.apl.vmvols.model;

/**
 *
 * @author Stuart Maclean
 
 * Exception thrown when call to VirtualDisk.getGeneration( int g )
 * cannot be satisfied.
*/

public class NoSuchGenerationException extends VirtualDiskException {

	public NoSuchGenerationException( int g ) {
		super( "" + g );
	}
}

// eof
