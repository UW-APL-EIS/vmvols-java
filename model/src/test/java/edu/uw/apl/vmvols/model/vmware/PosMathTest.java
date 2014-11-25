package edu.uw.apl.vmvols.model.vmware;

public class PosMathTest extends junit.framework.TestCase {

	public void testNull() {
	}

	static int log2( long i ) {
		for( int p = 0; p < 32; p++ ) {
			if( i == 1 << p )
				return p;
		}
		throw new IllegalArgumentException( "Not a pow2: " + i );
	}
}

// eof


	