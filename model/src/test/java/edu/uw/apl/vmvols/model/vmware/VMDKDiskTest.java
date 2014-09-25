package edu.uw.apl.vmvols.model.vmware;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.Set;
import java.util.List;
import java.util.HashSet;

import edu.uw.apl.vmvols.model.Utils;

public class VMDKDiskTest extends junit.framework.TestCase {
	
	protected void setUp() {
	}

	public void testNull() {
	}
	
	public void testConstructor( File f ) throws Exception {
		if( !f.exists() )
			return;
		System.out.println( f );
		VMDKDisk vd = null;//new VMDKDisk( f );
	}

	/*
	public void testDescriptor( VMDKDisk vd ) throws Exception {
		vd.readDescriptor();
		String d = vd.getDescriptor();
		System.out.println( d );
	}
	*/

	/*
	public void testGrainDirectory( VMDKDisk d ) throws Exception {
		long[] gd = d.getGrainDirectory();
		Set<Long> s = new HashSet<Long>();
		for( long gde : gd ) {
			assertTrue( "zero gde ? " + gde, gde != 0 );
			boolean b = s.add( gde );
			assertTrue( "duplicate gde ? " + gde, b );
		}
	}
	*/
	
	/*
	  All grain tables entries (save for 0) should be unique across
	  all grain tables in the grain directory
	*/
	/*
	  public void testGrainTables( VMDKDisk d ) throws Exception {
		long[][] gts = d.getGrainTables();
		Set<Long> s = new HashSet<Long>();
		for( long[] gt : gts ) {
			for( long gte : gt ) {
				// a zero gte means 'not allocated' and is ok..
				if( gte == 0 )
					continue;
				boolean isUnique = s.add( gte );
				assertTrue( "duplicate gte ? " + gte, isUnique );
			}
		}
	}
	*/
	
	/*
	  public void testMD5( VMDKDisk d, String[] expecteds ) throws Exception {
		InputStream is = d.getInputStream();
		long block = d.contiguousStorage();
		String actual = Utils.md5sum( is, (int)(2*block) );
		is.close();
		System.out.println( d.getPath() + " " + actual );
		assertEquals( "md5 mismatch?", expecteds[0], actual );

		List<Partition> ps = d.getPartitions();
		for( Partition p : ps ) {
			is = p.getInputStream();
			actual = Utils.md5sum( is );
			is.close();
			System.out.println( p.getPath() + " " + actual );
			assertEquals( "md5 mismatch?", expecteds[p.index()], actual );
		}
	}
	*/
	
}

// eof
