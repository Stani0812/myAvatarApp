package hrilab.ss.network;

import java.nio.charset.StandardCharsets;

public class Packet extends RecycleMemory
{
    public static final int MAX_PACKET_SIZE = 0x3FFFFFFF;
    public static final int MIN_PACKET_SIZE = 0x00000004;
    private static final int INITIAL_PACKET_SIZE = 32 * 1024;
    private byte[] _data = new byte[INITIAL_PACKET_SIZE];


    public Packet( )
    {
    }


    public byte[] getData( )
    {
        return _data;
    }


    public int getDataOffset( )
    {
        return MIN_PACKET_SIZE;
    }


    public int getDataLength( )
    {
        return getLength( ) - MIN_PACKET_SIZE;
    }


    public void setDataLength( int value )
    {
        setLength( value + MIN_PACKET_SIZE );
    }


    public int getLength( )
    {
        return (int)((_data[0] & 0xFF) | (_data[1] & 0xFF) << 8 | (_data[2] & 0xFF) << 16 | (_data[3] & 0x3F) << 24);
    }


    public void setLength( int value )
    {
        _data[0] = (byte)(value & 0x000000FF);
        _data[1] = (byte)((value >> 8) & 0x000000FF);
        _data[2] = (byte)((value >> 16) & 0x000000FF);
        _data[3] = (byte)(((value >> 24) & 0x0000003F) | (_data[3] & 0x000000C0));
    }


    public byte getType( )
    {
        return (byte)((_data[3] >> 6) & 0x00000003);
    }


    public void setType( byte type )
    {
        _data[3] = (byte)((type & 0x03) << 6);
    }


    public void initialize( )
    {
        initialize( PacketType.Normal );
    }


    public void initialize( byte packetType )
    {
        setType( packetType );
        setLength( MIN_PACKET_SIZE );
    }


    public void addInt8( byte value )
    {
        final int size = 1;
        int length = getLength( );
        ensureCapacity( length + size );
        _data[length] = value;
        setLength( length + size );
    }


    public void addInt16( short value )
    {
        final int size = 2;
        int length = getLength( );
        ensureCapacity( length + size );
        toBytes( value, _data, length );
        setLength( length + size );
    }


    public void addInt32( int value )
    {
        final int size = 4;
        int length = getLength( );
        ensureCapacity( length + size );
        toBytes( value, _data, length );
        setLength( length + size );
    }


    public void addInt64( long value )
    {
        final int size = 8;
        int length = getLength( );
        ensureCapacity( length + size );
        toBytes( value, _data, length );
        setLength( length + size );
    }


    public void addSingle( float value )
    {
        final int size = 4;
        int length = getLength( );
        ensureCapacity( length + size );
        toBytes( value, _data, length );
        setLength( length + size );
    }


    public void addDouble( double value )
    {
        final int size = 8;
        int length = getLength( );
        ensureCapacity( length + size );
        toBytes( value, _data, length );
        setLength( length + size );
    }


    public void addString( String value )
    {
        addString( value, true );
    }


    public void addString( String value, boolean includeNullTerminator )
    {
        byte[] sz = value.getBytes( StandardCharsets.UTF_8 );
        int length = getLength( );
        ensureCapacity( length + sz.length + (includeNullTerminator ? 1 : 0) );
        System.arraycopy( sz, 0, _data, length, sz.length );
        if( includeNullTerminator )
        {
            _data[length + sz.length] = 0;
            setLength( length + sz.length + 1 );
        }
        else
        {
            setLength( length + sz.length );
        }
    }


    public void addBytes( byte[] data, int offset, int count )
    {
        int length = getLength( );
        ensureCapacity( length + count );
        System.arraycopy( data, offset, _data, length, count );
        setLength( length + count );
    }


    public void ensureCapacity( int value )
    {
        if( !(MIN_PACKET_SIZE <= value && value <= MAX_PACKET_SIZE) )
        {
            throw new IndexOutOfBoundsException( );
        }
        if( _data.length < value )
        {
            byte[] tmp = _data;
            _data = new byte[value];
            System.arraycopy( tmp, 0, _data, 0, tmp.length );
        }
    }


    protected void onRecycleRequesting( )
    {
        initialize( );
    }


