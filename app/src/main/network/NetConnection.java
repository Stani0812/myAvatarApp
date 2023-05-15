import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;

public class NetConnection extends Thread
{
    public static final int HEADER_SIZE = 4;
    private static final int MAX_RECV_BUFFERS = 16;
    private final long PING_INTERVAL = 30000; // milliseconds
    private RecycleManager _packetPool = null;
    private byte[] _pingMessage = new byte[0];
    private long _pingTime = 0;
    private List<RecycleMemory> _received = new ArrayList<RecycleMemory>( );
    private Socket _socket = null;
    private String _socketAddress = null;
    private InputStream _socketInput = null;
    private OutputStream _socketOutput = null;


    public NetConnection( Socket socket, RecycleManager packetPool )
    {
        _packetPool = packetPool;
        _pingTime = System.currentTimeMillis( ) + PING_INTERVAL;
        try
        {
            _socket = socket;
            _socketAddress = getAddress( socket );
            _socketInput = _socket.getInputStream( );
            _socketOutput = _socket.getOutputStream( );
        }
        catch( Exception ex )
        {
            System.err.println( "NetConnection exception: " + ex.getMessage( ) );
            close( );
        }
    }


    public String address( )
    {
        return _socketAddress;
    }


    public void close( )
    {
        _pingTime = Long.MAX_VALUE;
        synchronized( _received )
        {
            while( _received.size( ) > 0 )
            {
                _received.remove( 0 ).removeRef( );
            }
        }
        if( _socketAddress != null )
        {
            System.out.println( "Close connection from " + _socketAddress );
            _socketAddress = null;
        }
        if( _socketInput != null )
        {
            try { _socketInput.close( ); } catch( IOException ex ) { }
            _socketInput = null;
        }
        if( _socketOutput != null )
        {
            try { _socketOutput.close( ); } catch( IOException ex ) { }
            _socketOutput = null;
        }
        if( _socket != null )
        {
            try { _socket.close( ); } catch( IOException ex ) { }
            _socket = null;
        }
    }


    public RecycleMemory getNextData( )
    {
        synchronized( _received )
        {   // lock the object
            if( _received.size( ) > 0 )
            {
                return _received.remove( 0 );
            }
        }
        return null;
    }


    public boolean isConnected( )
    {
        return _socketInput != null && _socketOutput != null && _socket != null && _socket.isConnected( );
    }


    public void ping( )
    {
        if( _pingTime <= System.currentTimeMillis( ) )
        {
            send( _pingMessage, 0, 0 );
        }
    }


    public void send( byte[][] dataCollection )
    {
        try
        {
            byte[] header = new byte[HEADER_SIZE];
            if( !isConnected( ) )
            {   // connection closed
                throw new IllegalStateException( );
            }
            else
            {
                int length = 0;
                for( byte[] data : dataCollection )
                {
                    length += data.length;
                }
                header[0] = (byte)(length & 0x000000FF);
                header[1] = (byte)((length >> 8) & 0x000000FF);
                header[2] = (byte)((length >> 16) & 0x000000FF);
                header[3] = 0;
                _socketOutput.write( header );
                for( byte[] data : dataCollection )
                {
                    _socketOutput.write( data );
                }
                _pingTime = System.currentTimeMillis( ) + PING_INTERVAL;
            }
        }
        catch( Exception ex )
        {
            System.err.println( "NetConnection exception: " + ex.getMessage( ) );
            close( );
        }
    }


    public void send( byte[] data, int offset, int length )
    {
        try
        {
            byte[] header = new byte[HEADER_SIZE];
            if( !isConnected( ) )
            {   // connection closed
                throw new IllegalStateException( );
            }
            else
            {
                header[0] = (byte)(length & 0x000000FF);
                header[1] = (byte)((length >> 8) & 0x000000FF);
                header[2] = (byte)((length >> 16) & 0x000000FF);
                header[3] = 0;
                _socketOutput.write( header );
                _socketOutput.write( data, offset, length );
                _pingTime = System.currentTimeMillis( ) + PING_INTERVAL;
            }
        }
        catch( Exception ex )
        {
            System.err.println( "NetConnection exception: " + ex.getMessage( ) );
            close( );
        }
    }


    public void run( )
    {
        RecycleMemory buffer = _packetPool.get( );
        buffer.length = 0;
        while( isConnected( ) )
        {
            try
            {
                if( buffer.length < HEADER_SIZE )
                {   // we do not have size information but the input stream has
                    if( _socketInput.available( ) >= HEADER_SIZE - buffer.length )
                    {
                        buffer.length += _socketInput.read( buffer.data, buffer.length, HEADER_SIZE - buffer.length );
                    }
                }
                else
                {   // we have message size information
                    int length = (int)((buffer.data[0] & 0xFF) | (buffer.data[1] & 0xFF) << 8 | (buffer.data[2] & 0xFF) << 16) + HEADER_SIZE;
                    buffer.ensureCapacity( length );
                    if( buffer.length < length && _socketInput.available( ) > 0 )
                    {
                        buffer.length += _socketInput.read( buffer.data, buffer.length, length - buffer.length );
                    }
                    //System.out.println( "MEMORY_CAPACITY= " + buffer.data.length + " MEMORY_LENGTH= " + buffer.length + " DATA_LENGTH=" + length );
                    if( buffer.length == length )
                    {
                        synchronized( _received )
                        {
                            if( _received.size( ) >= MAX_RECV_BUFFERS )
                            {
                                _received.remove( 0 ).removeRef( );
                            }
                            _received.add( buffer );
                        }
                        buffer = _packetPool.get( );
                        buffer.length = 0;
                    }
                }
                Thread.yield( );
            }
            catch( Exception ex )
            {
                System.err.println( "NetConnection exception: " + ex.getMessage( ) );
                close( );
            }
        }
    }


    public static String getAddress( Socket socket )
    {
        SocketAddress socketAddress = socket.getRemoteSocketAddress( );
        if( socketAddress instanceof InetSocketAddress )
        {
            InetAddress inetAddress = ((InetSocketAddress)socketAddress).getAddress( );
            if( inetAddress instanceof Inet4Address )
            {
                return inetAddress.toString( );
            }
            else if( inetAddress instanceof Inet6Address )
            {
                return inetAddress.toString( );
            }
            else
            {   // unknown IP address
                return "Unknown";
            }
        }
        else
        {   // unknown internet protocol
            return "Unknown";
        }
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

}
