package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.regex.*;

import org.apache.commons.io.FileUtils;

/**
 * Tests for the parsing of {@link Descriptor}
 *
 * @author Stuart Maclean
 */
public class DescriptorParseTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testBadParentFileNameHint() {
		String input = "notQuoted";
		Matcher m = Descriptor.reParentFileNameHint.matcher( input );
		assertFalse( m.matches() );
	}

	public void testGoodParentFileNameHint() {
		String input = "parentFileNameHint=\"Quoted\"";
		Matcher m = Descriptor.reParentFileNameHint.matcher( input );
		assertTrue( m.matches() );
		String c = m.group(1);
		System.out.println( c );
	}

	public void testGoodParentFileNameHint2() {
		String input = "parentFileNameHint=\"/path/to/Windows 7/Windows 7.vmdk\"";
		Matcher m = Descriptor.reParentFileNameHint.matcher( input );
		assertTrue( m.matches() );
		String c = m.group(1);
		System.out.println( c );
	}
}

// eof
