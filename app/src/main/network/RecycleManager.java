import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class RecycleManager implements RecycleEventHandler
{
    private Queue<RecycleMemory> _packets = new ConcurrentLinkedQueue<RecycleMemory>( );
    private int _memoryCapacity = 0;


    public RecycleManager( int memoryCapacity )
    {
        _memoryCapacity = memoryCapacity;
    }


    public void clear( )
    {
        _packets.clear( );
    }


    public RecycleMemory get( )
    {
        RecycleMemory memory = _packets.poll( );
        if( memory == null )
        {
            memory = new RecycleMemory( _memoryCapacity );
            memory.addListener( this );
        }
        memory.addRef( );
        return memory;
    }


    public void recycle( RecycleMemory memory )
    {
        _packets.add( memory );
    }

}
