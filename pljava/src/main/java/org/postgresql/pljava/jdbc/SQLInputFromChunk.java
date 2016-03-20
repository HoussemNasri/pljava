/*
 * Copyright (c) 2004-2016 TADA AB and other contributors, as listed below.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the The BSD 3-Clause License
 * which accompanies this distribution, and is available at
 * http://opensource.org/licenses/BSD-3-Clause
 *
 * Contributors:
 *   Thomas Hallgren
 *   Chapman Flack
 */
package org.postgresql.pljava.jdbc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.sql.Array;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Date;
import java.sql.NClob;
import java.sql.Ref;
import java.sql.RowId;
import java.sql.SQLException;
import java.sql.SQLDataException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLNonTransientException;
import java.sql.SQLInput;
import java.sql.SQLXML;
import java.sql.Time;
import java.sql.Timestamp;

import org.postgresql.pljava.internal.Backend;

/**
 * The SQLInputToChunk uses JNI to read from memory that has been allocated by
 * the PostgreSQL backend. A user should never make an attempt to create an
 * instance of this class. Only internal JNI routines can do that. An instance
 * is propagated in a call from the internal JNI layer to the Java layer will
 * only survive during that single call. The handle of the instance will be
 * invalidated when the call returns and subsequent use of the instance will
 * yield a SQLException with the message "Stream is closed".
 * 
 * @author Thomas Hallgren
 */
public class SQLInputFromChunk implements SQLInput
{
	private ByteBuffer m_bb;

	/* get rid of this once no longer supporting back to Java 6 */
	private static final Charset UTF8 = Charset.forName("UTF-8");

	private static ByteOrder scalarOrder;
	private static ByteOrder mirrorOrder;

	public SQLInputFromChunk(ByteBuffer bb, boolean isJavaBasedScalar)
		throws SQLException
	{
		m_bb = bb;
		if ( isJavaBasedScalar )
		{
			if ( null == scalarOrder )
				scalarOrder = getOrder(true);
			m_bb.order(scalarOrder);
		}
		else
		{
			if ( null == mirrorOrder )
				mirrorOrder = getOrder(false);
			m_bb.order(mirrorOrder);
		}
	}

	private ByteOrder getOrder(boolean isJavaBasedScalar) throws SQLException
	{
		ByteOrder result;
		String key = "org.postgresql.pljava.udt.byteorder."
			+ ( isJavaBasedScalar ? "scalar" : "mirror" ) + ".p2j";
		String val = System.getProperty(key);
		if ( "big_endian".equals(val) )
			result = ByteOrder.BIG_ENDIAN;
		else if ( "little_endian".equals(val) )
			result = ByteOrder.LITTLE_ENDIAN;
		else if ( "native".equals(val) )
			result = ByteOrder.nativeOrder();
		else
			throw new SQLNonTransientException(
				"System property " + key +
				" must be big_endian, little_endian, or native", "F0000");
		return result;
	}

	@Override
	public Array readArray() throws SQLException
	{
		throw unsupportedOperationException("readArray");
	}

	@Override
	public InputStream readAsciiStream() throws SQLException
	{
		throw unsupportedOperationException("readAsciiStream");
	}

	@Override
	public BigDecimal readBigDecimal() throws SQLException
	{
		return new BigDecimal(this.readString());
	}

	@Override
	public InputStream readBinaryStream() throws SQLException
	{
		return new ByteArrayInputStream(this.readBytes());
	}

	@Override
	public Blob readBlob() throws SQLException
	{
		throw unsupportedOperationException("readBlob");
	}

	@Override
	public boolean readBoolean() throws SQLException
	{
		try
		{
			return 0 != m_bb.get();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public byte readByte() throws SQLException
	{
		try
		{
			return m_bb.get();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public byte[] readBytes() throws SQLException
	{
		try
		{
			int len = m_bb.getShort() & 0xffff;
		    byte[] buffer = new byte[len];
			m_bb.get(buffer);
			return buffer;
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public Reader readCharacterStream() throws SQLException
	{
		return new StringReader(this.readString());
	}

	@Override
	public Clob readClob() throws SQLException
	{
		throw unsupportedOperationException("readClob");
	}

	@Override
	public Date readDate() throws SQLException
	{
		try
		{
			return new Date(m_bb.getLong());
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public double readDouble() throws SQLException
	{
		try
		{
			return m_bb.getDouble();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public float readFloat() throws SQLException
	{
		try
		{
			return m_bb.getFloat();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public int readInt() throws SQLException
	{
		try
		{
			return m_bb.getInt();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public long readLong() throws SQLException
	{
		try
		{
			return m_bb.getLong();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public Object readObject() throws SQLException
	{
		throw unsupportedOperationException("readObject");
	}

	@Override
	public Ref readRef() throws SQLException
	{
		throw unsupportedOperationException("readRef");
	}

	@Override
	public short readShort() throws SQLException
	{
		try
		{
			return m_bb.getShort();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public String readString() throws SQLException
	{
		try
		{
			int len = m_bb.getShort() & 0xffff;
			ByteBuffer bytes = (ByteBuffer)m_bb.slice().limit(len);
			m_bb.position(m_bb.position() + len);
			return UTF8.newDecoder().decode(bytes).toString();
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public Time readTime() throws SQLException
	{
		try
		{
			return new Time(m_bb.getLong());
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public Timestamp readTimestamp() throws SQLException
	{
		try
		{
			return new Timestamp(m_bb.getLong());
		}
		catch ( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public URL readURL() throws SQLException
	{
		try
		{
			return new URL(this.readString());
		}
		catch( Exception e )
		{
			throw badRepresentation(e);
		}
	}

	@Override
	public boolean wasNull() throws SQLException
	{
		return false;
	}

	void close()
	{
		m_bb = null;
	}

	private SQLException badRepresentation(Throwable e)
	{
		if ( e instanceof NullPointerException )
			return new SQLNonTransientException(
				"attempted read from SQLInput after closing it", "55000", e);
		return new SQLDataException(
			"Could not read binary representation of user-defined type",
			"22P03", e);
	}

	private SQLException unsupportedOperationException(String op)
	{
		return new SQLFeatureNotSupportedException(
			this.getClass() + "." + op + "() not implemented yet.", "0A000");
	}

	// ************************************************************
	// Non-implementation of JDBC 4 methods.
	// ************************************************************

	@Override
	public RowId readRowId()
                throws SQLException
	{
		throw unsupportedOperationException("readRowId");
	}

	@Override
	public SQLXML readSQLXML()
		throws SQLException
	{
		throw unsupportedOperationException("readSQLXML");
	}

	@Override
	public String readNString()
		throws SQLException
	{
		throw unsupportedOperationException("readNString");
	}
	
	@Override
	public NClob readNClob()
	       throws SQLException
	{
		throw unsupportedOperationException("readNClob");
	}
}
