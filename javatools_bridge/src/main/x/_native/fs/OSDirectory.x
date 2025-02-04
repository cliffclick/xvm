import ecstasy.fs.Directory;
import ecstasy.fs.File;
import ecstasy.fs.FileWatcher;
import ecstasy.fs.Path;

/**
 * Native OS Directory implementation.
 */
const OSDirectory
        extends OSFileNode
        implements Directory
    {
    @Override
    Iterator<String> names()
        {
        return store.names(this:protected).iterator();
        }

    @Override
    Iterator<Directory> dirs()
        {
        return names()
            .filter(name ->
                {
                assert File|Directory node := find(name);
                return node.is(Directory);
                })
            .map(name ->
                {
                assert File|Directory node := find(name);
                return node.as(Directory);
                });
        }

    @Override
    Iterator<File> files()
        {
        return names()
            .filter(name ->
                {
                assert File|Directory node := find(name);
                return node.is(File);
                })
            .map(name ->
                {
                assert File|Directory node := find(name);
                return node.as(File);
                });
        }

    @Override
    Iterator<File> filesRecursively() { TODO("native"); }

    @Override
    conditional Directory|File find(String name)
        {
        return name.size == 0
                ? (True, this)
                : store.find(path + name);
        }

    @Override
    Directory dirFor(String name)
        {
        return store.dirFor(path + name);
        }

    @Override
    File fileFor(String name)
        {
        return store.fileFor(path + name);
        }

    @Override
    Boolean deleteRecursively() { TODO("native"); }

    @Override
    Cancellable watch(FileWatcher watcher)
        {
        return store.watchDir(this, watcher);
        }

    @Override
    Cancellable watchRecursively(FileWatcher watcher) { TODO("native"); }
    }