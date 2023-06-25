package hrilab.ss.network;

import java.util.ArrayList;
import java.util.List;

public class RecycleMemory implements IRecyclable
{
    private List<RecycleEventHandler> _evtRecycleHandlers = new ArrayList<RecycleEventHandler>( );
    private int _refCount = 1;


    public RecycleMemory( )
    {
    }


    public synchronized void addListener( RecycleEventHandler hdlr )
    {
        _evtRecycleHandlers.add( hdlr );
    }


    public synchronized void addRef( )
    {
        _refCount++;
    }


    public synchronized void removeListener( RecycleEventHandler hdlr )
    {
        _evtRecycleHandlers.remove( hdlr );
    }


    public synchronized void removeRef( )
    {
        if( --_refCount <= 0 )
        {
            _refCount = 0;
            onRecycleRequesting( );
            for( RecycleEventHandler hdlr : _evtRecycleHandlers )
            {
                hdlr.recycle( this );
            }
        }
    }


    protected void onRecycleRequesting( )
    {
    }

}
