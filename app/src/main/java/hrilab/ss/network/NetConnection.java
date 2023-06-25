package hrilab.ss.network;

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

public class NetConnection extends Thread implements IMemoryFactory
{
    private final int MAX_RECV_BUFFERS = 32;
    private List<NameChangeEventHandler> _evtNameChangeHandlers = new ArrayList<NameChangeEventHandler>( );
    private String _hostAddress = null;
    private int _hostPort = 0;
    private boolean _isRunning = false;
    private String _name = "";
    private RecycleManager _packetPool = null;
    private List<Packet> _received = new ArrayList<Packet>( );
    private Socket _socket = null;
    private String _socketAddress = null;
    private Object _socketMutex = new Object( );
    private InputStream _socketInput = null;
    private OutputStream _socketOutput = null;


    public NetConnection( String name, String address, int port )
    {
        _name = name;
        _hostAddress = address;
        _hostPort = port;
        _packetPool = new RecycleManager( this );
    }


    public NetConnection( Socket socket )
    {
        _packetPool = new RecycleManager( this );
        _socket = socket;
    }


    public NetConnection( Socket socket, RecycleManager packetPool )
    {
        _packetPool = packetPool;
        _socket = socket;
    }


    public synchronized void addListener( NameChangeEventHandler hdlr )
    {
        _evtNameChangeHandlers.add( hdlr );
    }


    public synchronized void removeListener( NameChangeEventHandler hdlr )
    {
        _evtNameChangeHandlers.remove( hdlr );
    }


    public synchronized void notifyNameChanged( String oldName, String newName )
    {
        for( NameChangeEventHandler hdlr : _evtNameChangeHandlers )
        {
            hdlr.morph( this, oldName, newName );
        }
    }


    public String address( )
    {
        return _socketAddress;
    }


    public void close( )
    {
        _isRunning = false;
        synchronized( _socketMutex )
        {
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
        synchronized( _received )
        {
            while( _received.size( ) > 0 )
            {
                _received.remove( 0 ).removeRef( );
            }
        }
    }


    public IRecyclable create( )
    {
        return new Packet( );
    }


    public Packet getEmptyPacket( )
    {
        Packet packet = (Packet)_packetPool.get( );
        packet.initialize( );
        return packet;
    }


    public Packet getNextData( )
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


    public void send( Packet packet )
    {
        try
        {
            if( !isConnected( ) )
            {   // connection closed
                throw new IllegalStateException( );
            }
            else
            {
                synchronized( _socketMutex )
                {
                    if( _socketOutput != null )
                    {
                        _socketOutput.write( packet.getData( ), 0, packet.getLength( ) );
                    }
                }
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
        Packet packet = (Packet)_packetPool.get( );
        int nReceived = 0;
        boolean isRunning = connect( );
        _isRunning = true;
        while( isRunning )
        {
            isRunning = _isRunning && isConnected( );
            try
            {
                if( nReceived < Packet.MIN_PACKET_SIZE )
                {   // we do not have size information but the input stream has
                    synchronized( _socketMutex )
                    {
                        if( _socketInput != null && _socketInput.available( ) >= Packet.MIN_PACKET_SIZE - nReceived )
                        {
                            nReceived += _socketInput.read( packet.getData( ), nReceived, Packet.MIN_PACKET_SIZE - nReceived );
                        }
                    }
                }
                else
                {   // we have message size information
                    int length = packet.getLength( );
                    if( Packet.MIN_PACKET_SIZE > length || length > Packet.MAX_PACKET_SIZE )
                    {   // we have invalid length information => discard
                        System.err.println( "Connection " + _name + " received invalid length information (length=" + length + ")" );
                        packet.removeRef( );
                        isRunning = false;
                    }
                    else
                    {
                        packet.ensureCapacity( length );
                        if( nReceived < length )
                        {   // we need more data => get more data
                            synchronized( _socketMutex )
                            {
                                if( _socketInput != null && _socketInput.available( ) > 0 )
                                {
                                    nReceived += _socketInput.read( packet.getData( ), nReceived, length - nReceived );
                                }
                            }
                        }
                        else if( nReceived == length )
                        {   // we have received a full message
                            switch( packet.getType( ) )
                            {
                            case PacketType.Disconnection:
                                packet.removeRef( );
                                isRunning = false;
                                break;
                            case PacketType.ChangeConnection:
                                String name = Packet.getString( packet.getData( ), packet.getDataOffset( ), packet.getDataLength( ) );
                                if( !_name.equals( name ) )
                                {
                                    String oldName = _name;
                                    _name = name;
                                    notifyNameChanged( oldName, _name );
                                }
                                packet.removeRef( );
                                packet = (Packet)_packetPool.get( );
                                nReceived = 0;
                                break;
                            default:
                                synchronized( _received )
                                {
                                    if( _received.size( ) >= MAX_RECV_BUFFERS )
                                    {
                                        _received.remove( 0 ).removeRef( );
                                    }
                                    _received.add( packet );
                                }
                                packet = (Packet)_packetPool.get( );
                                nReceived = 0;
                                break;
                            }
                        }
                        else
                        {   // we should not have else condition => unknown error case
                            System.err.println( "Connection " + _name + " will be disconnected due to an unexpected error (received=" + nReceived + ", length=" + length + ")" );
                            packet.removeRef( );
                            isRunning = false;
                        }
                    }
                }
                Thread.yield( );
            }
            catch( Exception ex )
            {
                System.err.println( "NetConnection exception: " + ex.getMessage( ) );
                packet.removeRef( );
                isRunning = false;
            }
        }
        close( );
    }


    private boolean connect( )
    {
        boolean bSendName = false;
        boolean success = false;
        _isRunning = true;
        synchronized( _socketMutex )
        {
            if( _socket == null )
            {
                try
                {
                    _socket = new Socket( _hostAddress, _hostPort );
                    bSendName = true;
                }
                catch( Exception ex )
                {
                    System.err.println( "NetConnection exception: " + ex.getMessage( ) );
                }
            }
            if( _socket != null )
            {
                try
                {
                    _socketAddress = getAddress( _socket );
                    _socketInput = _socket.getInputStream( );
                    _socketOutput = _socket.getOutputStream( );
                    success = true;
                }
                catch( Exception ex )
                {
                    System.err.println( "NetConnection exception: " + ex.getMessage( ) );
                }
            }
        }
        if( bSendName )
        {
            Packet packet = (Packet)_packetPool.get( );
            packet.initialize( PacketType.ChangeConnection );
            packet.addString( _name );
            send( packet );
            packet.removeRef( );
        }
        return success;
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

}
