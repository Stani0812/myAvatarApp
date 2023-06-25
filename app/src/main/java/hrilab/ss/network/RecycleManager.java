package hrilab.ss.network;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class RecycleManager implements RecycleEventHandler
{
    private Queue<IRecyclable> _memories = new ConcurrentLinkedQueue<IRecyclable>( );
    private IMemoryFactory _factory = null;


    public RecycleManager( )
    {
    }


    public RecycleManager( IMemoryFactory factory )
    {
        _factory = factory;
    }


    public void clear( )
    {
        _memories.clear( );
    }


    public IRecyclable get( )
    {
        IRecyclable memory = _memories.poll( );
        if( memory != null )
        {
            memory.addRef( );
        }
        else if( _factory != null )
        {
            memory = _factory.create( );
            memory.addListener( this );
        }
        return memory;
    }


    public void recycle( IRecyclable memory )
    {
        _memories.add( memory );
    }

}
