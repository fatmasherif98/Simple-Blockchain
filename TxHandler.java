import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class TxHandler {

    private UTXOPool utxoPool;
    private ArrayList<UTXO> inputUtxo;

    /**
     * Creates a public ledger whose current UTXOPool (collection of unspent transaction outputs) is
     * {@code utxoPool}. 
     */
    public TxHandler(UTXOPool utxoPool) {
        this.utxoPool = new UTXOPool(utxoPool);

    }

    public UTXOPool getUTXOPool()
    {
        return this.utxoPool;
    }
    /**
     * @return true if:
     * (1) all outputs claimed by {@code tx} are in the current UTXO pool, 
     * (2) the signatures on each input of {@code tx} are valid, 
     * (3) no UTXO is claimed multiple times by {@code tx},
     * (4) all of {@code tx}s output values are non-negative, and
     * (5) the sum of {@code tx}s input values is greater than or equal to the sum of its output
     *     values; and false otherwise.
     */

    private boolean verifyInput( Transaction tx, int index, Transaction.Input currInput, HashSet<UTXO> seenInputSet)
    {
        byte[] tx_hash = currInput.prevTxHash;
        int output_index = currInput.outputIndex;
        UTXO new_utxo = new UTXO(tx_hash, output_index);

        if( !utxoPool.contains(new_utxo) || seenInputSet.contains(new_utxo) )
            return false;

        seenInputSet.add(new_utxo);

        PublicKey currAddress = utxoPool.getTxOutput(new_utxo).address;
        boolean isValid = Crypto.verifySignature(currAddress, tx.getRawDataToSign(index),currInput.signature );
        if( !isValid )
            return false;
        this.inputUtxo.add(new_utxo);
        return true;
    }

    private double calculateOutput(ArrayList<Transaction.Output> outputs){
        int length = outputs.size();
        double outputValue = 0;
        for(int index=0; index<length; index++)
        {
            double currOutputVal = outputs.get(index).value;
            if( currOutputVal < 0 )
            {
                return -1;
            }
            outputValue += currOutputVal;
        }
        return outputValue;
    }
    public boolean isValidTx(Transaction tx) {

        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        ArrayList<Transaction.Input> inputs = tx.getInputs();
        HashSet<UTXO> seenInputSet = new HashSet(); //to avoid one tx referencing the same UTXO multiple times

        this.inputUtxo = new ArrayList<UTXO>();
        double inputValue = 0;
        double outputValue = 0;

        int length = tx.numInputs();
        for(int index=0; index<length; index++){

            Transaction.Input currInput = inputs.get(index);
            boolean validity = verifyInput(tx, index, currInput, seenInputSet );
            if(validity == false)
                return false;
            //find value of this input
            UTXO lastUTXO = this.inputUtxo.get(this.inputUtxo.size()-1);
            double newInputVal = utxoPool.getTxOutput(lastUTXO).value;
            if(newInputVal < 0 )
                return false;
            inputValue += newInputVal;
        }

        outputValue = calculateOutput(outputs);
        if( (inputValue < outputValue) || (outputValue == -1) )
            return false;

        return true;
    }

    private void modifyUPool(Transaction tx)
    {
        for(UTXO input: this.inputUtxo)
        {
            utxoPool.removeUTXO(input);
        }
        ArrayList<Transaction.Output> outputs = tx.getOutputs();
        int len = outputs.size();
        byte[] hash = tx.getHash();
        for( int i=0; i<len; i++)
        {
            UTXO utxOutput = new UTXO( hash,i);
            this.utxoPool.addUTXO(utxOutput, outputs.get(i));
        }

    }
    /**
     * Handles each epoch by receiving an unordered array of proposed transactions, checking each
     * transaction for correctness, returning a mutually valid array of accepted transactions, and
     * updating the current UTXO pool as appropriate.
     */

    private void fillStateMap(Transaction[] possibleTxs,HashMap<Transaction, Integer> txState )
    {
        for( Transaction tx : possibleTxs)
        {
            txState.put( tx,0); //0 means not processed yet
        }
    }
    private void fillUTXOMap(Transaction[] possibleTxs,HashMap<UTXO, Transaction> UTXOMap)
    {
        for( Transaction tx : possibleTxs)
        {
            ArrayList<Transaction.Output> txOutputs = tx.getOutputs();
            int len = tx.numOutputs();
            byte[] txHash = tx.getHash();
            for(int i=0; i<len; i++)
            {
                UTXO newUTXO = new UTXO(txHash, i);
                UTXOMap.put( newUTXO, tx);
            }
        }
    }
    public Transaction[] handleTxs(Transaction[] possibleTxs) {
        // IMPLEMENT THIS
        ArrayList<Transaction> output = new ArrayList<>(); //result to be returned
        HashMap<Transaction, Integer> txState = new HashMap<>(); //links a tx to it's current state
        //0-> not processed yet, 1-> under processing, 2-> valid tx, -1-> invalid tx
        HashMap<UTXO, Transaction> UTXOMap = new HashMap<>(); //links all UTXOs to it's original tx (where it is an output)
        //fill both maps
        fillStateMap(possibleTxs, txState);
        fillUTXOMap(possibleTxs, UTXOMap);
        //outer loop for dfs
        for( Transaction tx : possibleTxs)
        {
            if( txState.get(tx) == 0) //if a tx is not processed yet
                processTxDFS(tx, txState, UTXOMap);
        }
        for (Map.Entry<Transaction,Integer> entry : txState.entrySet()) {
            if (entry.getValue() == 2)
                output.add(entry.getKey());
        }

        Transaction[] arr= new Transaction[output.size()];
        arr= output.toArray(arr);
        return arr;


    }
    private void convertIntputToUTXO(ArrayList<UTXO> currentTxUtxos,ArrayList<Transaction.Input> inputs )
    {
        for( Transaction.Input input : inputs )
        {
            UTXO curr_utxo = new UTXO(input.prevTxHash, input.outputIndex);
            currentTxUtxos.add(curr_utxo);
        }
    }
    private void processTxDFS(Transaction tx, HashMap<Transaction, Integer> txState, HashMap<UTXO, Transaction> UTXOMap)
    {
        txState.put(tx, 1); //this current tx is set as "visited"

        ArrayList<UTXO> currentTxUtxos = new ArrayList<>();
        ArrayList<Transaction.Input> inputs = tx.getInputs();

        convertIntputToUTXO(currentTxUtxos, inputs);

        //verify that there are no dependencies, if a dependency appears, we must validate it first
        int len = currentTxUtxos.size();
        for(int i=0; i<len; i++)
        {
            UTXO currUtxo = currentTxUtxos.get(i);
            if( !utxoPool.contains(currUtxo))
            {
                if( !UTXOMap.containsKey(currUtxo))
                {
                    txState.put(tx, -1);
                    return;
                }else //could be dependent on another tx
                {
                    Transaction depedentTx = UTXOMap.get(currUtxo);
                    if( txState.get(depedentTx) == 1)
                    {
                        //cycle
                        txState.put(tx,-1);
                        return;
                    } else if (txState.get(depedentTx) == 0)
                    {
                        processTxDFS(depedentTx,  txState,  UTXOMap);
                        i=-1; //start checking from beginning (so that check is atomic)
                    } else if( txState.get(depedentTx) == -1)
                    {
                        txState.put(tx,-1);
                        return;
                    }
                }
            }
        } //if we pass this check, then the tx is ready to be validated
        //and does not depend on any other tx

        if( isValidTx(tx))
        {
            txState.put(tx, 2); //2 means valid tx
            modifyUPool(tx);
        }else
            txState.put(tx, -1);

        return;
    }

}
