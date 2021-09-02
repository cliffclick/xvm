import collections.ArrayDeque;
import collections.SparseIntSet;

import json.ElementOutput;
import json.Mapping;
import json.ObjectInputStream;
import json.ObjectOutputStream;
import json.Parser;

import model.SysInfo;

import oodb.RootSchema;
import oodb.Transaction.TxInfo;

import storage.ObjectStore;

import Catalog.BuiltIn;


/**
 * The transaction manager is the clearinghouse for transactions. As a service, it naturally orders
 * the actions of concurrent transactions. When a transaction is instructed to commit, this service
 * causes the actual commit to occur, by issuing the instructions to the various enlisted
 * [ObjectStore] instances that were modified within the transaction.
 *
 * The transaction manager is not exposed; it's work is implied by the OODB interfaces, but it is
 * not part of the public API. It sits at the junction of three actors:
 *
 * * The [Catalog] creates the transaction manager, and holds on to it. The lifecycle of the
 *   transaction manager is controlled by the Catalog. When the Catalog transitions to its `Running`
 *   state, it enables the transaction manager, and as the Catalog leaves the `Running` state, it
 *   disables (and may eventually `close()`) the transaction manager. When the transaction manager
 *   is disabled, it immediately stops accepting new work, but it *will* attempt to complete the
 *   processing of any transactions that have already successfully prepared.
 *
 * * The [ObjectStore] instances each have a reference to the transaction manager, in order to ask
 *   it questions. The primary question is: "What is the read transaction ID that I should use for
 *   a specific write transaction ID?", which is used by the transaction manager as an indication
 *   to enlist the ObjectStore in the transaction, and to select the read transaction ID the first
 *   time that the question is asked for a particular write transaction ID.
 *
 * * The [Client] instances each have a reference to the transaction manager, in order to manage
 *   transaction boundaries. When a client requires a transaction, it creates it via
 *   [begin()](begin), it commits it via [commit()](commit), or rolls it back via
 *   [rollback()](rollback).
 *
 * The transaction manager could easily become a bottleneck in a highly concurrent system, so it
 * conducts its work via asynchronous calls as much as possible, usually by delegating work to
 * [Client] and [ObjectStore] instances.
 *
 * There are three file categories managed by the TxManager:
 *
 * * The `txmgr.json` file records the last known "check point" of the transaction manager, which
 *   is when the transaction manager was last enabled, disabled, or when the transaction log last
 *   rolled (the then-current `txlog.json` log file grew too large, so it was made an archive and a
 *   new one was started). It contains its a record of its then-current status, (size, timestamp,
 *   and transaction range, ending with the then-latest commit id) of the `txlog.json` log file, and
 *   a record of all known log archives, including the file name, size, timestamp, and transaction
 *   range for each.
 *
 * * The `txlog.json` file is the current transaction log. It is an array of entries, most of which
 *   are likely to be transaction entries, plus a few transaction manager-related entries, such
 *   as (i) log created (the first entry in the log), (2) log archived (the last entry in the log),
 *   (3) TxManager enabled, (4) TxManager disabled.
 *
 * * The log archives are copies of the previous `txlog.json` files, from when those files passed a
 *   certain size threshold. They are named `txlog_<datetime>.json`
 */
