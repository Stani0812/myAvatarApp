import java.util.ArrayList;
import java.util.List;

public class RecycleMemory
{
    private List<RecycleEventHandler> _evtRecycleHandlers = new ArrayList<RecycleEventHandler>( );
    private int _refCount = 0;
    public byte[] data;
    public int length;


    public RecycleMemory( int capacity )
    {
        data = new byte[capacity];
        length = 0;
    }


    public synchronized void addListener( RecycleEventHandler hdlr )
    {
        _evtRecycleHandlers.add( hdlr );
    }


    public synchronized void addRef( )
    {
        _refCount++;
    }


    public synchronized void ensureCapacity( int capacity )
    {
        if( data.length < capacity )
        {
            byte[] tmp = data;
            data = new byte[capacity];
            System.arraycopy( tmp, 0, data, 0, length );
        }
    }


    public synchronized void removeListener( RecycleEventHandler hdlr )
    {
        _evtRecycleHandlers.remove( hdlr );
    }


    public synchronized void removeRef( )
    {
        if( --_refCount <= 0 )
        {
            length = 0;
            for( RecycleEventHandler hdlr : _evtRecycleHandlers )
            {
                hdlr.recycle( this );
            }
        }
    }

}
