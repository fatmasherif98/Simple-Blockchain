// The BlockChain class should maintain only limited block nodes to satisfy the functionality.
// You should not have all the blocks added to the block chain in memory 
// as it would cause a memory overflow.

import java.util.ArrayList;
import java.util.HashMap;


public class BlockChain {
    public static final int CUT_OFF_AGE = 10;
    public static TransactionPool txPool;
    /**
     * create an empty blockchain with just a genesis block. Assume {@code genesisBlock} is a valid
     * block
     */
    private static int maximumHeight = 0;
    private static HashMap<Integer, ArrayList<Block>> mapBLock = new HashMap<>();
    private static HashMap<ByteArrayWrapper, Integer> mapHeight = new HashMap<>();
    private static HashMap<ByteArrayWrapper, Block> mapBLockByHash = new HashMap<>();
    private static HashMap<ByteArrayWrapper,UTXOPool> blockPool = new HashMap<>();
    public BlockChain(Block genesisBlock) {
        // IMPLEMENT THIS
        mapBLock.put(1, new ArrayList<Block>());
        mapBLock.get(1).add(genesisBlock);
        ByteArrayWrapper currHash = new ByteArrayWrapper(genesisBlock.getHash());
        mapHeight.put( currHash,1);
        mapBLockByHash.put(currHash, genesisBlock);
        UTXOPool genesisPool = new UTXOPool();

        Transaction coinbaseTx = genesisBlock.getCoinbase();
        ArrayList<Transaction.Output> outputs = coinbaseTx.getOutputs();
        int len = coinbaseTx.numOutputs();
        for(int i=0; i<len; i++)
        {
            UTXO coinbaseUTXO = new UTXO( coinbaseTx.getHash(),i);
            genesisPool.addUTXO(coinbaseUTXO, coinbaseTx.getOutput(i));
        }


        blockPool.put( currHash, genesisPool);
        this.maximumHeight = 1;
        txPool = new TransactionPool();
    }

    /** Get the maximum height block */
    public Block getMaxHeightBlock() {
        // IMPLEMENT THIS
        return mapBLock.get(this.maximumHeight).get(0);

    }

    /** Get the UTXOPool for mining a new block on top of max height block */
    public UTXOPool getMaxHeightUTXOPool() {
        // IMPLEMENT THIS
        Block b = mapBLock.get(maximumHeight).get(0);
        return blockPool.get( new ByteArrayWrapper(b.getHash()) );
    }

    /** Get the transaction pool to mine a new block */
    public TransactionPool getTransactionPool() {
        // IMPLEMENT THIS
        return txPool;
    }

    /**
     * Add {@code block} to the blockchain if it is valid. For validity, all transactions should be
     * valid and block should be at {@code height > (maxHeight - CUT_OFF_AGE)}, where maxHeight is 
     * the current height of the blockchain.
	 * <p>
	 * Assume the Genesis block is at height 1.
     * For example, you can try creating a new block over the genesis block (i.e. create a block at 
	 * height 2) if the current blockchain height is less than or equal to CUT_OFF_AGE + 1. As soon as
	 * the current blockchain height exceeds CUT_OFF_AGE + 1, you cannot create a new block at height 2.
     * 
     * @return true if block is successfully added
     */
    public boolean addBlock(Block block) {
        // IMPLEMENT THIS

        ByteArrayWrapper currHash = new ByteArrayWrapper(block.getHash());
        if( block.getPrevBlockHash() == null )
            return false;
        ByteArrayWrapper prevHash = new ByteArrayWrapper(block.getPrevBlockHash());
        if( !mapBLockByHash.containsKey(prevHash))
            return false;
        Block parent = mapBLockByHash.get(prevHash);
        if( !mapHeight.containsKey(prevHash))
            return false;
        int heightParent = mapHeight.get(prevHash);
        int height = heightParent + 1;
        if ( heightParent < ( maximumHeight - CUT_OFF_AGE ))
            return false;
        ArrayList<Transaction> arrayListTxs = block.getTransactions();
        Transaction[] txs = new Transaction[arrayListTxs.size()];
        txs = arrayListTxs.toArray(txs);

        UTXOPool currUTXOPool = new UTXOPool( blockPool.get(prevHash) );
        TxHandler txHandler = new TxHandler(currUTXOPool);
        Transaction[] validTxs = txHandler.handleTxs(txs);
        if( validTxs.length != txs.length)
            return false;

        currUTXOPool = txHandler.getUTXOPool(); //after modifying pool
        Transaction coinbaseTx = block.getCoinbase();
        //ArrayList<Transaction.Output> outputs = coinbaseTx.getOutputs();
        int len = coinbaseTx.numOutputs();
        for(int i=0; i<len; i++)
        {
            UTXO coinbaseUTXO = new UTXO( coinbaseTx.getHash(),i);
            currUTXOPool.addUTXO(coinbaseUTXO, coinbaseTx.getOutput(i));
        }

        blockPool.put(currHash, currUTXOPool);

        mapHeight.put(currHash, height);
        mapHeight.put(currHash, height);
        mapBLockByHash.put(currHash , block);
        if( mapBLock.containsKey(height) )
            mapBLock.get(height).add(block);
        else
        {
            mapBLock.put(height, new ArrayList<Block>());
            mapBLock.get(height).add(block);
        }

        if( height > maximumHeight)
        {

            maximumHeight = height;
            if( mapBLock.containsKey( maximumHeight - CUT_OFF_AGE - 1 ))
            {
                ArrayList<Block> blocksToBeRemoved = mapBLock.get(maximumHeight - CUT_OFF_AGE-1);
                for( Block currBlock : blocksToBeRemoved)
                {
                    ByteArrayWrapper bHash = new ByteArrayWrapper(currBlock.getHash());
                    blockPool.remove(bHash);
                    mapHeight.remove(bHash);
                    mapBLockByHash.remove(bHash);
                }

                mapBLock.remove(maximumHeight - CUT_OFF_AGE - 1);

            }
        }
//remove txs from pool

            for( Transaction tx : txs)
            {
                txPool.removeTransaction(tx.getHash());
            }

        return true;
    }

    /** Add a transaction to the transaction pool */
    public void addTransaction(Transaction tx) {
        // IMPLEMENT THIS
        txPool.addTransaction(tx);

    }
}