service TxManager<Schema extends RootSchema>(Catalog<Schema> catalog)
        implements Closeable
    {
    construct(Catalog<Schema> catalog)
        {
        this.catalog = catalog;

        // build the quick lookup information for the optional transactional "modifiers"
        // (validators, rectifiers, distributors, and async triggers)
        Boolean hasValidators    = catalog.metadata?.dbObjectInfos.any(info -> !info.validators   .empty) : False;
        Boolean hasRectifiers    = catalog.metadata?.dbObjectInfos.any(info -> !info.rectifiers   .empty) : False;
        Boolean hasDistributors  = catalog.metadata?.dbObjectInfos.any(info -> !info.distributors .empty) : False;
        Boolean hasAsyncTriggers = catalog.metadata?.dbObjectInfos.any(info -> !info.asyncTriggers.empty) : False;
        Boolean hasSyncTriggers  = hasValidators |  hasRectifiers | hasDistributors;

        if (hasValidators)
            {
            validators = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.validators.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasRectifiers)
            {
            rectifiers = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.rectifiers.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasDistributors)
            {
            distributors = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.distributors.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        if (hasSyncTriggers)
            {
            asyncTriggers = new SparseIntSet()
                    .addAll(validators)
                    .addAll(rectifiers)
                    .addAll(distributors);
            }

        if (hasAsyncTriggers)
            {
            syncTriggers = catalog.metadata?.dbObjectInfos
                    .filter(info -> !info.asyncTriggers.empty)
                    .map(info -> info.id, new SparseIntSet())
                    .as(Set<Int>);
            }

        internalJsonSchema = catalog.internalJsonSchema;
        }
    finally
        {
        reentrancy = Open;
        }


    // ----- properties ----------------------------------------------------------------------------

    @Inject Clock clock;

    /**
     * The runtime state of the TxManager:
     * * **Initial** - the TxManager has been newly created, but has not been used yet
     * * **Enabled** - the TxManager has been [enabled](enable), and can process transactions
     * * **Disabled** - the TxManager was previously enabled, but has been [disabled](disable), and
     *   can not process transactions until it is re-enabled
     * * **Closed** - the TxManager has been closed, and may not be used again
     */
    enum Status {Initial, Enabled, Disabled, Closed}

    /**
     * The status of the transaction manager. For the most part, this could be handled with a simple
     * "Boolean enabled" flag, but the two additional book-end states provide more clarity for
     * the terminal points of the life cycle, and thus (in theory) better assertions.
     */
    public/private Status status = Initial;

    /**
     * The ObjectStore for each user defined DBObject in the `Catalog`. These provide the I/O for
     * the database.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] appStores = new ObjectStore[];

    /**
     * The ObjectStore for each DBObject in the system schema.
     *
     * This data structure is lazily populated as necessary from the [Catalog], which is responsible
     * for creating and holding the references to each [ObjectStore].
     */
    private ObjectStore?[] sysStores = new ObjectStore[];

    /**
     * The system directory (./sys).
     */
    @Lazy public/private Directory sysDir.calc()
        {
        Directory root = catalog.dir;
        return root.store.dirFor(root.path + "sys");
        }

    /**
     * The transaction log file (sys/txlog.json).
     */
    @Lazy public/private File logFile.calc()
        {
        Directory root = catalog.dir;
        return root.store.fileFor(root.path + "sys" + "txlog.json");
        }

    /**
     * The transaction log file (sys/txlog.json).
     */
    @Lazy public/private File statusFile.calc()
        {
        Directory root = catalog.dir;
        return root.store.fileFor(root.path + "sys" + "txmgr.json");
        }

    /**
     * A snapshot of information about a transactional log file.
     */
    static const LogFileInfo(String name, Range<Int> txIds, Int size, DateTime timestamp)
        {
        LogFileInfo with(String?     name      = Null,
                         Range<Int>? txIds     = Null,
                         Int?        size      = Null,
                         DateTime?   timestamp = Null)
            {
            return new LogFileInfo(name       ?: this.name,
                                   txIds      ?: this.txIds,
                                   size       ?: this.size,
                                   timestamp  ?: this.timestamp);
            }
        }

    /**
     * A JSON Mapping to use to serialize instances of LogFileInfo.
     */
    @Lazy protected Mapping<LogFileInfo> logFileInfoMapping.calc()
        {
        return internalJsonSchema.ensureMapping(LogFileInfo);
        }

    /**
     * A JSON Mapping to use to serialize instances of LogFileInfo.
     */
    @Lazy protected Mapping<LogFileInfo[]> logFileInfoArrayMapping.calc()
        {
        return internalJsonSchema.ensureMapping(LogFileInfo[]);
        }

    /**
     * A record of all of the existent rolled-over log files.
     */
    protected/private LogFileInfo[] logInfos = new LogFileInfo[];

    /**
     * The maximum size log to store in any one log file.
     * TODO this setting should be configurable (need a "Prefs" API)
     */
    // TODO protected Int maxLogSize = 1 << 20; // 1MB
    protected Int maxLogSize = 1000;

    /**
     * The JSON Schema to use (for system classes).
     */
    protected/private json.Schema internalJsonSchema;

    /**
     * An illegal transaction ID used to indicate that no ID has been assigned yet.
     */
    static Int NO_TX = Int.minvalue;

    /**
     * The count of transactions (referred to as "write transactions") that this transaction manager
     * has created. This counter is used to provide unique write transaction IDs for each in-flight
     * transaction, and also for any trigger processing that occurs during the prepare phase of a
     * transaction. These values are not stored persistently; they only exist for the duration of an
     * in-flight transaction.
     *
     * When a new transaction manager is instantiated, the counter always  starts over at zero,
     * since the count and the ids are ephemeral.
     *
     * An ID is calculated as the negative of the count; for example, the first transaction would
     * increment the count to `1`, giving the transaction a "writeId" of `-1`. The corresponding
     * "readId" is not assigned until an operation within the transaction attempts to perform a
     * read, at which point the transaction manager will provide the `lastPrepared` transaction as
     * the "readId".
     *
     * (Note that the second `writeId` will be `-5`, and not `-2`. The count is incremented by 1
     * step, but writeIds are incremented by 4 steps, because each transaction goes through up to 4
     * states from beginning to fully committed.)
     */
    public/private Int txCount = 0;

    /**
     * The transaction id that was last successfully prepared. There is a period between when a
     * transaction finishes preparing, and when it finishes committing, during which the transaction
     * can be used as the basis for subsequent transactions (to help minimize the chance of a
     * rollback caused by data changing), even though the transaction has not been verified to have
     * been committed to disk.
     *
     * Note: `lastPrepared >= lastCommitted`
     */
    public/private Int lastPrepared = 0;

    /**
     * The transaction id that was last successfully committed. When a database is newly created,
     * this value is zero; otherwise, this value is recorded on disk and restored when the
     * transaction manager reloads that persistent state.
     *
     * Note: `lastPrepared >= lastCommitted`
     */
    public/private Int lastCommitted = 0;

    /**
     * The transactions that have begun, but have not yet committed or rolled back. The key is the
     * client id. Each client may have up to one "in flight" transaction at any given time.
     */
    protected/private Map<Int, TxRecord> byClientId = new HashMap();

    /**
     * The transactions that have begun, but have not yet committed or rolled back, keyed by the
     * write transaction id. These are the same transactions as exist in the [byClientId] map, but
     * indexed by writeId instead of by client id.
     */
    protected/private Map<Int, TxRecord> byWriteId = new HashMap();

    /**
     * A **count** of the transactions that have begun, but have not yet committed or rolled back,
     * keyed by the read transaction id. The transaction manager uses this information to determine
     * when a historical read transaction can be discarded by the ObjectStore instances; as long as
     * a read transaction is in use by a write transaction, its information must be retained and
     * kept available to clients.
     */
    protected/private Map<Int, Int> byReadId = new HashMap();

    /**
     * The set of ids of DBObjects that have validators.
     */
    protected/private Set<Int> validators = [];
    /**
     * The set of ids of DBObjects that have rectifiers.
     */
    protected/private Set<Int> rectifiers = [];
    /**
     * The set of ids of DBObjects that have distributors.
     */
    protected/private Set<Int> distributors = [];
    /**
     * The set of ids of DBObjects that have validators, rectifiers, and/or distributors.
     */
    protected/private Set<Int> syncTriggers = [];
    /**
     * The set of ids of DBObjects that have asynchronous triggers.
     */
    protected/private Set<Int> asyncTriggers = [];

    /**
     * A pool of artificial clients for processing both sync (validator/rectifier/distributor) and
     * async triggers.
     */
    protected/private ArrayDeque<Client<Schema>> clientCache = new ArrayDeque();

    /**
     * The writeId of the transaction, if any, that is currently preparing.
     */
    protected/private Int currentlyPreparing = NO_TX;

    /**
     * The count of transactions, if any, that are currently committing.
     */
    protected/private Int currentlyCommitting = 0;

    /**
     * A record of a pending operation, with its deferred result.
     */
    class Pending(Int txId, TxRecord rec, Future<Boolean> result);

    /**
     * A queue of transactions that are waiting for the "green light" to start preparing. While this
     * could be easily implemented using just a critical section, it would dramatically impinge on
     * the concurrent execution of transaction processing, particularly since only a small portion
     * of the prepare work is done by the TxManager service -- most is delegated to the various
     * enlisted ObjectStore instances, or onto Client objects that handle the potentially expensive
     * tasks of transaction validation, rectification, and distribution. As a result, the prepare
     * process is staggeringly asynchronous, and many other things -- except other prepares! -- can
     * be occurring while a transaction is being prepared. The result of this design is that any
     * transaction that shows up to be prepared while another transaction is already being prepared,
     * creates a future and places it into the queue for processing once all of the transactions
     * ahead of it have prepared.
     */
    protected/private ArrayDeque<Pending> pendingPrepare = new ArrayDeque();

    /**
     * A queue of transactions that are have prepared but have not yet been committed to disk. There
     * is no functional reason for queueing commits; the rationale is purely an optimization, based
     * on the likelihood that conducting I/O on behalf of a number of transactions will be
     * inherently more efficient than conducting I/O on behalf of each individual transaction in
     * sequence.
     */
    protected/private ArrayDeque<Pending> pendingCommit = new ArrayDeque();


    // ----- lifecycle management ------------------------------------------------------------------

    /**
     * Allow the transaction manager to accept work from clients.
     *
     * This method is intended to only be used by the [Catalog].
     */
    void enable()
        {
        assert status == Initial || status == Disabled;
        using (val cs = new CriticalSection(Exclusive))
            {
            if (status == Initial)
                {
                if (!(statusFile.exists ? openLog() : createLog()))
                    {
                    assert recoverLog();
                    }
                }

            status = Enabled;
            }
        }

    /**
     * Verify that the transaction manager is open for client requests.
     *
     * @return True iff the transaction manager is enabled
     *
     * @throws IllegalState iff the transaction manager is not enabled
     */
    Boolean checkEnabled()
        {
        assert status == Enabled as $"TxManager is not enabled (status={status})";
        return True;
        }

    /**
     * Stop the transaction manager from accepting any work from clients, and roll back any
     * transactions that have not already prepared.
     *
     * This method is intended to only be used by the [Catalog].
     *
     * @param abort  pass True to abort everything that can still be aborted in order to disable as
     *               quickly as possible with minimal risk of failure
     */
    void disable(Boolean abort = False)
        {
        switch (status)
            {
            case Initial:
                status = Disabled;
                return;

            case Enabled:
                Exception? failure = Null;
                using (val cs = new CriticalSection(Exclusive))
                    {
                    if (!abort)
                        {
                        // commit everything that has already prepared
                        // TODO attempt to commit any prepared transactions
                        }

                    // abort everything that is left over
                    // TODO

                    closeLog();
                    }
                throw failure?;
                return;

            case Disabled:
            case Closed:
                return;
            }
        }

    @Override
    void close(Exception? cause = Null)
        {
        if (status == Enabled)
            {
            disable(abort = cause != Null);
            }

        status = Closed;
        }


    // ----- file management  ----------------------------------------------------------------------

    /**
     * Read the TxManager status file.
     *
     * @return the mutable array of LogFileInfo records from the status file, from oldest to newest
     */
    protected conditional LogFileInfo[] readStatus()
        {
        if (!statusFile.exists)
            {
            return False;
            }

        try
            {
            String            json   = statusFile.contents.unpackString();
            ObjectInputStream stream = new ObjectInputStream(internalJsonSchema, json.toReader());
            LogFileInfo[]     infos  = logFileInfoArrayMapping.read(stream.ensureElementInput());
            return True, infos.toArray(Mutable, True);
            }
        catch (Exception e)
            {
            log($"Exception reading TxManager status file: {e}");
            return False;
            }
        }

    /**
     * Read the TxManager status file.
     *
     * @return the array of LogFileInfo records from the status file, from oldest to newest
     */
    protected void writeStatus()
        {
        // render all of the LogFileInfo records as JSON, but put each on its own line
        StringBuffer buf = new StringBuffer();
        using (ObjectOutputStream stream = new ObjectOutputStream(internalJsonSchema, buf))
            {
            Loop: for (LogFileInfo info : logInfos)
                {
                using (ElementOutput out = stream.createElementOutput())
                    {
                    buf.append(",\n");
                    logFileInfoMapping.write(out, info);
                    }
                }
            }

        // wrap it as a JSON array and write it out
        buf[0] = '[';
        buf.append("\n]");
        statusFile.contents = buf.toString().utf8();
        }

    /**
     * Create the transaction log.
     *
     * @return True if the transaction log did not exist, and now it does
     */
    protected Boolean createLog()
        {
        // neither the log nor the status file should exist
        if (logFile.exists || statusFile.exists)
            {
            return False;
            }

        assert lastCommitted == 0;
        assert logInfos.empty;

        // create the log with an entry in it denoting the time the log was created
        logFile.parent?.ensure();
        initLogFile();

        logInfos.add(new LogFileInfo(logFile.name, 0..0, logFile.size, logFile.modified));
        writeStatus();

        return True;
        }

    /**
     * Initialize the "current" transaction log file.
     */
    protected void initLogFile()
        {
        logFile.contents = $|[
                            |\{"status":"created", "timestamp":"{clock.now.toString(True)}", "prev-tx":{lastCommitted}}
                            |]
                            .utf8();
        }

    /**
     * Open the transaction log.
     *
     * @return True if the transaction log did exist, and was successfully opened
     */
    protected Boolean openLog()
        {
        // need to have both the log and the status file
        // load the status file to get a last-known snapshot of the log files
        if (logFile.exists, statusFile.exists, LogFileInfo[] logInfos := readStatus(),
                !logInfos.empty)
            {
            // if the current log file is as-expected, then assume that we're good to go
            LogFileInfo current = logInfos[logInfos.size-1];
            if (current.size == logFile.size && current.timestamp == logFile.modified)
                {
                // append the "we're open" message to the log
                addLogEntry($|\{"status":"opened", "timestamp":"{clock.now.toString(True)}"}
                           );

                this.logInfos      = logInfos;
                this.lastCommitted = current.txIds.effectiveUpperBound;
                this.lastPrepared  = this.lastCommitted;

                return True;
                }
            }

        return False;
        }

    /**
     * Make a "best attempt" to automatically recover the transaction log.
     *
     * @return True if the transaction log did exist, and was successfully opened
     */
    protected Boolean recoverLog()
        {
        // start by trying to read the status file
        LogFileInfo[] logInfos;
        Int           lastTx  = -1;
        if (logInfos := readStatus())
            {
            // validate the LogFileInfo entries; assume that older files may have been deleted, and
            // that the latest size/timestamp for the current logFile may not have been recorded
            Int firstIndex = 0;
            Loop: for (LogFileInfo oldInfo : logInfos)
                {
                File infoFile = sysDir.fileFor(oldInfo.name);
                if (infoFile.exists)
                    {
                    LogFileInfo newInfo;
                    try
                        {
                        newInfo = loadLog(infoFile);
                        }
                    catch (Exception e)
                        {
                        log($|TxManager was unable to load the log file {logFile} due to an\
                             | exception; abandoning automatic recovery; exception: {e}
                           );
                        return False;
                        }

                    if (newInfo.txIds.size > 0)
                        {
                        Int firstSegmentTx = newInfo.txIds.effectiveFirst;
                        Int lastSegmentTx  = newInfo.txIds.effectiveUpperBound;
assert:debug;
                        if (lastTx < 0 || firstSegmentTx == lastTx+1
                                && (newInfo.txIds.size == 0 || lastSegmentTx > lastTx))
                            {
                            if (newInfo.txIds.size > 0)
                                {
                                lastTx = lastSegmentTx;
                                }
                            }
                        else
                            {
                            log($|TxManager log files {logInfos[Loop.count-1].name} and {newInfo.name} do\
                                 | not hold contiguous transaction ranges; abandoning automatic recovery
                               );
                            return False;
                            }
                        }
                    logInfos[Loop.count] = newInfo;
                    }
                else
                    {
                    if (Loop.count == firstIndex)
                        {
                        if (infoFile.name == logFile.name)
                            {
                            log($|TxManager automatic recovery was unable to load the current\
                                 | segment of the transaction log, because it does not exist;\
                                 | an empty file will be created automatically
                               );
                            }
                        else
                            {
                            log($|TxManager was unable to load a historical segment of the\
                                 | transaction log from file {infoFile}, because the file does not\
                                 | exist; the missing information is assumed to have been archived\
                                 | or purged; recovery will continue
                               );
                            }

                        ++firstIndex;
                        }
                    else if (oldInfo.txIds.size > 0)
                        {
                        log($|TxManager was unable to load the log file {infoFile}, because the file\
                             | does not exist; abandoning automatic recovery
                           );
                        return False;
                        }
                    else
                        {
                        log($|TxManager was unable to load the log file {infoFile}, because the\
                             | file does not exist; however, it contained no transactional records,\
                             | so recovery will continue
                           );
                        }
                    }
                }

            if (firstIndex > 0)
                {
// TODO GG slice of size zero e.g. [x..x) should return empty array, but blows with exception:
//java.lang.IllegalArgumentException: 1 > 0
//	at java.base/java.util.Arrays.copyOfRange(Arrays.java:3990)
//	at java.base/java.util.Arrays.copyOfRange(Arrays.java:3950)
//	at org.xvm.runtime.template._native.collections.arrays.xRTDelegate.createCopyImpl(xRTDelegate.java:577)
//	at org.xvm.runtime.template._native.collections.arrays.xRTSlicingDelegate.createCopyImpl(xRTSlicingDelegate.java:106)
//	at org.xvm.runtime.template._native.collections.arrays.xRTDelegate.createCopy(xRTDelegate.java:493)
//	at org.xvm.runtime.template._native.collections.arrays.xRTDelegate.invokeNative1(xRTDelegate.java:247)
//	at org.xvm.runtime.CallChain.invoke(CallChain.java:138)
//	at org.xvm.asm.op.Invoke_11.complete(Invoke_11.java:112)
//	at org.xvm.asm.op.Invoke_11.resolveArg(Invoke_11.java:105)
//	at org.xvm.asm.op.Invoke_11.process(Invoke_11.java:92)
//	at org.xvm.runtime.ServiceContext.execute(ServiceContext.java:584)
                logInfos = firstIndex == logInfos.size
                        ? logInfos.clear()
                        : logInfos.slice([firstIndex..logInfos.size)).reify(Mutable);
                }
            }
        else
            {
            log("TxManager status file was not recoverable");

            // scan the directory for log files
            File[] logFiles = findLogs();
            if (logFiles.empty)
                {
                log("TxManager failed to locate any log files to recover; abandoning automatic recovery");
                return False;
                }

            // parse and order the log files
            logInfos = loadLogs(logFiles);

            // verify that the transactions are ordered and contiguous
            Loop: for (LogFileInfo info : logInfos)
                {
                if (lastTx >= 0)
                    {
                    if (lastTx == info.txIds.effectiveFirst-1)
                        {
                        if (info.txIds.size > 0)
                            {
                            lastTx = info.txIds.effectiveUpperBound;
                            }
                        }
                    else
                        {
                        log($|TxManager log files {logInfos[Loop.count-1].name} and {info.name} do\
                             | not hold contiguous transaction ranges; abandoning automatic recovery
                           );
                        return False;
                        }
                    }
                else
                    {
                    lastTx = info.txIds.effectiveUpperBound;
                    assert lastTx >= 0;
                    }
                }
            }

        // create the "current" log file, if one does not exist
        if (logFile.exists)
            {
            // append a recovery message to the log
            addLogEntry($|\{"status":"recovered", "timestamp":"{clock.now.toString(True)}"}
                       );
            logInfos[logInfos.size-1] = logInfos[logInfos.size-1].with(size=logFile.size,
                                                                       timestamp=logFile.modified);
            }
        else
            {
            log("TxManager current segment of transaction log is missing, so one will be created");
            String timestamp = clock.now.toString(True);
            logFile.contents = $|[
                                |\{"status":"created", "timestamp":"{timestamp}", "prev-tx":{lastTx}},
                                |\{"status":"recovered", "timestamp":"{timestamp}"}
                                |]
                                .utf8();
            LogFileInfo info = new LogFileInfo(logFile.name, [lastTx+1..lastTx+1), logFile.size, logFile.modified);
            if (logInfos[logInfos.size-1].name == logFile.name)
                {
                logInfos[logInfos.size-1] = info;
                }
            else
                {
                logInfos += info;
                }
            }

        // rebuild the status file
        this.logInfos = logInfos;
        writeStatus();

        log("TxManager automatic recovery completed");
        return openLog();
        }

    /**
     * Determine if the passed file is a log file.
     *
     * @param file  a possible log file
     *
     * @return the DateTime that the LogFile was rotated, or Null if it is the current log file
     */
    static conditional DateTime? isLogFile(File file)
        {
        String name = file.name;
        if (name == "txlog.json")
            {
            return True, Null;
            }

        if (name.startsWith("txlog_") && name.endsWith(".json"))
            {
            String timestamp = name[6..name.size-5);
            try
                {
                return True, new DateTime(timestamp);
                }
            catch (Exception e) {}
            }

        return False;
        }

    /**
     * Compare two log files for order.
     *
     * @param file1  the first log file
     * @param file2  the second log file
     *
     * @return the order to sort the two files into
     */
    static Ordered orderLogFiles(File file1, File file2)
        {
        assert DateTime? dt1 := isLogFile(file1);
        assert DateTime? dt2 := isLogFile(file2);

        // sort the null datetime to the end, because it represents the "current" log file
        return dt1? <=> dt2? : switch (dt1, dt2)
            {
            case (Null, Null): Equal;
            case (Null, _): Greater;
            case (_, Null): Lesser;
            default: assert;
            };
        }

    /**
     * Find all of the log files.
     *
     * @return a mutable array of log files, sorted from oldest to current
     */
    protected File[] findLogs()
        {
        return sysDir.files()
                .filter(f -> isLogFile(f) ? True : False) // TODO GG
                .sorted(orderLogFiles)
                .toArray(Mutable);
        }

    /**
     * Obtain corresponding LogFileInfo objects for each of the specified log files.
     *
     * @param logFiles an array of log files
     *
     * @return a mutable array of [LogFileInfo]
     */
    protected LogFileInfo[] loadLogs(File[] logFiles)
        {
        return logFiles.map(f -> loadLog(f), new LogFileInfo[]).as(LogFileInfo[]).toArray(Mutable, True);
        }

    /**
     * Obtain the LogFileInfo for the specified log file.
     *
     * @param logFile a log file
     *
     * @return the [LogFileInfo] for the file
     */
    protected LogFileInfo loadLog(File logFile)
        {
        String json = logFile.contents.unpackString();

        Int first = -1;
        Int last  = -1;
        using (Parser logParser = new Parser(json.toReader()))
            {
            using (val arrayParser = logParser.expectArray())
                {
                while (!arrayParser.eof)
                    {
                    using (val objectParser = arrayParser.expectObject())
                        {
                        Int txId;
                        if (objectParser.matchKey("_tx"))
                            {
                            txId = objectParser.expectInt();
                            }
                        else if (objectParser.matchKey("status") && objectParser.findKey("prev-tx"))
                            {
                            txId = objectParser.expectInt() + 1;
                            }
                        else
                            {
                            continue;
                            }

                        if (txId > last)
                            {
                            if (first < 0)
                                {
                                first = txId;
                                }
                            last = txId;
                            }
                        }
                    }
                }
            }

        assert first >= 0 as $"Log file \"{logFile.name}\" is missing required header information";
        return new LogFileInfo(logFile.name, first..last, logFile.size, logFile.modified);
        }

    /**
     * Append a log entry to the log file.
     *
     * @param entry  a JSON object, rendered as a string, to append to the log
     */
    protected void addLogEntry(String entry)
        {
        Int length = logFile.size;
        logFile.truncate(length-2)
               .append($",\n{entry}\n]".utf8());
        }

    /**
     * Move the current log to an archived state, and create a new, empty log.
     */
    protected void rotateLog()
        {
        assert !logInfos.empty;

        String timestamp = clock.now.toString(True);
        addLogEntry($|\{"status":"archived", "timestamp":"{timestamp}"}
                   );

        String rotatedName = $"txlog_{timestamp}.json";
        assert File rotatedFile := logFile.renameTo(rotatedName);

        LogFileInfo previousInfo = logInfos[logInfos.size-1];
        LogFileInfo rotatedInfo  = new LogFileInfo(rotatedName,
                previousInfo.txIds.first..lastCommitted, rotatedFile.size, rotatedFile.modified);

        initLogFile();
        LogFileInfo currentInfo = new LogFileInfo(logFile.name, [lastCommitted+1..lastCommitted+1),
                logFile.size, logFile.modified);

        logInfos[logInfos.size-1] = rotatedInfo;
        logInfos += currentInfo;
        writeStatus();
        }

    /**
     * Mark the transaction log as being closed.
     */
    protected void closeLog()
        {
        addLogEntry($|\{"status":"closed", "timestamp":"{clock.now.toString(True)}"}
                   );

        // update the "current log file" status record
        LogFileInfo oldInfo = logInfos[logInfos.size-1];
        assert oldInfo.name == logFile.name;
        Range<Int> txIds = oldInfo.txIds;
        if (lastCommitted > txIds.effectiveUpperBound)
            {
            txIds = txIds.effectiveLowerBound .. lastCommitted;
            }
        LogFileInfo newInfo = new LogFileInfo(logFile.name, txIds, logFile.size, logFile.modified);
        logInfos[logInfos.size-1] = newInfo;
        writeStatus();
        }


    // ----- transactional ID helpers --------------------------------------------------------------

    /**
     * Determine if the specified txId indicates a read ID, versus a write ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for read transaction IDs
     */
    static Boolean isReadTx(Int txId)
        {
        return txId >= 0;
        }

    /**
     * Determine if the specified txId indicates a write ID, versus a read ID.
     *
     * @param txId  a transaction identity
     *
     * @return True iff the txId is in the range reserved for write transaction IDs
     */
    static Boolean isWriteTx(Int txId)
        {
        return txId < 0;
        }

    /**
     * Obtain the transaction counter that was used to create the specified writeId.
     *
     * @param writeId  a transaction ID of type `WriteId`, `Validating`, `Rectifying`, or
     *                 `Distributing`
     *
     * @return the original transaction counter
     */
    static Int writeTxCounter(Int writeId)
        {
        assert isWriteTx(writeId);
        return -writeId >>> 2;
        }

    /**
     * Categories of transaction IDs.
     */
    enum TxType {ReadOnly, Open, Validating, Rectifying, Distributing}

    /**
     * Determine the category of the transaction ID.
     *
     * @param txId  any transaction ID
     *
     * @return the TxType for the specified transaction ID
     */
    static TxType txType(Int txId)
        {
        return txId >= 0
                ? ReadOnly
                : TxType.values[1 + (-txId & 0b11)];
        }

    /**
     * Given a transaction counter, produce a transaction ID of type `WriteId`.
     *
     * @param counter  the transaction counter
     *
     * @return the writeId for the transaction counter
     */
    static Int generateWriteId(Int counter)
        {
        assert counter >= 1;
        return -(counter<<2);
        }

    /**
     * Given a transaction `writeId`, generate a transaction id for the specified phase
     */
    static Int generateTxId(Int txId, TxType txType)
        {
        assert isWriteTx(txId), txType != ReadOnly;
        return -((-txId & ~0b11) | txType.ordinal - 1);
        }


    // ----- transactional API ---------------------------------------------------------------------

    /**
     * Begin a new transaction when it performs its first operation.
     *
     * This method is intended to only be used by the [Client].
     *
     * The transaction manager assigns a "write" temporary transaction id that the transaction will
     * use for all of its mutating operations (the "write TxId"). A "read" transaction id is
     * assigned the first time that the transaction attempts to read data. The read TxId ensures
     * that the client obtains a stable, transactional view of the underlying database, even as
     * other transactions from other clients may continue to be committed. All reads within that
     * transaction will be satisfied by the database using the version of the data specified by the
     * read TxId, plus whatever changes have occurred within the transaction using the write TxId.
     *
     * By forcing the client (the Connection, Transaction, and all DBObjects operate as "the Client
     * service") to call the transaction manager to obtain the write TxIds, and similarly by forcing
     * the ObjectStore implementations to obtain the read TxIds, the transaction manager acts as a
     * clearing-house for the lifecycle of each transaction, and for the lifecycle of transaction
     * management as a whole. As such, the transaction manager could (in theory) delay the creation
     * of a transaction, and/or re-order transactions, in order to optimize for some combination of
     * throughput, latency, and resource constraints.
     *
     * @param tx        the Client Transaction to assign TxIds for
     * @param systemTx  indicates that the transaction is being conducted by the database system
     *                  itself, and not by an application "client"
     *
     * @return  the "write" transaction ID to use
     */
    Int begin(Client.Transaction tx, Boolean systemTx = False)
        {
        checkEnabled();

        Int clientId = tx.outer.id;
        assert !byClientId.contains(clientId);

        Int      writeId = genWriteId();
        TxRecord rec     = new TxRecord(tx, tx.txInfo, clientId, writeId);

        byClientId.put(clientId, rec);
        byWriteId .put(writeId , rec);

        return writeId;
        }

    /**
     * Attempt to commit an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     *
     * @param writeId   the "write" transaction id previously returned from [begin]
     *
     * @return True if the transaction was committed; False if the transaction was rolled back for
     *         any reason
     */
    Boolean commit(Int writeId)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byWriteId.get(writeId) as $"Missing TxRecord for txid={writeId}";
        assert rec.status == InFlight;

        // REVIEW should this protected with a try/catch?
        return prepare(rec)
            && (validators  .empty || validate  (rec))
            && (rectifiers  .empty || rectify   (rec))
            && (distributors.empty || distribute(rec))
                // TODO RIGHT HERE!! this is the earliest point that we can check the pending queue
                ? commit(rec)
                : rollback(rec);
        }

    /**
     * Attempt to roll back an in-flight transaction.
     *
     * This method is intended to only be used by the [Client].
     *
     * @param writeId  the "write" transaction id previously returned from [begin]
     *
     * @return True iff the Transaction was rolled back
     */
    void rollback(Int writeId)
        {
        checkEnabled();

        // validate the transaction
        assert TxRecord rec := byWriteId.get(writeId) as $"Missing TxRecord for txid={writeId}";
        assert rec.status == InFlight;

        rollback(rec);
        }


    // ----- API for ObjectStore instances ---------------------------------------------------------

    /**
     * When an ObjectStore first encounters a transaction ID, it is responsible for enlisting itself
     * with the transaction manager for that transaction by calling this method.
     *
     * @param store  the ObjectStore instance that needs a base transaction ID (the "read" id) for
     *               the specified [txId] (the "write" id)
     * @param txId   the transaction ID that the client provided to the ObjectStore (the "write" id)
     *
     * @return the transaction id to use as the "read" transaction id; it identifies the transaction
     *         that the specified "write" transaction id is based on; note that the read transaction
     *         id will actually be a write transaction id while post-prepare triggers are being
     *         executed
     *
     * @throws ReadOnly  if the map does not allow or support the requested mutating operation
     * @throws IllegalState if the transaction state does not allow an ObjectStore to be enlisted,
     *         for example during rollback processing, or after a commit/rollback completes
     */
    Int enlist(ObjectStore store, Int txId)
        {
        assert isWriteTx(txId), TxRecord tx := byWriteId.get(txId);

        Int readId = tx.readId;
        if (readId == NO_TX)
            {
            // an enlist into a nascent transaction cannot occur while disabling the transaction
            // manager, or once it has disabled
            checkEnabled();

            // this is the first ObjectStore enlisting for this transaction, so create a mutable
            // set to hold all the enlisting ObjectStores, as they each enlist
            assert tx.enlisted.empty;
            tx.enlisted = new HashSet<ObjectStore>();

            // use the last successfully prepared transaction id as the basis for reading within
            // this transaction; this reduces the potential for roll-back, since prepared
            // transactions always commit unless (i) the plug gets pulled, (ii) a fatal error
            // occurs, or (iii) the database is shut down abruptly
            readId = lastPrepared;
            assert readId != NO_TX;
            tx.readId = readId;
            }

        assert !tx.enlisted.contains(store);
        switch (tx.status)
            {
            case InFlight:
                checkEnabled();
                break;

            case Distributing:
                break;

            default:
                assert as "Attempt to enlist into a transaction whose status is {tx.status} is forbidden";
            }

        tx.enlisted.add(store);
        return readId;
        }

    /**
     * Obtain a [Client.Worker] for the specified transaction. A `Worker` allows CPU-intensive
     * serialization and deserialization work to be dumped back onto the client that is responsible
     * for the request that causes the work to need to be done.
     *
     * @param txId  a "write" transaction ID
     *
     * @return worker  the [Client.Worker] to offload serialization and deserialization work onto
     */
    Client.Worker workerFor(Int txId)
        {
        assert TxRecord tx := byWriteId.get(txId);
        return tx.clientTx.outer.worker;
        }


    // ----- internal --------------------------------------------------------------------

    /**
     * Log a message to the system log.
     *
     * @param msg  the message to log
     */
    protected void log(String msg)
        {
        catalog.log^(msg);
        }

    /**
     * Generate a write transaction ID.
     *
     * @return the new write id
     */
    protected Int genWriteId()
        {
        return generateWriteId(++txCount);
        }

    /**
     * Generate the next "prepare" transaction ID. Note that until a transaction finishes TODO
     *
     * @return the new prepare id
     */
    protected Int genPrepareId()
        {
        return lastPrepared + 1;
        }

    /**
     * Obtain a Client object from an internal pool of Client objects. These Client objects are used
     * to represent specific stages of transaction processing. When the use of the Client is
     * finished, it should be returned to the pool by passing it to [recycleClient].
     *
     * @return an "internal" Client object
     */
    protected Client<Schema> allocateClient()
        {
        return clientCache.takeOrCompute(() -> catalog.createClient(system=True));
        }

    /**
     * Return the passed Client object to the internal pool of Client objects.
     *
     * @param client  an "internal" Client object previously obtained from [allocateClient]
     */
    protected void recycleClient(Client<Schema> client)
        {
        return clientCache.reversed.add(client);
        }

    /**
     * Attempt to prepare an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean prepare(TxRecord rec)
        {
        Int              writeId = rec.writeId;
        Set<ObjectStore> stores  = rec.enlisted;
        if (stores.empty)
            {
            // an empty transaction is considered committed
            assert rec.readId == NO_TX || isWriteTx(rec.readId);
            terminate(rec, Committed);
            return True;
            }

        // TODO: add ourselves to the pendingPrepare queue; proceed only if it was empty

        // prepare phase
        Int                 prepareId   = genPrepareId();
        Boolean             abort       = False;
        FutureVar<Boolean>? preparedAll = Null;
        rec.status    = Preparing;
        rec.prepareId = prepareId;
        for (ObjectStore store : stores)
            {
            // we cannot predict the order of asynchronous execution, so it is quite possible that
            // we find out that the transaction must be rolled back while we are still busy here
            // trying to get the various ObjectStore instances to prepare what changes they have
            if (abort)
                {
                // this will eventually resolve to False, since something has failed and caused the
                // transaction to roll back (which rollback may still be occurring asynchronously)
                break;
                }

            import ObjectStore.PrepareResult;
            @Future PrepareResult result      = store.prepare(writeId, prepareId);
            @Future Boolean       preparedOne = &result.transform(pr ->
                {
                switch (pr)
                    {
                    case FailedRolledBack:
                        stores.remove(store);
                        abort = True;
                        return False;

                    case CommittedNoChanges:
                        stores.remove(store);
                        return True;

                    case Prepared:
                        return True;
                    }
                });

            preparedAll = preparedAll?.and(&preparedOne, (f1, f2) -> f1 & f2) : &preparedOne;
            }

        assert preparedAll != Null;
        @Future Boolean result = preparedAll.whenComplete((v, e) ->
            {
            // check for successful prepare, and whether anything is left enlisted
            switch (v, stores.empty)
                {
                case (False, False):
                    // failed; remaining stores need to be rolled back
                    rollback(rec);
                    break;

                case (False, True ):
                    // failed; already rolled back
                    terminate(rec, RolledBack);
                    break;

                case (True , False):
                    // there might still be triggers to process as part of the prepare, but the
                    // "client" portion of the prepare has completed
                    if (rec.triggered.empty)
                        {
                        // no triggers; prepare is completed
                        lastPrepared = rec.prepareId; // TODO: remember to add the same logic to the trigger processing completion
                        rec.status = Prepared;
                        }
                    break;

                case (True , True ):
                    // succeeded; already committed
                    terminate(rec, Committed);
                    break;
                }
            });

// TODO
//        @Future Boolean triggered = syncTriggers.empty
//                ? &prepared
//                : &prepared.createContinuation(ok -> ok && triggerImpl(rec));

        return result;
        }

    /**
     * Attempt to prepare an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean validate(TxRecord rec)
        {
        if (validators.empty)
            {
            return True;
            }

        // TODO allocateClient()
        // TODO fake create transaction with writeId - 1 (so -17 becomes -18)
        // TODO for each enlisted storage
            {
            // TODO future call method on Client (that doesn't exist yet) called "run validators" and pass the [] of validators for the specific storage
            }
        // TODO return future result
        TODO

        // TODO (somewhere else) is-readonly has to eval to true for -18
        }

    /**
     * Attempt to prepare an in-flight transaction.   TODO
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean rectify(TxRecord rec)
        {
        if (rectifiers.empty)
            {
            return True;
            }

        // TODO see all the TODO in validate
        // TODO seal each object store after it rectifies
        TODO

        // TODO (somewhere else) is-readonly has to eval to true for all object store instances other than the one inside the for loop
        }

    /**
     * Attempt to prepare an in-flight transaction.TODO
     *
     * @param rec  the TxRecord representing the transaction to prepare
     *
     * @return True iff the Transaction was successfully prepared
     */
    protected Boolean distribute(TxRecord rec)
        {
        if (distributors.empty)
            {
            return True;
            }

        // TODO after weeding out any stores that have been enlisted but have no changes, seal all of the already-enlisted stores
        // TODO see all the TODO in validate
        TODO
        // TODO seal each object store after all of them have finished distributing (second for loop)
        }

    /**
     * Attempt to execute the synchronous triggers against a preparing transaction.
     *
     * @param rec  the TxRecord representing the transaction to run triggers against
     *
     * @return True iff the Transaction successfully evaluated its triggers
     */
    protected Boolean triggerImpl(TxRecord rec)
        {
        if (rec.triggered.empty)
            {
            return True;
            }
TODO
//        // some reasonable amount of trigger recursion should be expected; consider improving the
//        // error message here to provide some indication of how we got here, for example start
//        // keeping track of what is triggering what once we get close to the recursion limit
//        assert depth <= 64;
//
//        // the transaction record will hold on to a second transactional record that is only used
//        // during trigger processing, and which "commits to" (merges into) the existing transaction
//        // on each completion
//        TxRecord triggerRec;
//        Int      triggerId = rec.prepareId;
//        if (triggerId == NO_TX)
//            {
//            // we've already completed the preparation of the transaction based on the client's
//            // changes, so the trigger processing will occur based on the prepared transaction that
//            // will commit on top of the most recent transaction
//            rec.readId = lastPrepared;
//
//            triggerId  = genWriteId();
//            triggerRec = new TxRecord(tx, tx.txInfo, clientId, triggerId, readId=rec.writeId);
//
//            byWriteId.put(writeId, rec);
//
//            rec.prepareId = triggerId;
//            }
//        else
//            {
//            assert triggerRec := byWriteId.get(rec.prepareId);
//            }
//
//        FutureVar<Boolean>? triggeredAll = Null;
//        for (ObjectStore store : rec.takeTriggered())
//            {
//            // triggers are processed in sequence
//// TODO            @Future TODO result = store.prepare(writeId);
//            @Future Boolean triggeredOne = &result.transform(pr ->
//                {
//                TODO
//                });
//
//            triggeredAll = triggeredAll?.and(&triggeredOne, (f1, f2) -> f1 & f2) : &triggeredOne;
//            }
//
//        assert triggeredAll != Null;
//        @Future Boolean result = triggeredAll.whenComplete((v, e) ->
//            {
//            // check for successful prepare, and whether anything is left enlisted
//            switch (v, stores.empty)
//                {
//                case (False, False):
//                    // failed; remaining stores need to be rolled back
//                    rollback(rec);
//                    break;
//
//                case (False, True ):
//                    // failed; already rolled back
//                    terminate(rec, RolledBack);
//                    break;
//
//                case (True , False):
//                    // there might still be triggers to process as part of the prepare, but the
//                    // "client" portion of the prepare has completed
//                    if (rec.triggered.empty)
//                        {
//                        // no triggers; prepare is completed
//                        rec.status = triggered;
//                        }
//                    break;
//
//                case (True , True ):
//                    // succeeded; already committed
//                    terminate(rec, Committed);
//                    break;
//                }
//            });
//        return result;
        }

    /**
     * Attempt to commit a fully prepared transaction.
     *
     * @param rec  the TxRecord representing the transaction to finish committing
     *
     * @return True iff the Transaction was successfully committed
     */
    protected Boolean commit(TxRecord rec)
        {
        Set<ObjectStore> stores = rec.enlisted;
        if (stores.empty)
            {
            terminate(rec, Committed);
            return True;
            }

        // bundle the results of "sealPrepare()" into a buffer
        Int          writeId  = rec.writeId;
        Int          commitId = rec.prepareId;
        StringBuffer buf      = new StringBuffer();

        buf.append(",\n{")
           .append("\n\"_tx\":")
           .append(commitId);
        Loop: for (ObjectStore store : stores)
            {
            String json = store.sealPrepare(writeId);

            buf.add(',')
               .append("\n\"")
               .append(store.info.name)
               .append("\":")
               .append(json);
            }
        buf.append("\n}\n]");

        // append this information to the end of the database tx log, e.g.:
        //     [
        //     {
        //     "_tx":1
        //     "title":"hello",
        //     "contacts":[{"k":1, "v":{Bob...}}, {"k":2, "v":{Sam...}}, {"k":7}],
        //     ...
        //     },
        //     {
        //     "_tx":2
        //     "title":"goodbye",
        //     "contacts":[{"k":1, "v":{Bob...}}, {"k":2, "v":{Sam...}}, {"k":7}],
        //     ...
        //     }
        //     ]

        File file   = logFile;
        Int  length = file.size;
        assert length >= 20;        // log file is never empty!

        // TODO right now this assumes that no manual edits have occurred; must cache "last
        //      update timestamp" and rebuild file if someone else changed it (see ValueStore.commit)
        file.truncate(length-2)
            .append(buf.toString().utf8());

        assert commitId == lastCommitted + 1;
        lastCommitted = commitId;

        if (length > maxLogSize)
            {
            rotateLog();
            }

        FutureVar<Tuple<>>? commitAll = Null;
        rec.status = Committing;
        for (ObjectStore store : stores)
            {
            @Future Tuple<> commitOne = store.commit(writeId);
            commitAll = commitAll?.and(&commitOne, (_, _) -> Tuple:()) : &commitOne;
            }

        assert commitAll != Null;
        @Future Boolean completed = commitAll.transformOrHandle((Tuple<>? t, Exception? e) ->
            {
            if (e != Null)
                {
                log($"HeuristicException during commit caused by: {e}");
                terminate(rec, RolledBack); // this should be HeuristicRollback
                return False;
                }
            terminate(rec, Committed);
            return True;
            });
        return completed;
        }

    /**
     * Attempt to roll back an in-flight transaction.
     *
     * @param rec  the TxRecord representing the transaction to roll back
     *
     * @return True iff the Transaction was rolled back
     */
    protected Boolean rollback(TxRecord rec)
        {
        Set<ObjectStore> stores = rec.enlisted;
        if (stores.empty)
            {
            terminate(rec, RolledBack);
            return True;
            }

        Int                 writeId     = rec.writeId;
        FutureVar<Tuple<>>? rollbackAll = Null;
        rec.status = RollingBack;
        for (ObjectStore store : stores)
            {
            @Future Tuple<> rollbackOne = store.rollback(writeId);
            rollbackAll = rollbackAll?.and(&rollbackOne, (_, _) -> Tuple:()) : &rollbackOne;
            }

        assert rollbackAll != Null;
        @Future Boolean completed = rollbackAll.transform((Tuple<> t) ->
            {
            terminate(rec, RolledBack);
            return True;
            });
        return completed;
        }

    /**
     * Finish the specified transaction.
     *
     * @param rec     the transaction record
     * @param status  the terminal status of the transaction
     */
    protected void terminate(TxRecord rec, TxRecord.Status status)
        {
        byWriteId.remove(rec.writeId);
        byClientId.remove(rec.clientId);
        rec.status   = status;
        rec.enlisted = [];
        }


    // ----- internal: TxRecord --------------------------------------------------------------------

    /**
     * This is the information that the TxManager maintains about each in flight transaction.
     */
    protected static class TxRecord(
            Client.Transaction  clientTx,
            TxInfo              txInfo,
            Int                 clientId,
            Int                 writeId,
            Int                 readId    = NO_TX,
            Int                 prepareId = NO_TX,
            Set<ObjectStore>    enlisted  = [],         // TODO set of ids
            Set<ObjectStore>    triggered = [],         // TODO set of ids
            Status              status    = InFlight)
        {
        /**
         * Enlist a transactional resource.
         *
         * @param store  the resource to enlist
         */
        void enlist(ObjectStore store)
            {
            if (enlisted.is(immutable Object))
                {
                enlisted = new HashSet(enlisted);
                }

            enlisted.add(store);
            }

        @RO Boolean readOnly.get()
            {
            return txInfo.readOnly;
            }

// TODO review the purpose of this
        /**
         * Mark a transactional resource as being triggered.
         *
         * @param store  the resource to enlist
         */
        void trigger(ObjectStore store)
            {
            if (triggered.is(immutable Object))
                {
                triggered = new HashSet(triggered);
                }

            assert triggered.addIfAbsent(store);
            }

        /**
         * @return the ObjectStores **in a predictable order** that need to have triggers evaluated
         */
        Iterable<ObjectStore> takeTriggered()
            {
            if (triggered.empty)
                {
                return [];
                }

            val result = triggered.sorted((os1, os2) -> os1.id <=> os2.id);
            triggered = [];
            return result;
            }

        /**
         * Transaction status.
         */
        enum Status
            {
            InFlight,
            Preparing,
            Validating,
            Rectifying,
            Distributing,
            Prepared,
            Committing,
            Committed,
            RollingBack,
            RolledBack,
            }
        }
    }
