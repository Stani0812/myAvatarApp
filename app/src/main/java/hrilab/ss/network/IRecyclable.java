package hrilab.ss.network;

public interface IRecyclable
{

    void addListener( RecycleEventHandler hdlr );

    void addRef( );

    void removeListener( RecycleEventHandler hdlr );

    void removeRef( );

}
