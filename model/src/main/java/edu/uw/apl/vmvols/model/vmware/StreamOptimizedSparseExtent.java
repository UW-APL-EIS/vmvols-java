package edu.uw.apl.vmvols.model.vmware;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

import org.apache.commons.io.EndianUtils;
import org.apache.log4j.Logger;

import edu.uw.apl.vmvols.model.Constants;
import edu.uw.apl.vmvols.model.RandomAccessVolume;
import edu.uw.apl.vmvols.model.AbstractRandomAccessVolume;

/**
 * Stream-optimized compressed sparse extent.  The normal format for
 * vmdk files in .ovf bundles.  As well as being 'sparse', each grain
 * for which there _IS_ data is compressed using RFC1951 (Deflate).
 */
public class StreamOptimizedSparseExtent {

	public StreamOptimizedSparseExtent( File f, SparseExtentHeader h ) {
		source = f;
		header = h;
		logger = Logger.getLogger( getClass() );
		grainSizeBytes = header.grainSize * Constants.SECTORLENGTH;

		/*
		  Looks like markers do NOT follow descriptor, but come after
		  the advertised 'overhead' ???
		*/
		if( false ) {
			markersOrigin = (header.descriptorOffset +	header.descriptorSize) *
				Constants.SECTORLENGTH;
		} else {
			markersOrigin = header.overhead * Constants.SECTORLENGTH;
		}

		long grainCount = header.capacity / header.grainSize;
		grainTableCount = grainCount / header.numGTEsPerGT;
		
		grainTableCoverageBytes = header.numGTEsPerGT * grainSizeBytes;

		/*
		  maintain a zero grain as an optimisation for all reads
		  which hit such a grain
		*/
		if( grainSizeBytes == ZEROGRAIN_128.length ) {
			zeroGrain = ZEROGRAIN_128;
			logger.info( "Using static zero grain: " +
						 zeroGrain.length );
		} else {
			zeroGrain = new byte[(int)grainSizeBytes];
			logger.info( "Using local zero grain: " +
						 zeroGrain.length );
		}
	}

	public SparseExtentHeader locateSparseExtentFooter() throws IOException {
		long len = source.length();
		long footerOffset = len - 2 * Constants.SECTORLENGTH;
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( footerOffset );
		byte[] ba = new byte[SparseExtentHeader.SIZEOF];
		raf.readFully( ba );
		raf.close();
		SparseExtentHeader result = new SparseExtentHeader( ba );
		return result;
	}
	
	List<GrainMarker> getGrainMarkers() throws IOException {
		if( grainMarkers == null )
			readMarkers();
		return grainMarkers;
	}

	List<MetadataMarker> getMetadataMarkers() throws IOException {
		if( metadataMarkers == null )
			readMarkers();
		return metadataMarkers;
	}
	
	List<Marker> getAllMarkers() throws IOException {
		if( grainMarkers == null )
			readMarkers();
		List<Marker> result = new ArrayList<Marker>();
		result.addAll( grainMarkers );
		result.addAll( metadataMarkers );
		return result;
	}
	
	private void readMarkers() throws IOException {
		grainMarkers = new ArrayList<GrainMarker>();
		metadataMarkers = new ArrayList<MetadataMarker>();
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( markersOrigin );
		byte[] b12 = new byte[12];
		byte[] b4 = new byte[4];
		long skip = 0;
		while( true ) {
			raf.readFully( b12 );
			long val = EndianUtils.readSwappedLong( b12, 0 );
			long size = EndianUtils.readSwappedUnsignedInteger( b12, 8 );
			if( size == 0 ) {
				long numSectors = val;
				raf.readFully( b4 );
				int type = (int)EndianUtils.readSwappedUnsignedInteger( b4, 0 );
				int inMarkerPadding = 496;
				MetadataMarker m = new MetadataMarker( raf.getFilePointer() +
													   inMarkerPadding,
													   numSectors, type );
				metadataMarkers.add( m );

				if( type != MetadataMarker.TYPE_GT )
					System.out.println( m.paramString() );
					
				if( type == MetadataMarker.TYPE_EOS )
					break;
				long dataLen = numSectors * Constants.SECTORLENGTH;
				int postMarkerPadding = 0;
				skip = inMarkerPadding + dataLen + postMarkerPadding;
				
				switch( type ) {
				case MetadataMarker.TYPE_GT:
					raf.skipBytes( inMarkerPadding );
					byte[] gt = new byte[(int)dataLen];
					raf.readFully( gt );
					skip -= (inMarkerPadding + dataLen);
					break;
				default:
					break;
				}
			} else {
				//System.out.println( val + " " + size );
				GrainMarker m = new GrainMarker( raf.getFilePointer(), val, size );
				grainMarkers.add( m );
				long dataLen = size;

				boolean readGrainData = false;
				
				if( readGrainData ) {
					byte[] data = new byte[(int)dataLen];
					raf.readFully( data );
					ByteArrayInputStream bais = new ByteArrayInputStream( data );
					InflaterInputStream dis = new InflaterInputStream( bais );
					byte[] buf = new byte[1024*1024];
					int n = 0;
					while( true ) {
						int nin = dis.read( buf );
						if( nin < 0 )
							break;
						n += nin;
					}
					System.out.println( n );
					dis.close();
				}
				
				long markerLen = dataLen + b12.length;
				int inMarkerPadding = 0;
				int postMarkerPadding =
					(int)( Math.ceil( (double)markerLen /
									Constants.SECTORLENGTH  ) *
						 Constants.SECTORLENGTH - markerLen );
				if( readGrainData ) {
					skip = inMarkerPadding + postMarkerPadding;
				} else {
					skip = inMarkerPadding + dataLen + postMarkerPadding;
				}
				
				/*
				  System.out.println( "Padding " +
									(inMarkerPadding + postMarkerPadding) );
				System.out.println( "Skip " + skip );
				*/
				
			}
			raf.skipBytes( (int)skip );
		}
		raf.close();
	}

