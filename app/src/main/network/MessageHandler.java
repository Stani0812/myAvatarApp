import java.util.ArrayList;
import java.util.List;

public class MessageHandler extends Thread
{
    protected static final int HEADER_SIZE = NetConnection.HEADER_SIZE;
    protected static final int MSG_DISCONNECT = 0;
    protected static final int MSG_VIDEO_PLAY = 1;
    private List<NetConnection> _connections = new ArrayList<NetConnection>( );
    private List<TypeChangeEventHandler> _evtTypeChangeHandlers = new ArrayList<TypeChangeEventHandler>( );
    private boolean _isRunning = true;


    public MessageHandler( )
    {
    }


    public void addListener( TypeChangeEventHandler evthdlr )
    {
        _evtTypeChangeHandlers.add( evthdlr );
    }


    public void removeListener( TypeChangeEventHandler evthdlr )
    {
        _evtTypeChangeHandlers.remove( evthdlr );
    }


    public void addConnection( NetConnection connection )
    {
        synchronized( _connections )
        {
            _connections.add( connection );
            System.out.println( "Added a connection from " + connection.address( ) + " to handler " + getMessageType( ) + "" );
        }
    }


    public void removeConnection( NetConnection connection )
    {
        synchronized( _connections )
        {
            if( _connections.remove( connection ) )
            {
                System.out.println( "Removed a connection to " + connection.address( ) + " from handler " + getMessageType( ) + "" );
            }
        }
    }


    public void close( )
    {
        _isRunning = false;
    }


    public byte getMessageType( )
    {
        return MSG_DISCONNECT;
    }


    public void run( )
    {
        long lastNumConnections = 0;
        long tsLastUpdate = System.currentTimeMillis( );
        while( _isRunning )
        {
            long tsNow = System.currentTimeMillis( );
            long tsDiff = getThreadUpdateInterval( ) - (tsNow - tsLastUpdate);
            if( tsDiff <= 0 )
            {
                tsLastUpdate = tsNow;
                synchronized( _connections )
                {
                    if( lastNumConnections == 0 && _connections.size( ) > 0 )
                    {   // we received the first connection => initialize
                        initialize( );
                    }
                    lastNumConnections = _connections.size( );
                    update( ); // update the handler
                    for( int i = _connections.size( ) - 1; i >= 0; --i )
                    {   // check message from each connection
                        updateConnection( i );
                    }
                    if( lastNumConnections != 0 && _connections.size( ) <= 0 )
                    {   // we lost the last connection => release
                        release( );
                    }
                }
                try { Thread.sleep( 0 ); } catch( InterruptedException ex ) { }
            }
            else
            {
                try { Thread.sleep( tsDiff / 4 ); } catch( InterruptedException ex ) { }
            }
        }
        synchronized( _connections )
        {
            for( int i = _connections.size( ) - 1; i >= 0; --i )
            {
                closeConnectionAt( i );
            }
        }
        release( );
    }


    protected void broadcast( byte[] data, int offset, int length )
    {
        for( NetConnection connection : _connections )
        {
            connection.send( data, offset, length );
        }
    }


    protected void broadcast( byte[][] dataCollection )
    {
        for( NetConnection connection : _connections )
        {
            connection.send( dataCollection );
        }
    }


    protected long getThreadUpdateInterval( )
    {
        return 1000L / 10L; // 100ms
    }


    protected void initialize( )
    {
    }


    protected void process( NetConnection connection, byte[] data, int offset, int length )
    {
    }


    protected void release( )
    {
    }


    protected void update( )
    {
    }


    private void closeConnectionAt( int index )
    {
        NetConnection connection = _connections.remove( index );
        connection.close( );
        try
        {
            connection.join( );
        }
        catch( InterruptedException ex )
        {
            System.err.println( "MessageHandler exception: " + ex.getMessage( ) );
        }
    }


    private void updateConnection( int index )
    {
        NetConnection connection = _connections.get( index );
        if( connection.isConnected( ) )
        {
            RecycleMemory memory = connection.getNextData( );
            if( memory != null )
            {
                if( memory.data[HEADER_SIZE] == MSG_DISCONNECT )
                {
                    closeConnectionAt( index );
                }
                else if( memory.data[HEADER_SIZE] == getMessageType( ) )
                {
                    process( connection, memory.data, HEADER_SIZE + 1, memory.length - (HEADER_SIZE + 1) );
                }
                else
                {
                    for( TypeChangeEventHandler hdlr : _evtTypeChangeHandlers )
                    {
                        hdlr.morph( connection, memory.data[HEADER_SIZE] );
                    }
                }
                memory.removeRef( );
            }
            connection.ping( );
        }
        else
        {
            closeConnectionAt( index );
        }
    }

}
