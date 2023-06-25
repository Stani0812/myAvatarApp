package hrilab.ss.network;

public interface NameChangeEventHandler
{

    void morph( NetConnection connection, String oldName, String newName );

}