	public void readGrainTables() throws IOException {
		List<MetadataMarker> mmgts = new ArrayList<MetadataMarker>();
		List<MetadataMarker> mms = getMetadataMarkers();
		for( MetadataMarker mm : mms ) {
			if( mm.type == MetadataMarker.TYPE_GT )
				mmgts.add( mm );
		}
		if( mmgts.size() != grainTableCount && false ) {
			throw new IllegalStateException( "Expected grain table count " +
											 grainTableCount + ", actual " +
											 mmgts.size() );
		}
											 
		grainTables = new GrainTable[mmgts.size()];
		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		byte[] ba = new byte[GrainTable.SIZEOF];
		for( int i = 0; i < grainTables.length; i++ ) {
			MetadataMarker mmgt = mmgts.get(i);
			raf.seek( mmgt.fileOffset );
			raf.readFully( ba );
			GrainTable gt = new GrainTable( ba );
			grainTables[i] = gt;
		}
		raf.close();
	}

	public void readGrainDirectory() throws IOException {
		SparseExtentHeader sef = locateSparseExtentFooter();
		long gdOffset = sef.gdOffset;
		System.out.println( "gdOffset " + gdOffset );

		long grainCount = sef.capacity / sef.grainSize;

		RandomAccessFile raf = new RandomAccessFile( source, "r" );
		raf.seek( gdOffset * Constants.SECTORLENGTH );

		if( true ) {
			raf.seek( gdOffset * Constants.SECTORLENGTH - Constants.SECTORLENGTH );
			long l = raf.readLong();
			l = EndianUtils.swapLong( l );
			int s = raf.readInt();
			int t = raf.readInt();
			t = EndianUtils.swapInteger( t );
			System.out.println( l + " "+ s + " " + t );
			raf.seek( gdOffset * Constants.SECTORLENGTH );
		}
		
		long grainTableCoverage = grainTableCount * 4;

		System.out.println( "Graintablecoverage " + grainTableCoverage );
		
		byte[] ba = new byte[(int)grainTableCoverage];
		raf.readFully( ba );
		grainDirectory = new GrainDirectory( ba );
		raf.close();
	}
	
	File source;
	SparseExtentHeader header;
	
	//	long[] grainDirectory;
	//long[][] grainTables;
	byte[] zeroGrain;

	long markersOrigin;
	
	// derivable from header properties. Used by streaming operations...
	long grainSizeBytes, grainTableCoverageBytes;

	Logger logger;
	
	/*
	  The spec says that the default grain size is 2^7 = 128 sectors,
	  or 64KB.  So prealloc the '64KB zero grain', so that all
	  SparseExentInputStreams can use it (so saving on per-disk/stream
	  copies)
	*/
	static private final byte[] ZEROGRAIN_128 =
		new byte[128*Constants.SECTORLENGTH];

	static class Marker {
		Marker( long fileOffset ) {
			this.fileOffset = fileOffset;
		}

		final long fileOffset;
	}

	static class GrainMarker extends Marker {
		GrainMarker( long fileOffset, long lba, long size ) {
			super( fileOffset );
			this.lba = lba;
			this.size = size;
		}

		final long lba, size;
	}

	static class MetadataMarker extends Marker {
		MetadataMarker( long fileOffset, long numSectors, int type ) {
			super( fileOffset );
			this.numSectors = numSectors;
			this.type = type;
		}

		public String paramString() {
			return fileOffset + "(" + fileOffset/Constants.SECTORLENGTH + ")," +
				numSectors + "," + type;
		}

		final long numSectors;
		final int type;

		static final int TYPE_EOS = 0;
		static final int TYPE_GT = 1;
		static final int TYPE_GD = 2;
		static final int TYPE_FOOTER = 3;
	}

	List<GrainMarker> grainMarkers;
	List<MetadataMarker> metadataMarkers;

	long grainTableCount;
	GrainTable[] grainTables;
	GrainDirectory grainDirectory;
}

// eof
