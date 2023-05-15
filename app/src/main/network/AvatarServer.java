import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class AvatarServer implements Runnable, TypeChangeEventHandler
{
    private static final int DEFAULT_NETWORK_PORT = 15964;
    private static final int DEFAULT_PACKET_CAPACITY = 32 * 1024;
    private List<MessageHandler> _handlers = new ArrayList<MessageHandler>( );
    private MessageHandler _general = null;
    private boolean _isRunning = true;
    private RecycleManager _recycler = new RecycleManager( DEFAULT_PACKET_CAPACITY );
    private ServerSocket _server = null;
    private Thread _serverThread = null;


    public AvatarServer( int port )
    {
        _handlers.add( _general = new MessageHandler( ) );
        _handlers.add( new VideoPlayHandler( ) );
        for( MessageHandler hdlr : _handlers )
        {
            hdlr.addListener( this );
            hdlr.start( );
        }
        try
        {
            _server = new ServerSocket( port );
            _serverThread = new Thread( this );
            _serverThread.start( );
        }
        catch( Exception ex )
        {
            System.err.println( "AvatarServer exception: " + ex.getMessage( ) );
            ex.printStackTrace( );
            _isRunning = false;
        }
    }


    public void close( )
    {
        _isRunning = false;
        if( _server != null )
        {
            try
            {
                _server.close( );
            }
            catch( IOException ex )
            {
                System.err.println( "AvatarServer exception: " + ex.getMessage( ) );
            }
            _server = null;
        }
        if( _serverThread != null )
        {
            try
            {
                _serverThread.join( );
            }
            catch( InterruptedException ex )
            {
                System.err.println( "AvatarServer exception: " + ex.getMessage( ) );
            }
            _serverThread = null;
        }
        for( MessageHandler hdlr : _handlers )
        {
            hdlr.close( );
            hdlr.removeListener( this );
            try
            {
                hdlr.join( );
            }
            catch( InterruptedException ex )
            {
                System.err.println( "AvatarServer exception: " + ex.getMessage( ) );
            }
        }
        _handlers.clear( );
    }


    public void morph( NetConnection connection, byte type )
    {
        for( MessageHandler hdlr : _handlers )
        {
            if( hdlr.getMessageType( ) == type )
            {
                hdlr.addConnection( connection );
            }
            else
            {
                hdlr.removeConnection( connection );
            }
        }
    }


    public void run( )
    {
        System.out.println( "AvatarServer server started" );
        while( _isRunning )
        {
            Socket socket = null;
            try
            {
                socket = _server.accept( );
            }
            catch( Exception ex )
            {
                System.err.println( "AvatarServer exception: " + ex.getMessage( ) );
                _isRunning = false;
            }
            if( socket != null )
            {
                NetConnection connection = new NetConnection( socket, _recycler );
                connection.start( );
                _general.addConnection( connection );
            }
            Thread.yield( );
        }
        System.out.println( "AvatarServer server stopped" );
    }


    /*
    public static void main( String[] args )
    {
        AvatarServer server = new AvatarServer( DEFAULT_NETWORK_PORT );
        try
        {
            System.out.println( "Press ENTER to close the application. . ." );
            Scanner keyboard = new Scanner( System.in );
            keyboard.nextLine( );
        }
        catch( Exception ex )
        {
        }
        server.close( );
        System.out.println( );
    }
    */

}
