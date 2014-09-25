package edu.uw.apl.vmvols.model.vmware;


import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class Descriptor {

	/*
	static Descriptor parse( VMDKHeader h, File f ) throws IOException {
		byte[] ba = new byte[(int)h.descriptorSize()];
		RandomAccessFile raf = new RandomAccessFile( f, "r" );
		raf.seek( h.descriptorOffset() * 512 );
		raf.readFully( ba );
		raf.close();
		return new Descriptor( ba );
	}
	*/
	
	Descriptor( byte[] ba ) {
		String s = null;
		try {
			s = new String( ba, "ISO-8859-1" );
		} catch( UnsupportedEncodingException never ) {
		}
		data = s;
		BufferedReader br = new BufferedReader( new StringReader( data ) );
		String line;
		try {
			while( (line = br.readLine()) != null ) {
				line = line.trim();
				if( line.length() == 0 || line.startsWith( "#" ) )
					continue;
				Matcher m = reType.matcher( line );
				if( m.matches() ) {
					type = m.group(1);
				}
			}
		} catch( IOException never ) {
		}
	}

	@Override
	public String toString() {
		return data;
	}

	public String getCreateType() {
		return type;
	}
	
	static private final Pattern reType = Pattern.compile
		( "createType=\"([A-Za-z]+)\"" );
	
	private final String data;
	private String type;
}

// eof