    public static short getInt16( byte[] data, int offset )
    {
        return (short)((data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8);
    }


    public static int getInt32( byte[] data, int offset )
    {
        return (int)((data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24);
    }


    public static long getInt64( byte[] data, int offset )
    {
        return (long)((data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24
            | (data[offset + 4] & 0xFF) << 32 | (data[offset + 5] & 0xFF) << 40 | (data[offset + 6] & 0xFF) << 48 | (data[offset + 7] & 0xFF) << 56);
    }


    public static float getSingle( byte[] data, int offset )
    {
        int intBits = (int)((data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24);
        return Float.intBitsToFloat( intBits );
    }


    public static double getDouble( byte[] data, int offset )
    {
        long longBits = (long)((data[offset] & 0xFF) | (data[offset + 1] & 0xFF) << 8 | (data[offset + 2] & 0xFF) << 16 | (data[offset + 3] & 0xFF) << 24
            | (data[offset + 4] & 0xFF) << 32 | (data[offset + 5] & 0xFF) << 40 | (data[offset + 6] & 0xFF) << 48 | (data[offset + 7] & 0xFF) << 56);
        return Double.longBitsToDouble( longBits );
    }


    public static String getString( byte[] data, int offset )
    {
        return getString( data, offset, data.length - offset );
    }


    public static String getString( byte[] data, int offset, int maxCount )
    {
        int index = offset;
        int length = 0;
        while( index < data.length && data[index] != 0 && length < maxCount )
        {
            index++;
            length++;
        }
        return new String( data, offset, length, StandardCharsets.UTF_8 );
    }


    public static void toBytes( short value, byte[] dst, int offset )
    {
        dst[offset] = (byte)(value & 0x00FF);
        dst[offset + 1] = (byte)((value >> 8) & 0x00FF);
    }


    public static void toBytes( int value, byte[] dst, int offset )
    {
        dst[offset] = (byte)(value & 0x000000FF);
        dst[offset + 1] = (byte)((value >> 8) & 0x000000FF);
        dst[offset + 2] = (byte)((value >> 16) & 0x000000FF);
        dst[offset + 3] = (byte)((value >> 24) & 0x000000FF);
    }


    public static void toBytes( long value, byte[] dst, int offset )
    {
        dst[offset] = (byte)(value & 0x00000000000000FF);
        dst[offset + 1] = (byte)((value >> 8) & 0x00000000000000FF);
        dst[offset + 2] = (byte)((value >> 16) & 0x00000000000000FF);
        dst[offset + 3] = (byte)((value >> 24) & 0x00000000000000FF);
        dst[offset + 4] = (byte)((value >> 32) & 0x00000000000000FF);
        dst[offset + 5] = (byte)((value >> 40) & 0x00000000000000FF);
        dst[offset + 6] = (byte)((value >> 48) & 0x00000000000000FF);
        dst[offset + 7] = (byte)((value >> 56) & 0x00000000000000FF);
    }


    public static void toBytes( float value, byte[] dst, int offset )
    {
        int intBits =  Float.floatToIntBits( value );
        dst[offset] = (byte)(intBits & 0x000000FF);
        dst[offset + 1] = (byte)((intBits >> 8) & 0x000000FF);
        dst[offset + 2] = (byte)((intBits >> 16) & 0x000000FF);
        dst[offset + 3] = (byte)((intBits >> 24) & 0x000000FF);
    }


    public static void toBytes( double value, byte[] dst, int offset )
    {
        long longBits =  Double.doubleToLongBits( value );
        dst[offset] = (byte)(longBits & 0x00000000000000FF);
        dst[offset + 1] = (byte)((longBits >> 8) & 0x00000000000000FF);
        dst[offset + 2] = (byte)((longBits >> 16) & 0x00000000000000FF);
        dst[offset + 3] = (byte)((longBits >> 24) & 0x00000000000000FF);
        dst[offset + 4] = (byte)((longBits >> 32) & 0x00000000000000FF);
        dst[offset + 5] = (byte)((longBits >> 40) & 0x00000000000000FF);
        dst[offset + 6] = (byte)((longBits >> 48) & 0x00000000000000FF);
        dst[offset + 7] = (byte)((longBits >> 56) & 0x00000000000000FF);
    }

}
