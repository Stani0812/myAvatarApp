import java.util.ArrayList;
import java.util.List;

public class VideoPlayHandler extends MessageHandler
{
    private static final long PING_INTERVAL = 5000;
    private byte[] _pingMessage = new byte[1];
    private long _pingTimestamp = 0;


    public VideoPlayHandler( )
    {
        _pingMessage[0] = getMessageType( );
    }


    public byte getMessageType( )
    {
        return MSG_VIDEO_PLAY;
    }


    protected long getThreadUpdateInterval( )
    {
        return 15; //ms
    }


    protected void initialize( )
    {
    }


    protected void process( NetConnection connection, byte[] data, int offset, int length )
    {
        if( length > 0 )
        {
            byte[] image = new byte[length];
            System.arraycopy( data, offset, image, 0, length );
        }
    }


    protected void release( )
    {
    }


    protected void update( )
    {
        if( _pingTimestamp <= System.currentTimeMillis( ) )
        {
            _pingTimestamp = System.currentTimeMillis( ) + PING_INTERVAL;
            broadcast( _pingMessage, 0, _pingMessage.length );
        }
    }

}
